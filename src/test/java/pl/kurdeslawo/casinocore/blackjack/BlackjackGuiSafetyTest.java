package pl.kurdeslawo.casinocore.blackjack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.kurdeslawo.casinocore.blackjack.gui.*;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;

class BlackjackGuiSafetyTest {
    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {"SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DOUBLE_CLICK", "SWAP_OFFHAND", "DROP", "CONTROL_DROP"})
    void inventoryManipulationsAreCancelled(ClickType type) {
        Fixture f = new Fixture();
        InventoryClickEvent event = f.click(BlackjackGui.SIZE + 1);
        when(event.getClick()).thenReturn(type);
        f.listener.onClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(f.economy);
    }

    @Test
    void dragIsCancelled() {
        Fixture f = new Fixture();
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(f.view);
        f.listener.onDrag(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(f.economy);
    }

    @Test
    void oneHundredStartClicksFundExactlyOneRound() {
        Fixture f = new Fixture();
        for (int i = 0; i < 100; i++) f.listener.onClick(f.click(BlackjackGui.STATUS));
        verify(f.economy, times(1)).withdraw(f.id, 5);
        verify(f.animation, times(1)).deal(f.player, f.inventory);
        assertEquals(BlackjackPhase.DEALING, f.manager.session(f.id).orElseThrow().phase());
    }

    @Test
    void wrongPlayerCannotOperateSomeoneElsesMenu() {
        Fixture f = new Fixture();
        when(f.player.getUniqueId()).thenReturn(UUID.randomUUID());
        InventoryClickEvent event = f.click(BlackjackGui.STATUS);
        f.listener.onClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(f.economy, f.animation);
    }

    @Test
    void closingDuringDealSettlesOnceAndStopsAnimation() {
        Fixture f = new Fixture();
        f.manager.start(f.id);
        f.listener.onClose(f.close());
        f.listener.onQuit(f.quit());
        verify(f.animation, times(2)).cancel(f.id);
        verify(f.economy, times(1)).withdraw(f.id, 5);
        verify(f.economy, times(1)).deposit(f.id, 10);
        assertEquals(1, f.manager.recent(f.id).size());
        assertTrue(f.manager.session(f.id).isEmpty());
    }

    @Test
    void closingBeforeDealHasNoEconomicEffect() {
        Fixture f = new Fixture();
        f.listener.onClose(f.close());
        verifyNoInteractions(f.economy);
        assertTrue(f.manager.recent(f.id).isEmpty());
    }

    @Test
    void repeatedStandAndDoubleClicksAfterSettlementDoNotPayAgain() {
        Fixture f = new Fixture();
        f.manager.start(f.id);
        f.manager.finishDeal(f.id);
        f.listener.onClick(f.click(BlackjackGui.STAND));
        for (int i = 0; i < 100; i++) {
            f.listener.onClick(f.click(BlackjackGui.STAND));
            f.listener.onClick(f.click(BlackjackGui.DOUBLE));
        }
        verify(f.economy, times(1)).withdraw(f.id, 5);
        verify(f.economy, times(1)).deposit(f.id, 10);
        assertEquals(1, f.manager.recent(f.id).size());
    }

    @Test
    void dealCallbacksStopAfterCloseEvenIfAlreadyQueued() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        animation.deal(f.player, f.inventory);
        Pending queued = f.pending.removeFirst();
        animation.cancel(f.id);
        f.manager.closeSession(f.id);
        clearInvocations(f.gui);
        queued.action.run();
        verify(queued.task).cancel();
        verifyNoInteractions(f.gui);
        assertTrue(f.pending.isEmpty());
        assertTrue(f.manager.session(f.id).isEmpty());
    }

    @Test
    void offlinePlayerDoesNotReceiveAnimationUpdates() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        animation.deal(f.player, f.inventory);
        when(f.player.isOnline()).thenReturn(false);
        f.pending.removeFirst().action.run();
        verifyNoInteractions(f.gui);
        assertTrue(f.pending.isEmpty());
    }

    @Test
    void otherInventoryDoesNotReceiveAnimationUpdates() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        animation.deal(f.player, f.inventory);
        when(f.view.getTopInventory()).thenReturn(mock(Inventory.class));
        f.pending.removeFirst().action.run();
        verifyNoInteractions(f.gui);
        assertTrue(f.pending.isEmpty());
    }

    @Test
    void normalDealUsesFourStepsAndEndsInPlayerTurn() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        animation.deal(f.player, f.inventory);
        for (int i = 0; i < 4; i++) f.pending.removeFirst().action.run();
        assertTrue(f.pending.isEmpty());
        assertEquals(BlackjackPhase.PLAYER_TURN, f.manager.session(f.id).orElseThrow().phase());
        verify(f.gui).render(f.player, f.inventory, 1, 0, true);
        verify(f.gui).render(f.player, f.inventory, 1, 1, true);
        verify(f.gui).render(f.player, f.inventory, 2, 1, true);
        verify(f.gui).render(f.player, f.inventory, 2, 2, true);
    }

    @Test
    void disableCancelsScheduledWorkAndSettlesOnce() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        animation.deal(f.player, f.inventory);
        Pending queued = f.pending.removeFirst();
        animation.cancelAll();
        f.manager.closeAll();
        queued.action.run();
        verify(queued.task).cancel();
        verifyNoInteractions(f.gui);
        verify(f.economy, times(1)).deposit(f.id, 10);
        assertTrue(f.manager.session(f.id).isEmpty());
    }

    @Test
    void resultResetCallbackCannotRecreateClosedSession() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        f.manager.stand(f.id);
        animation.showResult(f.player, f.inventory);
        Pending reset = f.pending.removeFirst();
        f.manager.closeSession(f.id);
        clearInvocations(f.gui);
        reset.action.run();
        verifyNoInteractions(f.gui);
        assertTrue(f.manager.session(f.id).isEmpty());
        verify(f.economy, times(1)).deposit(f.id, 10);
    }

    @Test
    void dealerRevealKeepsFeedbackHiddenUntilLastStepThenResets() {
        Fixture f = new Fixture();
        BlackjackAnimation animation = f.realAnimation();
        f.manager.start(f.id);
        f.manager.stand(f.id);
        animation.dealerReveal(f.player, f.inventory);
        assertTrue(f.holder.revealingDealer());
        f.pending.removeFirst().action.run();
        assertFalse(f.holder.revealingDealer());
        assertEquals(BlackjackPhase.RESULT, f.manager.session(f.id).orElseThrow().phase());
        f.pending.removeFirst().action.run();
        assertEquals(BlackjackPhase.BETTING, f.manager.session(f.id).orElseThrow().phase());
        assertTrue(f.manager.session(f.id).orElseThrow().playerHand().cards().isEmpty());
        assertTrue(f.pending.isEmpty());
        verify(f.economy, times(1)).deposit(f.id, 10);
    }

    @Test
    void payoutFailureInDealCallbackLeavesVisiblePendingState() {
        Fixture f = new Fixture(BlackjackRank.ACE);
        BlackjackAnimation animation = f.realAnimation();
        doThrow(pl.kurdeslawo.casinocore.economy.EconomyTransactionException.rejected("Rejected deposit")).when(f.economy).deposit(eq(f.id), anyLong());
        f.manager.start(f.id);
        animation.deal(f.player, f.inventory);
        for (int i = 0; i < 4; i++) f.pending.removeFirst().action.run();
        assertEquals(BlackjackPhase.SETTLING, f.manager.session(f.id).orElseThrow().phase());
        verify(f.gui).render(f.player, f.inventory);
        verify(f.logger).severe(contains("Rejected deposit"));
        assertTrue(f.pending.isEmpty());
        verify(f.economy, times(1)).withdraw(f.id, 5);
        assertTrue(f.manager.recent(f.id).isEmpty());
    }

    private record Pending(Runnable action, BukkitTask task) {}

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final EconomyProvider economy = mock(EconomyProvider.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final Logger logger = mock(Logger.class);
        final BlackjackGui gui = mock(BlackjackGui.class);
        final BlackjackAnimation animation = mock(BlackjackAnimation.class);
        final Player player = mock(Player.class);
        final Inventory inventory = mock(Inventory.class);
        final InventoryView view = mock(InventoryView.class);
        final BlackjackGuiHolder holder = new BlackjackGuiHolder(id);
        final Deque<Pending> pending = new ArrayDeque<>();
        final BlackjackManager manager;
        final BlackjackGuiListener listener;

        Fixture() {
            this(BlackjackRank.NINE);
        }

        Fixture(BlackjackRank secondPlayerCard) {
            when(economy.has(eq(id), anyLong())).thenReturn(true);
            when(economy.withdraw(eq(id), anyLong())).thenReturn(true);
            when(plugin.getLogger()).thenReturn(logger);
            YamlConfiguration config = new YamlConfiguration();
            config.set("blackjack.sounds.enabled", false);
            when(plugin.getConfig()).thenReturn(config);
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(player.getOpenInventory()).thenReturn(view);
            when(view.getTopInventory()).thenReturn(inventory);
            when(inventory.getHolder()).thenReturn(holder);
            // Holder is normally attached by BlackjackGui.open().
            manager = new BlackjackManager(economy, List.of(5L), 3, 2, false,
                () -> new BlackjackDeck(List.of(
                    new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.HEARTS),
                    new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.SPADES),
                    new BlackjackCard(secondPlayerCard, BlackjackSuit.HEARTS),
                    new BlackjackCard(BlackjackRank.EIGHT, BlackjackSuit.SPADES))),
                new InMemoryBlackjackHistoryRepository());
            listener = new BlackjackGuiListener(plugin, manager, gui, animation);
            // attach is package-private; use the inventory from the view in listener tests.
            try {
                var method = BlackjackGuiHolder.class.getDeclaredMethod("attach", Inventory.class);
                method.setAccessible(true);
                method.invoke(holder, inventory);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        InventoryClickEvent click(int slot) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getView()).thenReturn(view);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getRawSlot()).thenReturn(slot);
            return event;
        }

        InventoryCloseEvent close() {
            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(inventory);
            return event;
        }

        PlayerQuitEvent quit() {
            PlayerQuitEvent event = mock(PlayerQuitEvent.class);
            when(event.getPlayer()).thenReturn(player);
            return event;
        }

        BlackjackAnimation realAnimation() {
            Server server = mock(Server.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
                BukkitTask task = mock(BukkitTask.class);
                pending.addLast(new Pending(call.getArgument(1), task));
                return task;
            });
            return new BlackjackAnimation(plugin, manager, gui);
        }
    }
}
