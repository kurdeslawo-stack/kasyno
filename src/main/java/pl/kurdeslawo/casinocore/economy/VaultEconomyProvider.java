package pl.kurdeslawo.casinocore.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.UUID;

public final class VaultEconomyProvider implements EconomyProvider {
    private final Economy economy;

    public VaultEconomyProvider(Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public long getBalance(UUID playerId) {
        double balance;
        try {
            balance = economy.getBalance(player(playerId));
        } catch (RuntimeException exception) {
            throw failure("balance lookup", playerId, exception);
        }
        if (!Double.isFinite(balance)) {
            throw new EconomyTransactionException(
                    "Vault provider '"
                            + providerName()
                            + "' returned a non-finite balance for "
                            + playerId
                            + ".");
        }
        return Money.fromVault(balance);
    }

    @Override
    public boolean has(UUID playerId, long amount) {
        try {
            return economy.has(player(playerId), vaultAmount(amount));
        } catch (RuntimeException exception) {
            throw failure("balance check", playerId, exception);
        }
    }

    @Override
    public boolean withdraw(UUID playerId, long amount) {
        EconomyResponse response;
        try {
            response = economy.withdrawPlayer(player(playerId), vaultAmount(amount));
        } catch (RuntimeException exception) {
            throw failure("withdrawal", playerId, exception);
        }
        requireSuccess(response, "withdrawal", playerId, amount);
        return true;
    }

    @Override
    public void deposit(UUID playerId, long amount) {
        EconomyResponse response;
        try {
            response = economy.depositPlayer(player(playerId), vaultAmount(amount));
        } catch (RuntimeException exception) {
            throw failure("deposit", playerId, exception);
        }
        requireSuccess(response, "deposit", playerId, amount);
    }

    public int fractionalDigits() {
        return economy.fractionalDigits();
    }

    public String providerName() {
        try {
            String name = economy.getName();
            return name == null || name.isBlank() ? economy.getClass().getSimpleName() : name;
        } catch (RuntimeException exception) {
            return economy.getClass().getSimpleName();
        }
    }

    private void requireSuccess(
            EconomyResponse response, String operation, UUID playerId, long amount) {
        if (response == null) {
            throw new EconomyTransactionException(
                    "Vault provider '"
                            + providerName()
                            + "' returned no response for "
                            + operation
                            + " of "
                            + Money.format(amount)
                            + " currency units for "
                            + playerId
                            + ".");
        }
        if (!response.transactionSuccess()) {
            String reason =
                    response.errorMessage == null || response.errorMessage.isBlank()
                            ? "no reason supplied"
                            : response.errorMessage;
            throw EconomyTransactionException.rejected(
                    "Vault provider '"
                            + providerName()
                            + "' rejected "
                            + operation
                            + " of "
                            + Money.format(amount)
                            + " currency units for "
                            + playerId
                            + ": "
                            + reason);
        }
        // A successful response for a different amount is ambiguous; never replay it blindly.
        if (!Double.isFinite(response.amount)
                || Math.abs(response.amount - Money.toVault(amount)) > 0.000001) {
            throw new EconomyTransactionException(
                    "Vault provider '"
                            + providerName()
                            + "' reported a different amount during "
                            + operation
                            + " for "
                            + playerId);
        }
    }

    private EconomyTransactionException failure(
            String operation, UUID playerId, RuntimeException cause) {
        return new EconomyTransactionException(
                "Vault provider '"
                        + providerName()
                        + "' failed during "
                        + operation
                        + " for "
                        + playerId
                        + ": "
                        + cause.getMessage(),
                cause);
    }

    private static OfflinePlayer player(UUID playerId) {
        return Bukkit.getOfflinePlayer(Objects.requireNonNull(playerId, "playerId"));
    }

    private static double vaultAmount(long amount) {
        return Money.toVault(amount);
    }
}
