package pl.kurdeslawo.casinocore.economy;

public final class EconomyTransactionException extends RuntimeException {
    private final boolean rejected;

    public EconomyTransactionException(String message) {
        super(message);
        rejected = false;
    }

    public EconomyTransactionException(String message, Throwable cause) {
        super(message, cause);
        rejected = false;
    }

    private EconomyTransactionException(String message, boolean rejected) {
        super(message);
        this.rejected = rejected;
    }

    public static EconomyTransactionException rejected(String message) {
        return new EconomyTransactionException(message, true);
    }

    public boolean definitelyRejected() {
        return rejected;
    }
}
