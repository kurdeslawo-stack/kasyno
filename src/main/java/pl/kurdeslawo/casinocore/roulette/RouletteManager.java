package pl.kurdeslawo.casinocore.roulette;

import pl.kurdeslawo.casinocore.economy.EconomyProvider;

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
    private final Map<UUID, RouletteSession> sessions = new ConcurrentHashMap<>();
    private volatile List<Long> allowedStakes;
    private volatile RouletteEngine engine;

    public RouletteManager(EconomyProvider economy, RouletteRandom random, List<Long> allowedStakes,
                           int outsideMultiplier, int exactMultiplier) {
        this(economy, random, allowedStakes, outsideMultiplier, exactMultiplier,
            new InMemoryRouletteHistoryRepository());
    }

    public RouletteManager(EconomyProvider economy, RouletteRandom random, List<Long> allowedStakes,
                           int outsideMultiplier, int exactMultiplier, RouletteHistoryRepository history) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.random = Objects.requireNonNull(random, "random");
        this.history = Objects.requireNonNull(history, "history");
        updateConfiguration(allowedStakes, outsideMultiplier, exactMultiplier);
    }

    public RouletteSession openSession(UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new RouletteSession(id, allowedStakes.getFirst()));
    }

    public Optional<RouletteSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void selectBet(UUID playerId, RouletteBetType type, Integer selectedNumber) {
        RouletteSession session = openSession(playerId);
        synchronized (session) {
            session.selectBet(new RouletteBet(playerId, type, session.selectedStake(), selectedNumber));
        }
    }

    public StartRoundOutcome startRound(UUID playerId) {
        RouletteSession session = openSession(playerId);
        synchronized (session) {
            if (session.phase() != RoulettePhase.SELECTING) {
                return StartRoundOutcome.failure(session, "A round is already active.");
            }
            long stake = session.selectedStake();
            if (!allowedStakes.contains(stake)) {
                return StartRoundOutcome.failure(session, "The selected stake is no longer available.");
            }
            RouletteBet bet = session.selectedBet();
            if (bet == null) {
                return StartRoundOutcome.failure(session, "Select a bet first.");
            }
            RouletteResult result = random.nextResult();
            long totalReturn = engine.totalReturn(bet, result);
            if (!economy.has(playerId, stake) || !economy.withdraw(playerId, stake)) {
                return StartRoundOutcome.failure(session, "You do not have enough coins.");
            }

            session.beginSpin(bet, result, totalReturn);
            return StartRoundOutcome.success(session);
        }
    }

    public boolean settle(UUID playerId) {
        RouletteSession session = sessions.get(playerId);
        if (session == null || !session.claimSettlement()) {
            return false;
        }
        try {
            RouletteBet bet = session.selectedBet();
            RouletteResult result = session.result();
            long payout = session.expectedPayout();
            if (payout > 0) {
                economy.deposit(playerId, payout);
            }
            session.completeSettlement(payout);
            history.record(RouletteRoundRecord.from(bet, result, payout));
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

    public void updateConfiguration(List<Long> stakes, int outsideMultiplier, int exactMultiplier) {
        if (stakes.isEmpty() || stakes.stream().anyMatch(stake -> stake == null || stake <= 0)) {
            throw new IllegalArgumentException("Roulette stakes must contain positive values");
        }
        this.allowedStakes = stakes.stream().distinct().sorted().toList();
        this.engine = new RouletteEngine(outsideMultiplier, exactMultiplier);
    }

    public List<Long> allowedStakes() {
        return allowedStakes;
    }

    public int outsideMultiplier() { return engine.outsideMultiplier(); }
    public int exactMultiplier() { return engine.exactMultiplier(); }

    public Collection<RouletteSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public List<RouletteRoundRecord> recentRounds(UUID playerId) {
        return history.recentRounds(playerId);
    }

    public RoulettePlayerStats playerStats(UUID playerId) {
        return history.stats(playerId);
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
