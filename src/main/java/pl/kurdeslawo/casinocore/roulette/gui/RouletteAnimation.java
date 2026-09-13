package pl.kurdeslawo.casinocore.roulette.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.kurdeslawo.casinocore.roulette.RouletteManager;
import pl.kurdeslawo.casinocore.roulette.RoulettePhase;
import pl.kurdeslawo.casinocore.roulette.RouletteSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouletteAnimation {
    private final JavaPlugin plugin;
    private final RouletteManager manager;
    private final RouletteGui gui;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public RouletteAnimation(JavaPlugin plugin, RouletteManager manager, RouletteGui gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    public void start(Player player, Inventory inventory) {
        cancel(player.getUniqueId());
        RouletteSession session = manager.session(player.getUniqueId()).orElseThrow();
        int finalNumber = session.result().winningNumber();
        int finalStep = 26 + finalNumber;
        scheduleStep(player, inventory, 0, finalStep);
    }

    private void scheduleStep(Player player, Inventory inventory, int step, int finalStep) {
        long delay = step < 18 ? 2L : step < 25 ? 3L : step < 29 ? 5L : 7L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            UUID playerId = player.getUniqueId();
            RouletteSession session = manager.session(playerId).orElse(null);
            if (session == null || session.phase() != RoulettePhase.SPINNING || !isStillViewing(player, inventory)) {
                tasks.remove(playerId);
                return;
            }
            int highlighted = step % 13;
            gui.highlightNumber(inventory, highlighted);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.15f + (step * 0.01f));
            if (step < finalStep) {
                scheduleStep(player, inventory, step + 1, finalStep);
                return;
            }

            tasks.remove(playerId);
            finish(player, inventory, session);
        }, delay);
        tasks.put(player.getUniqueId(), task);
    }

    private void finish(Player player, Inventory inventory, RouletteSession session) {
        try {
            manager.settle(player.getUniqueId());
            gui.render(player, inventory, session.result().winningNumber());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.0f);
            if (session.payout() > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.15f);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isStillViewing(player, inventory) && manager.resetForNextRound(player.getUniqueId())) {
                    gui.render(player, inventory, null);
                }
            }, 40L);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not settle Roulette round for " + player.getUniqueId() + ": " + exception.getMessage());
            player.sendMessage("Casino payout failed. The round remains pending; contact an administrator.");
        }
    }

    public void cancel(UUID playerId) {
        BukkitTask task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    private static boolean isStillViewing(Player player, Inventory inventory) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory);
    }
}
