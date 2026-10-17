package pl.kurdeslawo.casinocore.roulette;

import java.util.Objects;
import java.util.List;
import java.util.random.RandomGenerator;

public final class RouletteRandom {
    public static final int MIN_NUMBER = 0;
    public static final int MAX_NUMBER = 12;
    private final RandomGenerator random;
    public static final long MAX_WEIGHT = 1_000_000;
    private record Distribution(List<Long> weights, long total, boolean uniform) { }
    private volatile Distribution distribution = distribution(java.util.Collections.nCopies(13, 1L));

    public RouletteRandom() {
        this(RandomGenerator.getDefault());
    }

    public RouletteRandom(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public RouletteResult nextResult() {
        Distribution current = distribution;
        int number;
        if (current.uniform) {
            number = random.nextInt(MIN_NUMBER, MAX_NUMBER + 1);
        } else {
            long ticket = random.nextLong(current.total);
            number = 0;
            long cumulative = current.weights.getFirst();
            while (ticket >= cumulative) cumulative += current.weights.get(++number);
        }
        return new RouletteResult(number, RouletteEngine.colorOf(number));
    }

    public void updateWeights(List<Long> weights) { distribution = distribution(weights); }
    public List<Long> weights() { return distribution.weights; }
    public double probability(int number) {
        if (number < 0 || number > 12) throw new IllegalArgumentException("Numer musi być od 0 do 12");
        Distribution current = distribution;
        return (double) current.weights.get(number) / current.total;
    }
    public static void validateWeights(List<Long> weights) { distribution(weights); }
    private static Distribution distribution(List<Long> weights) {
        if (weights == null || weights.size() != 13
            || weights.stream().anyMatch(w -> w == null || w < 0 || w > MAX_WEIGHT)) {
            throw new IllegalArgumentException("Wagi 0–12 muszą być liczbami całkowitymi od 0 do " + MAX_WEIGHT);
        }
        List<Long> copy = List.copyOf(weights);
        long total = copy.stream().mapToLong(Long::longValue).sum();
        if (total == 0) throw new IllegalArgumentException("Co najmniej jedna waga musi być większa od zera");
        return new Distribution(copy, total, copy.stream().distinct().count() == 1);
    }
}
