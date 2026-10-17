package pl.kurdeslawo.casinocore.roulette;

import java.util.Objects;
import java.util.UUID;

public record RouletteBet(UUID playerId, RouletteBetType type, long stake, Integer selectedNumber) {
    public RouletteBet {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (stake <= 0) {
            throw new IllegalArgumentException("Stake must be positive");
        }
        if (type == RouletteBetType.EXACT_NUMBER
                && (selectedNumber == null || selectedNumber < 0 || selectedNumber > 12)) {
            throw new IllegalArgumentException(
                    "An exact-number bet requires a number from 0 to 12");
        }
        if (type != RouletteBetType.EXACT_NUMBER && selectedNumber != null) {
            throw new IllegalArgumentException("Only an exact-number bet can select a number");
        }
    }
}
