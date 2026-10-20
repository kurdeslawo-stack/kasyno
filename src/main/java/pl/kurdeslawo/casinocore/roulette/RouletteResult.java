package pl.kurdeslawo.casinocore.roulette;

public record RouletteResult(int winningNumber, RouletteColor color) {
    // Stable journal IDs: 0..10 retain their meaning; 11 represents the separate 00 pocket.
    public static final int DOUBLE_ZERO = 11;
    public static final int POCKET_COUNT = 12;

    public static boolean isZero(int number) {
        return number == 0 || number == DOUBLE_ZERO;
    }

    public static String label(int number) {
        return number == DOUBLE_ZERO ? "00" : Integer.toString(number);
    }

    public RouletteResult {
        if (winningNumber < 0 || winningNumber >= POCKET_COUNT) {
            throw new IllegalArgumentException("Winning pocket must be 0..10 or 00 (ID 11)");
        }
        RouletteColor expected = RouletteEngine.colorOf(winningNumber);
        if (color != expected) {
            throw new IllegalArgumentException("Color does not match winning number");
        }
    }
}
