package pl.kurdeslawo.casinocore.roulette;

import java.util.Objects;
import java.util.UUID;

public record RouletteRoundRecord(
        UUID playerId,
        RouletteResult result,
        RouletteBetType betType,
        Integer selectedNumber,
        long stake,
        long payout) {
    public RouletteRoundRecord {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(betType, "betType");
        new RouletteBet(playerId, betType, stake, selectedNumber);
        if (payout < 0) {
            throw new IllegalArgumentException("Payout cannot be negative");
        }
    }

    public static RouletteRoundRecord from(RouletteBet bet, RouletteResult result, long payout) {
        return new RouletteRoundRecord(
                bet.playerId(), result, bet.type(), bet.selectedNumber(), bet.stake(), payout);
    }

    public boolean won() {
        return payout > 0;
    }

    public long netChange() {
        return payout - stake;
    }
}
