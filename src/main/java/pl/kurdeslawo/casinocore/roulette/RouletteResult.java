package pl.kurdeslawo.casinocore.roulette;

import java.util.Set;

public record RouletteResult(int winningNumber, Set<RouletteBetType> winningTypes) {
    public RouletteResult {
        if (winningNumber < 0 || winningNumber > 36) {
            throw new IllegalArgumentException("Winning number must be from 0 to 36");
        }
        winningTypes = Set.copyOf(winningTypes);
    }
}
