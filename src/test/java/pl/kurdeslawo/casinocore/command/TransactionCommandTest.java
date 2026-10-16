package pl.kurdeslawo.casinocore.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import pl.kurdeslawo.casinocore.CasinoCorePlugin;
import pl.kurdeslawo.casinocore.blackjack.BlackjackManager;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.RoundJournal;

class TransactionCommandTest {
    @Test void pendingListCanReachEntriesBeyondFirstTwenty() {
        Fixture f = new Fixture(true);
        for (int i = 0; i < 24; i++) f.journal.begin(UUID.randomUUID(), UUID.randomUUID(), "roulette", Map.of());
        f.run("transactions", "list", "2");
        verify(f.sender).sendMessage(contains("page 2/2"));
        verify(f.sender, times(6)).sendMessage(anyString());
    }
    @Test void nonAdminCannotResolveOrRetryMoney() {
        Fixture f = new Fixture(false);
        f.run("transactions", "resolve", f.id.toString(), "stake", "applied", "evidence");
        f.run("transactions", "retry", f.id.toString());
        assertTrue(f.journal.get(f.id).needsReview());
        verifyNoInteractions(f.manager);
        verify(f.economy, times(1)).withdraw(any(), anyLong());
    }

    @Test void evidenceIsRequiredAndResolutionDoesNotTransferMoney() {
        Fixture f = new Fixture(true);
        f.run("transactions", "resolve", f.id.toString(), "stake", "applied");
        assertTrue(f.journal.get(f.id).needsReview());
        f.run("transactions", "resolve", f.id.toString(), "stake", "applied", "provider-log-123");
        assertFalse(f.journal.get(f.id).needsReview());
        assertTrue(f.journal.get(f.id).transactions().get("stake").detail().contains("Admin: provider-log-123"));
        verify(f.manager).recoverRound(f.id);
        verify(f.economy, times(1)).withdraw(any(), anyLong());
    }

    @Test void unknownTransactionCannotUseRetryOrBeResolvedTwice() {
        Fixture f = new Fixture(true);
        f.run("transactions", "retry", f.id.toString());
        verifyNoInteractions(f.manager);
        f.run("transactions", "resolve", f.id.toString(), "stake", "applied", "provider-log-123");
        f.run("transactions", "resolve", f.id.toString(), "stake", "not-applied", "different-note");
        assertEquals(RoundJournal.State.SUCCESS, f.journal.get(f.id).transactions().get("stake").state());
        verify(f.manager, times(1)).recoverRound(f.id);
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final UUID player = UUID.randomUUID();
        final RoundJournal journal = new RoundJournal();
        final EconomyProvider economy = mock(EconomyProvider.class);
        final CasinoCorePlugin plugin = mock(CasinoCorePlugin.class);
        final BlackjackManager manager = mock(BlackjackManager.class);
        final CommandSender sender = mock(CommandSender.class);
        final Command command = mock(Command.class);
        final CasinoCommand executor;
        Fixture(boolean admin) {
            when(plugin.roundJournal()).thenReturn(journal);
            when(plugin.blackjackManager()).thenReturn(manager);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            when(sender.hasPermission("casinocore.admin")).thenReturn(admin);
            when(sender.getName()).thenReturn("Admin");
            journal.begin(id, player, "blackjack", Map.of());
            when(economy.withdraw(player, 5)).thenThrow(new IllegalStateException("lost response"));
            assertThrows(IllegalStateException.class, () -> journal.withdraw(id, "stake", 5, economy));
            executor = new CasinoCommand(plugin);
        }
        void run(String... args) { assertTrue(executor.onCommand(sender, command, "casino", args)); }
    }
}
