package pl.kurdeslawo.casinocore.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All engine, journal and provider amounts are hundredths of the server currency. */
public final class Money {
    private Money() {}

    public static long parse(String value) {
        long amount = new BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact();
        if (amount < 0) throw new IllegalArgumentException("Kwota nie może być ujemna");
        return amount;
    }

    public static String format(long amount) {
        return BigDecimal.valueOf(amount, 2).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static double toVault(long amount) {
        if (amount < 0 || amount > 100_000_000_000_000L)
            throw new IllegalArgumentException("Kwota poza bezpiecznym zakresem Vault");
        return BigDecimal.valueOf(amount, 2).doubleValue();
    }

    public static long fromVault(double value) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Nieprawidłowe saldo Vault");
        return BigDecimal.valueOf(Math.max(0, value))
                .movePointRight(2)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }
}
