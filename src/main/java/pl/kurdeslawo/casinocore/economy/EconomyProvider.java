package pl.kurdeslawo.casinocore.economy;

import java.util.UUID;

public interface EconomyProvider {
    // withdraw=false and EconomyTransactionException.rejected(...) must mean no
    // transfer occurred. Any other transaction exception is ambiguous: a caller
    // must not retry it without reconciliation. Balance differences are not proof.
    long getBalance(UUID playerId);

    boolean has(UUID playerId, long amount);

    boolean withdraw(UUID playerId, long amount);

    void deposit(UUID playerId, long amount);
}
