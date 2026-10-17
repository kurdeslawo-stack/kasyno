package pl.kurdeslawo.casinocore.blackjack.gui;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.kurdeslawo.casinocore.blackjack.BlackjackManager;
import pl.kurdeslawo.casinocore.blackjack.BlackjackPhase;
import pl.kurdeslawo.casinocore.blackjack.BlackjackSession;

public final class BlackjackGuiListener implements Listener {
    private final JavaPlugin plugin;
    private final BlackjackManager manager;
    private final BlackjackGui gui;
    private final BlackjackAnimation animation;

    public BlackjackGuiListener(
        JavaPlugin plugin,
        BlackjackManager manager,
        BlackjackGui gui,
        BlackjackAnimation animation
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.animation = animation;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BlackjackGuiHolder holder)) return;
        event.setCancelled(true);
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (!(event.getWhoClicked() instanceof Player player)
            || !holder.playerId().equals(player.getUniqueId())) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= BlackjackGui.SIZE) return;
        if (slot == BlackjackGui.CLOSE) {
            player.closeInventory();
            return;
        }

        BlackjackSession session = manager.openSession(player.getUniqueId());
        try {
            if (manager.needsReview(player.getUniqueId())) {
                gui.render(player, holder.getInventory());
                return;
            }
            if (session.phase() == BlackjackPhase.SETTLING && slot == BlackjackGui.STATUS) {
                manager.stand(player.getUniqueId());
                animation.showResult(player, holder.getInventory());
            } else if (session.phase() == BlackjackPhase.BETTING) {
                handleBettingClick(player, holder, slot);
            } else if (session.phase() == BlackjackPhase.PLAYER_TURN) {
                handlePlayerTurnClick(player, holder, session, slot);
            }
        } catch (RuntimeException exception) {
            gui.render(player, holder.getInventory());
            plugin.getLogger().severe("Blackjack action failed for " + player.getUniqueId()
                + ": " + exception.getMessage());
            player.sendActionBar(Component.text("Nie udało się wykonać akcji. Sprawdź status w menu.", NamedTextColor.RED));
        }
    }

    private void handleBettingClick(Player player, BlackjackGuiHolder holder, int slot) {
        int stakeIndex = BlackjackGui.stakeAt(slot);
        if (stakeIndex >= 0 && stakeIndex < manager.stakes().size()) {
            manager.selectStake(player.getUniqueId(), manager.stakes().get(stakeIndex));
            gui.render(player, holder.getInventory());
            return;
        }
        if (slot != BlackjackGui.STATUS) return;

        BlackjackManager.StartOutcome outcome = manager.start(player.getUniqueId());
        if (!outcome.started()) {
            String message = manager.stakes().contains(manager.openSession(player.getUniqueId()).selectedStake())
                ? "Nie masz wystarczającej liczby coinów." : "Wybierz dostępną stawkę.";
            player.sendActionBar(Component.text(message, NamedTextColor.RED));
            return;
        }
        gui.render(player, holder.getInventory(), 0, 0, true);
        animation.deal(player, holder.getInventory());
    }

    private void handlePlayerTurnClick(
        Player player,
        BlackjackGuiHolder holder,
        BlackjackSession session,
        int slot
    ) {
        UUID playerId = player.getUniqueId();
        if (slot == BlackjackGui.HIT) {
            manager.hit(playerId);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.1f);
            if (session.phase() == BlackjackPhase.RESULT) {
                if (session.playerHand().isBust()) animation.showResult(player, holder.getInventory());
                else animation.dealerReveal(player, holder.getInventory());
            } else {
                gui.render(player, holder.getInventory());
            }
        } else if (slot == BlackjackGui.STAND) {
            manager.stand(playerId);
            animation.dealerReveal(player, holder.getInventory());
        } else if (slot == BlackjackGui.DOUBLE) {
            if (!manager.doubleDown(playerId)) {
                player.sendActionBar(Component.text("Nie możesz teraz podwoić stawki.", NamedTextColor.RED));
            } else {
                animation.dealerReveal(player, holder.getInventory());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BlackjackGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlackjackGuiHolder holder)) return;
        animation.cancel(holder.playerId());
        safelyClose(holder.playerId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        animation.cancel(playerId);
        safelyClose(playerId);
    }

    private void safelyClose(UUID playerId) {
        try {
            manager.closeSession(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not settle Blackjack round for " + playerId
                + ": " + exception.getMessage());
        }
    }
}
