package pl.kurdeslawo.casinocore.economy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

class VaultTransactionOutcomeTest {
    @Test void rejectedResponseIsDistinguishedFromLostResponse() {
        UUID id = UUID.randomUUID();
        Economy vault = mock(Economy.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(id)).thenReturn(player);
            VaultEconomyProvider provider = new VaultEconomyProvider(vault);
            when(vault.depositPlayer(player, 5D)).thenReturn(
                new EconomyResponse(0, 100, EconomyResponse.ResponseType.FAILURE, "declined"));
            assertTrue(assertThrows(EconomyTransactionException.class, () -> provider.deposit(id, 5)).definitelyRejected());
            when(vault.depositPlayer(player, 5D)).thenThrow(new IllegalStateException("timeout"));
            assertFalse(assertThrows(EconomyTransactionException.class, () -> provider.deposit(id, 5)).definitelyRejected());
        }
    }

    @Test void nullWithdrawalResponseIsUnknownAndSuccessIsAccepted() {
        UUID id = UUID.randomUUID();
        Economy vault = mock(Economy.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(id)).thenReturn(player);
            VaultEconomyProvider provider = new VaultEconomyProvider(vault);
            assertFalse(assertThrows(EconomyTransactionException.class, () -> provider.withdraw(id, 5)).definitelyRejected());
            when(vault.withdrawPlayer(player, 5D)).thenReturn(
                new EconomyResponse(5, 95, EconomyResponse.ResponseType.SUCCESS, null));
            assertTrue(provider.withdraw(id, 5));
        }
    }
}
