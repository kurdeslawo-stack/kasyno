package pl.kurdeslawo.casinocore.economy;

import java.util.UUID;

public interface EconomyProvider {
    long getBalance(UUID playerId);

    boolean has(UUID playerId, long amount);

    boolean withdraw(UUID playerId, long amount);

    void deposit(UUID playerId, long amount);
}
