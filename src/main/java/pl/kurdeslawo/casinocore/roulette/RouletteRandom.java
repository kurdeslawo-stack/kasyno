package pl.kurdeslawo.casinocore.roulette;

import java.util.Objects;
import java.util.random.RandomGenerator;

public final class RouletteRandom {
    public static final int MIN_NUMBER = 0;
    public static final int MAX_NUMBER = 12;
    private final RandomGenerator random;

    public RouletteRandom() {
        this(RandomGenerator.getDefault());
    }

    public RouletteRandom(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public RouletteResult nextResult() {
        int number = random.nextInt(MIN_NUMBER, MAX_NUMBER + 1);
        return new RouletteResult(number, RouletteEngine.colorOf(number));
    }
}
