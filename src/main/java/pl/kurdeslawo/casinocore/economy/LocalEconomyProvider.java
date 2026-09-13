package pl.kurdeslawo.casinocore.economy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalEconomyProvider implements EconomyProvider {
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final long startingBalance;

    public LocalEconomyProvider(long startingBalance) {
        if (startingBalance < 0) {
            throw new IllegalArgumentException("Starting balance cannot be negative");
        }
        this.startingBalance = startingBalance;
    }

    @Override
    public long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, startingBalance);
    }

    @Override
    public boolean has(UUID playerId, long amount) {
        requireNonNegative(amount);
        return getBalance(playerId) >= amount;
    }

    @Override
    public synchronized boolean withdraw(UUID playerId, long amount) {
        requireNonNegative(amount);
        long balance = getBalance(playerId);
        if (balance < amount) {
            return false;
        }
        balances.put(playerId, balance - amount);
        return true;
    }

    @Override
    public synchronized void deposit(UUID playerId, long amount) {
        requireNonNegative(amount);
        balances.put(playerId, Math.addExact(getBalance(playerId), amount));
    }

    public void setBalance(UUID playerId, long amount) {
        requireNonNegative(amount);
        balances.put(playerId, amount);
    }

    private static void requireNonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
}
