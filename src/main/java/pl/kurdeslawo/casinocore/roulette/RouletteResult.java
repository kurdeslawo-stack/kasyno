package pl.kurdeslawo.casinocore.roulette;

public record RouletteResult(int winningNumber, RouletteColor color) {
    public RouletteResult {
        if (winningNumber < 0 || winningNumber > 10) {
            throw new IllegalArgumentException("Winning number must be from 0 to 10");
        }
        RouletteColor expected = RouletteEngine.colorOf(winningNumber);
        if (color != expected) {
            throw new IllegalArgumentException("Color does not match winning number");
        }
    }
}
