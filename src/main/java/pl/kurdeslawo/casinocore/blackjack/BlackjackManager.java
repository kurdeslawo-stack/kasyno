package pl.kurdeslawo.casinocore.blackjack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.RoundJournal;
import pl.kurdeslawo.casinocore.economy.EconomyTransactionException;

public final class BlackjackManager {
    private final EconomyProvider economy;
    private final Supplier<BlackjackDeck> decks;
    private final BlackjackHistoryRepository history;
    private final RoundJournal journal;
    private final java.util.Set<UUID> recorded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BlackjackSession> sessions = new ConcurrentHashMap<>();
    private volatile List<Long> stakes;
    private volatile BlackjackEngine engine;

    public BlackjackManager(
        EconomyProvider economy,
        List<Long> stakes,
        int blackjackNumerator,
        int blackjackDenominator,
        boolean hitSoft17
    ) {
        this(economy, stakes, blackjackNumerator, blackjackDenominator, hitSoft17,
            () -> new BlackjackDeck(RandomGenerator.getDefault()),
            new InMemoryBlackjackHistoryRepository());
    }

    BlackjackManager(
        EconomyProvider economy,
        List<Long> stakes,
        int blackjackNumerator,
        int blackjackDenominator,
        boolean hitSoft17,
        Supplier<BlackjackDeck> decks,
        BlackjackHistoryRepository history
    ) {
        this(economy, stakes, blackjackNumerator, blackjackDenominator, hitSoft17, decks, history, new RoundJournal());
    }

    public BlackjackManager(EconomyProvider economy, List<Long> stakes, int numerator, int denominator,
                            boolean hitSoft17, RoundJournal journal) {
        this(economy, stakes, numerator, denominator, hitSoft17,
            () -> new BlackjackDeck(RandomGenerator.getDefault()), new InMemoryBlackjackHistoryRepository(), journal);
    }

    BlackjackManager(EconomyProvider economy, List<Long> stakes, int blackjackNumerator, int blackjackDenominator,
                     boolean hitSoft17, Supplier<BlackjackDeck> decks, BlackjackHistoryRepository history,
                     RoundJournal journal) {
        this.economy = Objects.requireNonNull(economy);
        this.decks = Objects.requireNonNull(decks);
        this.history = Objects.requireNonNull(history);
        this.journal = Objects.requireNonNull(journal);
        updateConfiguration(stakes, blackjackNumerator, blackjackDenominator, hitSoft17);
    }

