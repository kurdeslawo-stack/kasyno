package pl.kurdeslawo.casinocore.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.UUID;

public final class VaultEconomyProvider implements EconomyProvider {
    private static final long MAX_EXACT_DOUBLE_INTEGER = 9_007_199_254_740_992L;
    private final Economy economy;

    public VaultEconomyProvider(Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public long getBalance(UUID playerId) {
        double balance = economy.getBalance(player(playerId));
        if (!Double.isFinite(balance) || balance <= 0) {
            return 0L;
        }
        return balance >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(balance);
    }

    @Override
    public boolean has(UUID playerId, long amount) {
        return economy.has(player(playerId), vaultAmount(amount));
    }

    @Override
    public boolean withdraw(UUID playerId, long amount) {
        EconomyResponse response = economy.withdrawPlayer(player(playerId), vaultAmount(amount));
        return response.transactionSuccess();
    }

    @Override
    public void deposit(UUID playerId, long amount) {
        EconomyResponse response = economy.depositPlayer(player(playerId), vaultAmount(amount));
        if (!response.transactionSuccess()) {
            throw new EconomyTransactionException("Vault deposit failed: " + response.errorMessage);
        }
    }

    private static OfflinePlayer player(UUID playerId) {
        return Bukkit.getOfflinePlayer(Objects.requireNonNull(playerId, "playerId"));
    }

    private static double vaultAmount(long amount) {
        if (amount < 0 || amount > MAX_EXACT_DOUBLE_INTEGER) {
            throw new IllegalArgumentException("Vault amount must be between 0 and 2^53");
        }
        return amount;
    }
}
