package pl.kurdeslawo.casinocore.roulette;

public record RoulettePlayerStats(
        long roundsPlayed, long roundsWon, long roundsLost, long totalStaked, long totalPayout) {
    public static final RoulettePlayerStats EMPTY = new RoulettePlayerStats(0, 0, 0, 0, 0);

    public long netBalance() {
        return totalPayout - totalStaked;
    }
}