    public BlackjackSession openSession(UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new BlackjackSession(id, stakes.getFirst()));
    }

    public Optional<BlackjackSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void selectStake(UUID playerId, long stake) {
        if (!stakes.contains(stake)) throw new IllegalArgumentException("Unsupported Blackjack stake");
        openSession(playerId).selectStake(stake);
    }

    public StartOutcome start(UUID playerId) {
        BlackjackSession session = openSession(playerId);
        synchronized (session) {
            if (sessions.get(playerId) != session) return new StartOutcome(false, sessions.get(playerId), "Session changed");
            if (session.phase() != BlackjackPhase.BETTING) {
                return new StartOutcome(false, session, "Round active");
            }

            long stake = session.selectedStake();
            if (!stakes.contains(stake)) {
                return new StartOutcome(false, session, "The selected stake is no longer available.");
            }
            BlackjackDeck deck = decks.get();
            BlackjackEngine roundEngine = engine;
            BlackjackCard playerFirst = deck.draw();
            BlackjackCard dealerFirst = deck.draw();
            BlackjackCard playerSecond = deck.draw();
            BlackjackCard dealerSecond = deck.draw();
            if (!economy.has(playerId, stake)) {
                return new StartOutcome(false, session, "Not enough coins");
            }
            BlackjackSession round = new BlackjackSession(playerId, stake);
            round.begin(deck, roundEngine, playerFirst, dealerFirst, playerSecond, dealerSecond);
            journal.begin(round.roundId(), playerId, "blackjack", round.snapshot());
            sessions.put(playerId, round);
            try {
                if (!journal.withdraw(round.roundId(), "stake", stake, economy)) {
                    journal.cancelUnfunded(round.roundId());
                    sessions.put(playerId, session);
                    return new StartOutcome(false, session, "Not enough coins");
                }
            } catch (EconomyTransactionException exception) {
                if (exception.definitelyRejected()) {
                    journal.cancelUnfunded(round.roundId());
                    sessions.put(playerId, session);
                }
                throw exception;
            }
            return new StartOutcome(true, round, null);
        }
    }

    public void finishDeal(UUID playerId) {
        BlackjackSession session = sessions.get(playerId);
        if (session == null) return;
        synchronized (session) {
            if (session.phase() != BlackjackPhase.DEALING) return;
            journal.requireFunded(session.roundId());
            if (session.playerHand().isBlackjack() || session.dealerHand().isBlackjack()) {
                settleLocked(session);
            } else {
                session.playerTurn();
                journal.checkpoint(session.roundId(), session.snapshot());
            }
        }
    }

    public void hit(UUID playerId) {
        BlackjackSession session = require(playerId);
        synchronized (session) {
            journal.requireFunded(session.roundId());
            session.hit();
            journal.checkpoint(session.roundId(), session.snapshot());
            if (session.playerHand().isBust()) {
                settleLocked(session);
            } else if (session.playerHand().value() == 21) {
                standLocked(session);
            }
        }
    }

    public boolean doubleDown(UUID playerId) {
        BlackjackSession session = require(playerId);
        synchronized (session) {
            if (session.phase() != BlackjackPhase.PLAYER_TURN || session.playerHand().cards().size() != 2) {
                return false;
            }
            long extraStake = session.wager();
            journal.requireFunded(session.roundId());
            if (!economy.has(playerId, extraStake) || !journal.withdraw(session.roundId(), "double", extraStake, economy)) {
                return false;
            }
            session.doubleWager();
            session.hit();
            journal.checkpoint(session.roundId(), session.snapshot());
            if (session.playerHand().isBust()) settleLocked(session);
            else standLocked(session);
            return true;
        }
    }

    public void stand(UUID playerId) {
        BlackjackSession session = require(playerId);
        synchronized (session) {
            if (session.phase() == BlackjackPhase.RESULT || session.phase() == BlackjackPhase.BETTING) return;
            journal.requireFunded(session.roundId());
            if (session.phase() == BlackjackPhase.SETTLING) {
                settleLocked(session);
                return;
            }
            if (session.phase() == BlackjackPhase.DEALING) {
                if (session.playerHand().isBlackjack() || session.dealerHand().isBlackjack()) {
                    settleLocked(session);
                    return;
                }
                session.playerTurn();
            }
            if (session.phase() == BlackjackPhase.PLAYER_TURN) standLocked(session);
        }
    }

    private void standLocked(BlackjackSession session) {
        session.drawDealerTo(session.roundEngine());
        settleLocked(session);
    }

    private void settleLocked(BlackjackSession session) {
        session.prepareSettlement();
        if (!session.claimSettlement()) return;
        try {
            journal.checkpoint(session.roundId(), session.snapshot());
            BlackjackOutcome outcome = session.outcome();
            long payout = session.payout();
            if (payout > 0) journal.deposit(session.roundId(), payout, economy);
            session.complete(outcome, payout);
            journal.complete(session.roundId(), session.snapshot());
            recordHistory(session);
        } catch (RuntimeException exception) {
            session.failSettlement();
            throw exception;
        }
    }

    public void closeSession(UUID playerId) {
        BlackjackSession session = sessions.get(playerId);
        if (session == null) return;
        synchronized (session) {
            if (session.funded() && !session.settled()) stand(playerId);
            if (!session.funded() || session.settled()) sessions.remove(playerId, session);
        }
    }

    public boolean reset(UUID playerId) {
        BlackjackSession session = sessions.get(playerId);
        if (session == null || session.phase() != BlackjackPhase.RESULT) return false;
        session.reset();
        return true;
    }

    public void closeAll() {
        RuntimeException firstFailure = null;
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            try {
                closeSession(playerId);
            } catch (RuntimeException exception) {
                if (firstFailure == null) firstFailure = exception;
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    public List<Long> stakes() {
        return stakes;
    }

    public List<BlackjackRoundRecord> recent(UUID playerId) {
        return history.recent(playerId);
    }

    public BlackjackStats stats(UUID playerId) {
        return history.stats(playerId);
    }
    public boolean needsReview(UUID playerId) { return journal.needsReview(playerId); }

    private void recordHistory(BlackjackSession session) {
        if (recorded.contains(session.roundId())) return;
        history.record(session.playerId(), new BlackjackRoundRecord(session.playerHand().value(),
            session.dealerHand().value(), session.wager(), session.payout(), session.outcome()));
        recorded.add(session.roundId());
    }

    public void recoverRound(UUID id) {
        RoundJournal.Entry entry = journal.get(id);
        if (!entry.game().equals("blackjack") || entry.cancelled()) return;
        if (entry.complete()) {
            recordHistory(BlackjackSession.restore(entry.player(), id, entry.data()));
            return;
        }
        if (entry.needsReview()) throw new IllegalStateException("Blackjack round needs review: " + id);
        var stake = entry.transactions().get("stake");
        if (stake == null || stake.state() == RoundJournal.State.REJECTED) {
            journal.cancelUnfunded(id);
            sessions.remove(entry.player());
            return;
        }
        BlackjackSession session = BlackjackSession.restore(entry.player(), id, entry.data());
        sessions.put(entry.player(), session);
        var doubled = entry.transactions().get("double");
        if (doubled != null && doubled.state() == RoundJournal.State.SUCCESS
            && session.wager() == session.selectedStake()) {
            session.doubleWager();
            session.hit();
            journal.checkpoint(id, session.snapshot());
        }
        if (session.playerHand().isBust()) settleLocked(session);
        else stand(entry.player());
        if (session.settled()) sessions.remove(entry.player(), session);
    }

    public void recoverAll(java.util.function.Consumer<String> errors) {
        for (RoundJournal.Entry entry : journal.entries("blackjack")) {
            try { recoverRound(entry.id()); }
            catch (RuntimeException exception) { errors.accept(exception.getMessage()); }
        }
    }

    public void updateConfiguration(
        List<Long> newStakes,
        int blackjackNumerator,
        int blackjackDenominator,
        boolean hitSoft17
    ) {
        if (newStakes == null || newStakes.isEmpty()
            || newStakes.stream().anyMatch(stake -> stake == null || stake <= 0)) {
            throw new IllegalArgumentException("Blackjack stakes must be positive");
        }
        BlackjackEngine newEngine = new BlackjackEngine(blackjackNumerator, blackjackDenominator, hitSoft17);
        newEngine.maximumReturn(newStakes.stream().mapToLong(Long::longValue).max().orElseThrow());
        stakes = newStakes.stream().distinct().sorted().toList();
        engine = newEngine;
    }

    private BlackjackSession require(UUID playerId) {
        BlackjackSession session = sessions.get(playerId);
        if (session == null) throw new IllegalStateException("No Blackjack session");
        return session;
    }

    public record StartOutcome(boolean started, BlackjackSession session, String error) {}
}
