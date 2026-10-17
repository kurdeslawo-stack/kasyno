package pl.kurdeslawo.casinocore.roulette;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RouletteSession {
    private final UUID playerId;
    private UUID roundId;
    private long selectedStake;
    private RouletteBet selectedBet;
    private RouletteResult result;
    private RoulettePhase phase = RoulettePhase.SELECTING;
    private boolean stakeWithdrawn;
    private boolean settlementInProgress;
    private boolean settled;
    private long expectedPayout;
    private long payout;
    private long resultStake;

    public RouletteSession(UUID playerId, long selectedStake) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        selectStake(selectedStake);
    }

    public synchronized void selectStake(long stake) {
        requirePhase(RoulettePhase.SELECTING);
        if (stake <= 0) {
            throw new IllegalArgumentException("Stake must be positive");
        }
        selectedStake = stake;
        if (selectedBet != null) {
            selectedBet =
                    new RouletteBet(
                            playerId, selectedBet.type(), stake, selectedBet.selectedNumber());
        }
    }

    public synchronized void selectBet(RouletteBet bet) {
        requirePhase(RoulettePhase.SELECTING);
        if (!bet.playerId().equals(playerId) || bet.stake() != selectedStake) {
            throw new IllegalArgumentException("Bet does not match this session");
        }
        selectedBet = Objects.requireNonNull(bet, "bet");
    }

    public synchronized void beginSpin(
            RouletteBet bet, RouletteResult drawnResult, long expectedPayout) {
        requirePhase(RoulettePhase.SELECTING);
        if (!bet.playerId().equals(playerId) || bet.stake() != selectedStake) {
            throw new IllegalArgumentException("Bet does not match this session");
        }
        if (!Objects.equals(selectedBet, bet)) {
            throw new IllegalArgumentException("Bet is not selected in this session");
        }
        result = Objects.requireNonNull(drawnResult, "drawnResult");
        if (expectedPayout < 0) {
            throw new IllegalArgumentException("Expected payout cannot be negative");
        }
        this.expectedPayout = expectedPayout;
        stakeWithdrawn = true;
        phase = RoulettePhase.SPINNING;
        roundId = UUID.randomUUID();
    }

    public synchronized boolean claimSettlement() {
        if (!stakeWithdrawn || settled || settlementInProgress) {
            return false;
        }
        settlementInProgress = true;
        return true;
    }

    public synchronized void completeSettlement(long payout) {
        if (!settlementInProgress) {
            throw new IllegalStateException("Settlement was not claimed");
        }
        this.payout = payout;
        resultStake = selectedBet.stake();
        settlementInProgress = false;
        settled = true;
        phase = RoulettePhase.RESULT;
    }

    public synchronized void failSettlement() {
        settlementInProgress = false;
    }

    public synchronized void resetForNextRound() {
        if (phase != RoulettePhase.RESULT || !settled) {
            throw new IllegalStateException("The current round is not settled");
        }
        stakeWithdrawn = false;
        settled = false;
        expectedPayout = 0L;
        phase = RoulettePhase.SELECTING;
    }

    private void requirePhase(RoulettePhase required) {
        if (phase != required) {
            throw new IllegalStateException("Session is in phase " + phase);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public synchronized long selectedStake() {
        return selectedStake;
    }

    public synchronized RouletteBet selectedBet() {
        return selectedBet;
    }

    public synchronized RouletteResult result() {
        return result;
    }

    public synchronized RoulettePhase phase() {
        return phase;
    }

    public synchronized boolean stakeWithdrawn() {
        return stakeWithdrawn;
    }

    public synchronized boolean settled() {
        return settled;
    }

    public synchronized long expectedPayout() {
        return expectedPayout;
    }

    public synchronized long payout() {
        return payout;
    }

    public synchronized long resultStake() {
        return resultStake;
    }

    public synchronized UUID roundId() {
        return roundId;
    }

    public synchronized Map<String, String> snapshot() {
        return Map.of(
                "stake",
                Long.toString(selectedStake),
                "bet",
                selectedBet.type().name(),
                "number",
                selectedBet.selectedNumber() == null ? "" : selectedBet.selectedNumber().toString(),
                "result",
                Integer.toString(result.winningNumber()),
                "expected",
                Long.toString(expectedPayout),
                "payout",
                Long.toString(payout),
                "settled",
                Boolean.toString(settled));
    }

    public static RouletteSession restore(UUID player, UUID round, Map<String, String> data) {
        RouletteSession s = new RouletteSession(player, Long.parseLong(data.get("stake")));
        Integer number = data.get("number").isEmpty() ? null : Integer.valueOf(data.get("number"));
        RouletteBet bet =
                new RouletteBet(
                        player, RouletteBetType.valueOf(data.get("bet")), s.selectedStake, number);
        s.selectBet(bet);
        int winning = Integer.parseInt(data.get("result"));
        s.beginSpin(
                bet,
                new RouletteResult(winning, RouletteEngine.colorOf(winning)),
                Long.parseLong(data.get("expected")));
        s.roundId = round;
        if (Boolean.parseBoolean(data.get("settled"))) {
            s.claimSettlement();
            s.completeSettlement(Long.parseLong(data.get("payout")));
        }
        return s;
    }
}
