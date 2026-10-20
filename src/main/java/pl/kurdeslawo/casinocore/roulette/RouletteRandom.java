package pl.kurdeslawo.casinocore.roulette;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Fixed, uniform distribution. No weights or administrator chance overrides. */
public final class RouletteRandom {
    public static final int MIN_NUMBER = 0;
    public static final int MAX_NUMBER = 10;
    public static final int NUMBER_COUNT = 11;
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
        if (number < 0 || number > MAX_NUMBER) throw new IllegalArgumentException("Numer: 0–10");
        return 1.0 / NUMBER_COUNT;
    }
}
