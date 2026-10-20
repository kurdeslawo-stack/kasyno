package pl.kurdeslawo.casinocore.roulette;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Fixed, uniform distribution. No weights or administrator chance overrides. */
public final class RouletteRandom {
    public static final int MIN_NUMBER = 0;
    public static final int MAX_NUMBER = RouletteResult.DOUBLE_ZERO;
    public static final int NUMBER_COUNT = RouletteResult.POCKET_COUNT;
    private final RandomGenerator random;

    public RouletteRandom() {
        this(RandomGenerator.getDefault());
    }

    public RouletteRandom(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    public RouletteResult nextResult() {
        int number = random.nextInt(NUMBER_COUNT);
        return new RouletteResult(number, RouletteEngine.colorOf(number));
    }

    public double probability(int number) {
        if (number < 0 || number > MAX_NUMBER)
            throw new IllegalArgumentException("Pole: 0, 00 lub 1–10");
        return 1.0 / NUMBER_COUNT;
    }
}
