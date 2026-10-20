package pl.kurdeslawo.casinocore.roulette;

import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.EconomyTransactionException;
import pl.kurdeslawo.casinocore.economy.RoundJournal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouletteManager {
    private final EconomyProvider economy;
    private final RouletteRandom random;
    private final RouletteHistoryRepository history;
    private final RoundJournal journal;
    private final java.util.Set<UUID> recorded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RouletteSession> sessions = new ConcurrentHashMap<>();
    private volatile List<Long> allowedStakes;
    private volatile RouletteEngine engine;

    public RouletteManager(
            EconomyProvider economy,
            RouletteRandom random,
            List<Long> allowedStakes,
            java.math.BigDecimal outsideMultiplier,
            java.math.BigDecimal exactMultiplier) {
        this(
                economy,
                random,
                allowedStakes,
                outsideMultiplier,
                exactMultiplier,
                new InMemoryRouletteHistoryRepository());
    }

    public RouletteManager(
            EconomyProvider economy,
            RouletteRandom random,
            List<Long> allowedStakes,
            java.math.BigDecimal outsideMultiplier,
            java.math.BigDecimal exactMultiplier,
            RouletteHistoryRepository history) {
        this(
                economy,
                random,
                allowedStakes,
                outsideMultiplier,
                exactMultiplier,
                history,
                new RoundJournal());
    }

    public RouletteManager(
            EconomyProvider economy,
            RouletteRandom random,
            List<Long> allowedStakes,
            java.math.BigDecimal outsideMultiplier,
            java.math.BigDecimal exactMultiplier,
            RouletteHistoryRepository history,
            RoundJournal journal) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.random = Objects.requireNonNull(random, "random");
        this.history = Objects.requireNonNull(history, "history");
        this.journal = Objects.requireNonNull(journal);
        updateConfiguration(allowedStakes, outsideMultiplier, exactMultiplier);
    }

    public RouletteSession openSession(UUID playerId) {
        return sessions.computeIfAbsent(
                playerId, id -> new RouletteSession(id, allowedStakes.getFirst()));
    }

    public Optional<RouletteSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void selectBet(UUID playerId, RouletteBetType type, Integer selectedNumber) {
        RouletteSession session = openSession(playerId);
        synchronized (session) {
            session.selectBet(
                    new RouletteBet(playerId, type, session.selectedStake(), selectedNumber));
        }
    }

    public StartRoundOutcome startRound(UUID playerId) {
        RouletteSession session = openSession(playerId);
        synchronized (session) {
            if (session.phase() != RoulettePhase.SELECTING) {
                return StartRoundOutcome.failure(session, "A round is already active.");
            }
            long stake = session.selectedStake();
            if (sessions.get(playerId) != session)
                return StartRoundOutcome.failure(session, "Session changed");
            if (!allowedStakes.contains(stake)) {
                return StartRoundOutcome.failure(
                        session, "The selected stake is no longer available.");
            }
            RouletteBet bet = session.selectedBet();
            if (bet == null) {
                return StartRoundOutcome.failure(session, "Select a bet first.");
            }
            RouletteResult result = random.nextResult();
            long totalReturn = engine.totalReturn(bet, result);
            if (!economy.has(playerId, stake)) {
                return StartRoundOutcome.failure(session, "You do not have enough coins.");
            }

            RouletteSession round = new RouletteSession(playerId, stake);
            round.selectBet(bet);
            round.beginSpin(bet, result, totalReturn);
            journal.begin(round.roundId(), playerId, "roulette", round.snapshot());
            sessions.put(playerId, round);
            try {
                if (!journal.withdraw(round.roundId(), "stake", stake, economy)) {
                    journal.cancelUnfunded(round.roundId());
                    sessions.put(playerId, session);
                    return StartRoundOutcome.failure(session, "You do not have enough coins.");
                }
            } catch (EconomyTransactionException exception) {
                if (exception.definitelyRejected()) {
                    journal.cancelUnfunded(round.roundId());
                    sessions.put(playerId, session);
                }
                throw exception;
            }
            return StartRoundOutcome.success(round);
        }
    }

    public boolean settle(UUID playerId) {
        RouletteSession session = sessions.get(playerId);
        if (session == null || !session.claimSettlement()) {
            return false;
        }
        try {
            journal.requireFunded(session.roundId());
            RouletteBet bet = session.selectedBet();
            RouletteResult result = session.result();
            long payout = session.expectedPayout();
            if (payout > 0) {
                journal.deposit(session.roundId(), payout, economy);
            }
            session.completeSettlement(payout);
            journal.complete(session.roundId(), session.snapshot());
            recordHistory(session);
            return true;
        } catch (RuntimeException exception) {
            session.failSettlement();
            throw exception;
        }
    }

    public void selectStake(UUID playerId, long stake) {
        if (!allowedStakes.contains(stake)) {
            throw new IllegalArgumentException("Unsupported roulette stake: " + stake);
        }
        openSession(playerId).selectStake(stake);
    }

    public boolean resetForNextRound(UUID playerId) {
        RouletteSession session = sessions.get(playerId);
        if (session == null || session.phase() != RoulettePhase.RESULT || !session.settled()) {
            return false;
        }
        session.resetForNextRound();
        return true;
    }

    public void closeSession(UUID playerId) {
        RouletteSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        if (session.stakeWithdrawn() && !session.settled()) {
            settle(playerId);
        }
        if (!session.stakeWithdrawn() || session.settled()) {
            sessions.remove(playerId, session);
        }
    }

    public void settleAll() {
        RuntimeException firstFailure = null;
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            try {
                closeSession(playerId);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public void updateConfiguration(
            List<Long> stakes,
            java.math.BigDecimal outsideMultiplier,
            java.math.BigDecimal exactMultiplier) {
        if (stakes.isEmpty() || stakes.stream().anyMatch(stake -> stake == null || stake <= 0)) {
            throw new IllegalArgumentException("Roulette stakes must contain positive values");
        }
        this.allowedStakes = stakes.stream().distinct().sorted().toList();
        this.engine = new RouletteEngine(outsideMultiplier, exactMultiplier);
    }

    public List<Long> allowedStakes() {
        return allowedStakes;
    }

    public java.math.BigDecimal outsideMultiplier() {
        return engine.outsideMultiplier();
    }

    public java.math.BigDecimal exactMultiplier() {
        return engine.exactMultiplier();
    }

    public double winProbability(RouletteBetType type, Integer number) {
        if (type == RouletteBetType.EXACT_NUMBER)
            return random.probability(Objects.requireNonNull(number));
        RouletteBet preview = new RouletteBet(new UUID(0, 0), type, 1, null);
        double chance = 0;
        for (int n = 0; n <= 10; n++) {
            if (engine.wins(preview, new RouletteResult(n, RouletteEngine.colorOf(n))))
                chance += random.probability(n);
        }
        return chance;
    }

    public Collection<RouletteSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public List<RouletteRoundRecord> recentRounds(UUID playerId) {
        return history.recentRounds(playerId);
    }

    public RoulettePlayerStats playerStats(UUID playerId) {
        return history.stats(playerId);
    }

    public boolean needsReview(UUID playerId) {
        return journal.needsReview(playerId);
    }

    private void recordHistory(RouletteSession session) {
        if (recorded.contains(session.roundId())) return;
        history.record(
                RouletteRoundRecord.from(
                        session.selectedBet(), session.result(), session.payout()));
        recorded.add(session.roundId());
    }

    public void recoverRound(UUID id) {
        RoundJournal.Entry entry = journal.get(id);
        if (!entry.game().equals("roulette") || entry.cancelled()) return;
        if (entry.complete()) {
            recordHistory(RouletteSession.restore(entry.player(), id, entry.data()));
            return;
        }
        if (entry.needsReview())
            throw new IllegalStateException("Roulette round needs review: " + id);
        var stake = entry.transactions().get("stake");
        if (stake == null || stake.state() == RoundJournal.State.REJECTED) {
            journal.cancelUnfunded(id);
            sessions.remove(entry.player());
            return;
        }
        sessions.put(entry.player(), RouletteSession.restore(entry.player(), id, entry.data()));
        closeSession(entry.player());
    }

    public void recoverAll(java.util.function.Consumer<String> errors) {
        for (RoundJournal.Entry entry : journal.entries("roulette")) {
            try {
                recoverRound(entry.id());
            } catch (RuntimeException exception) {
                errors.accept(exception.getMessage());
            }
        }
    }

    public record StartRoundOutcome(boolean started, RouletteSession session, String error) {
        private static StartRoundOutcome success(RouletteSession session) {
            return new StartRoundOutcome(true, session, null);
        }

        private static StartRoundOutcome failure(RouletteSession session, String error) {
            return new StartRoundOutcome(false, session, error);
        }
    }
}
