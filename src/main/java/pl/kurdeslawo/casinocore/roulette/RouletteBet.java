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
        if (type == RouletteBetType.STRAIGHT && (selectedNumber == null || selectedNumber < 0 || selectedNumber > 36)) {
            throw new IllegalArgumentException("A straight bet requires a number from 0 to 36");
        }
        if (type != RouletteBetType.STRAIGHT && selectedNumber != null) {
            throw new IllegalArgumentException("Only a straight bet can select a number");
        }
    }
}
