package pl.kurdeslawo.casinocore.roulette;

import java.math.BigDecimal;

public final class RouletteEngine {
    private final BigDecimal outsideMultiplier;
    private final BigDecimal exactMultiplier;

    public RouletteEngine(BigDecimal outside, BigDecimal exact) {
        if (outside.signum() <= 0 || exact.signum() <= 0)
            throw new IllegalArgumentException("Mnożniki muszą być dodatnie");
        outsideMultiplier = outside.stripTrailingZeros();
        exactMultiplier = exact.stripTrailingZeros();
    }

    public boolean wins(RouletteBet bet, RouletteResult result) {
        int n = result.winningNumber();
        if (bet.type() == RouletteBetType.EXACT_NUMBER) return bet.selectedNumber() == n;
        if (RouletteResult.isZero(n)) return false;
        return switch (bet.type()) {
            case RED -> result.color() == RouletteColor.RED;
            case BLACK -> result.color() == RouletteColor.BLACK;
            case LOW -> n <= 5;
            case HIGH -> n >= 6;
            case EXACT_NUMBER -> false;
        };
    }

    public long totalReturn(RouletteBet bet, RouletteResult result) {
        if (!wins(bet, result)) return 0;
        // Exact cents. Reject unsupported precision at configuration time, never round payouts.
        return BigDecimal.valueOf(bet.stake())
                .multiply(
                        bet.type() == RouletteBetType.EXACT_NUMBER
                                ? exactMultiplier
                                : outsideMultiplier)
                .longValueExact();
    }

    public BigDecimal outsideMultiplier() {
        return outsideMultiplier;
    }

    public BigDecimal exactMultiplier() {
        return exactMultiplier;
    }

    public static RouletteColor colorOf(int n) {
        if (n < 0 || n >= RouletteResult.POCKET_COUNT)
            throw new IllegalArgumentException("Pole: 0, 00 lub 1–10");
        return RouletteResult.isZero(n)
                ? RouletteColor.GREEN
                : n % 2 == 1 ? RouletteColor.RED : RouletteColor.BLACK;
    }
}
