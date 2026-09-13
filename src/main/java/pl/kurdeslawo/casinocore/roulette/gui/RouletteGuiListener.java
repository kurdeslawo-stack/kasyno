package pl.kurdeslawo.casinocore.roulette.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.kurdeslawo.casinocore.roulette.RouletteBetType;
import pl.kurdeslawo.casinocore.roulette.RouletteManager;
import pl.kurdeslawo.casinocore.roulette.RoulettePhase;
import pl.kurdeslawo.casinocore.roulette.RouletteSession;

import java.util.List;

public final class RouletteGuiListener implements Listener {
    private final JavaPlugin plugin;
    private final RouletteManager manager;
    private final RouletteGui gui;
    private final RouletteAnimation animation;

    public RouletteGuiListener(JavaPlugin plugin, RouletteManager manager, RouletteGui gui, RouletteAnimation animation) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.animation = animation;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RouletteGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= RouletteGui.SIZE) {
            return;
        }
        if (slot == RouletteGui.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        RouletteSession session = manager.openSession(player.getUniqueId());
        if (session.phase() != RoulettePhase.SELECTING) {
            return;
        }

        int stakeIndex = RouletteGui.stakeIndexAt(slot);
        List<Long> stakes = manager.allowedStakes();
        if (stakeIndex >= 0 && stakeIndex < stakes.size()) {
            manager.selectStake(player.getUniqueId(), stakes.get(stakeIndex));
            gui.render(player, holder.getInventory(), null);
            return;
        }

        Integer exactNumber = RouletteGui.numberAt(slot);
        RouletteBetType type = exactNumber == null ? RouletteGui.outsideBetAt(slot) : RouletteBetType.EXACT_NUMBER;
        if (type == null) {
            return;
        }

        RouletteManager.StartRoundOutcome outcome = manager.startRound(player.getUniqueId(), type, exactNumber);
        if (!outcome.started()) {
            player.sendMessage(outcome.error());
            return;
        }
        gui.render(player, holder.getInventory(), null);
        animation.start(player, holder.getInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RouletteGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RouletteGuiHolder holder)) {
            return;
        }
        animation.cancel(holder.playerId());
        safelyCloseSession(holder.playerId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        animation.cancel(event.getPlayer().getUniqueId());
        safelyCloseSession(event.getPlayer().getUniqueId());
    }

    private void safelyCloseSession(java.util.UUID playerId) {
        try {
            manager.closeSession(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not settle Roulette round for " + playerId + ": " + exception.getMessage());
        }
    }
}
