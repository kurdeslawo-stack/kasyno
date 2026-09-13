package pl.kurdeslawo.casinocore.roulette;

import java.util.Set;

public final class RouletteEngine {
    private static final Set<Integer> RED_NUMBERS = Set.of(1, 3, 5, 7, 9, 12);
    private final int outsideMultiplier;
    private final int exactMultiplier;

    public RouletteEngine(int outsideMultiplier, int exactMultiplier) {
        if (outsideMultiplier <= 0 || exactMultiplier <= 0) {
            throw new IllegalArgumentException("Payout multipliers must be positive");
        }
        this.outsideMultiplier = outsideMultiplier;
        this.exactMultiplier = exactMultiplier;
    }

    public boolean wins(RouletteBet bet, RouletteResult result) {
        int number = result.winningNumber();
        if (bet.type() == RouletteBetType.EXACT_NUMBER) {
            return bet.selectedNumber() == number;
        }
        if (number == 0) {
            return false;
        }
        return switch (bet.type()) {
            case RED -> result.color() == RouletteColor.RED;
            case BLACK -> result.color() == RouletteColor.BLACK;
            case EVEN -> number % 2 == 0;
            case ODD -> number % 2 != 0;
            case LOW -> number >= 1 && number <= 6;
            case HIGH -> number >= 7 && number <= 12;
            case EXACT_NUMBER -> false;
        };
    }

    public long totalReturn(RouletteBet bet, RouletteResult result) {
        if (!wins(bet, result)) {
            return 0L;
        }
        int multiplier = bet.type() == RouletteBetType.EXACT_NUMBER ? exactMultiplier : outsideMultiplier;
        return Math.multiplyExact(bet.stake(), multiplier);
    }

    public int outsideMultiplier() {
        return outsideMultiplier;
    }

    public int exactMultiplier() {
        return exactMultiplier;
    }

    public static RouletteColor colorOf(int number) {
        if (number < 0 || number > 12) {
            throw new IllegalArgumentException("Roulette number must be from 0 to 12");
        }
        if (number == 0) {
            return RouletteColor.GREEN;
        }
        return RED_NUMBERS.contains(number) ? RouletteColor.RED : RouletteColor.BLACK;
    }
}
