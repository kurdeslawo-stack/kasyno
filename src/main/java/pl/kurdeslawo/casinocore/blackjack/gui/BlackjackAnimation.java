package pl.kurdeslawo.casinocore.blackjack.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.kurdeslawo.casinocore.blackjack.BlackjackManager;
import pl.kurdeslawo.casinocore.blackjack.BlackjackOutcome;
import pl.kurdeslawo.casinocore.blackjack.BlackjackPhase;
import pl.kurdeslawo.casinocore.blackjack.BlackjackSession;

public final class BlackjackAnimation {
    private final JavaPlugin plugin;
    private final BlackjackManager manager;
    private final BlackjackGui gui;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public BlackjackAnimation(JavaPlugin plugin, BlackjackManager manager, BlackjackGui gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    public void deal(Player player, Inventory inventory) {
        cancel(player.getUniqueId());
        dealStep(player, inventory, 0);
    }

    private void dealStep(Player player, Inventory inventory, int step) {
        long delay = plugin.getConfig().getLong("blackjack.animation.deal-delay-ticks", 5);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            BlackjackSession session = manager.session(player.getUniqueId()).orElse(null);
            if (session == null || session.phase() != BlackjackPhase.DEALING || !viewing(player, inventory)) {
                tasks.remove(player.getUniqueId());
                return;
            }

            int playerVisible = switch (step) {
                case 0, 1 -> 1;
                default -> 2;
            };
            int dealerVisible = switch (step) {
                case 0 -> 0;
                case 1, 2 -> 1;
                default -> 2;
            };
            gui.render(player, inventory, playerVisible, dealerVisible, true);
            sound(player, () -> Sound.ITEM_BOOK_PAGE_TURN, 0.45f, 1.15f);
            if (step < 3) {
                dealStep(player, inventory, step + 1);
                return;
            }

            tasks.remove(player.getUniqueId());
            try {
                manager.finishDeal(player.getUniqueId());
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Blackjack settlement failed for " + player.getUniqueId()
                    + ": " + exception.getMessage());
                gui.render(player, inventory);
                return;
            }
            if (session.phase() == BlackjackPhase.RESULT) showResult(player, inventory);
            else gui.render(player, inventory);
        }, Math.max(1, delay));
        tasks.put(player.getUniqueId(), task);
    }

    public void dealerReveal(Player player, Inventory inventory) {
        cancel(player.getUniqueId());
        BlackjackSession session = manager.session(player.getUniqueId()).orElse(null);
        if (session == null || session.phase() != BlackjackPhase.RESULT) return;
        if (inventory.getHolder() instanceof BlackjackGuiHolder holder) holder.setRevealingDealer(true);
        gui.render(player, inventory, session.playerHand().cards().size(), 2, true);
        revealStep(player, inventory, session, 2);
    }

    private void revealStep(Player player, Inventory inventory, BlackjackSession session, int visibleCards) {
        long delay = plugin.getConfig().getLong("blackjack.animation.dealer-delay-ticks", 6);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (manager.session(player.getUniqueId()).orElse(null) != session || !viewing(player, inventory)) {
                tasks.remove(player.getUniqueId());
                return;
            }

            gui.render(player, inventory, session.playerHand().cards().size(),
                Math.min(visibleCards, session.dealerHand().cards().size()), false);
            sound(player, () -> Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.0f);
            if (visibleCards < session.dealerHand().cards().size()) {
                revealStep(player, inventory, session, visibleCards + 1);
                return;
            }

            tasks.remove(player.getUniqueId());
            if (inventory.getHolder() instanceof BlackjackGuiHolder holder) holder.setRevealingDealer(false);
            gui.render(player, inventory);
            sound(player, () -> Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f,
                session.outcome() == BlackjackOutcome.LOSS ? 0.8f : 1.2f);
            scheduleReset(player, inventory, session);
        }, Math.max(1, delay));
        tasks.put(player.getUniqueId(), task);
    }

    public void showResult(Player player, Inventory inventory) {
        BlackjackSession session = manager.session(player.getUniqueId()).orElse(null);
        if (session == null || session.phase() != BlackjackPhase.RESULT) return;
        gui.render(player, inventory);
        sound(player, () -> Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.1f);
        scheduleReset(player, inventory, session);
    }

    private void scheduleReset(Player player, Inventory inventory, BlackjackSession session) {
        long delay = plugin.getConfig().getLong("blackjack.animation.result-duration-ticks", 50);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(player.getUniqueId());
            if (manager.session(player.getUniqueId()).orElse(null) == session
                && viewing(player, inventory)
                && manager.reset(player.getUniqueId())) {
                gui.render(player, inventory);
            }
        }, Math.max(1, delay));
        tasks.put(player.getUniqueId(), task);
    }

    private void sound(Player player, Supplier<Sound> sound, float volume, float pitch) {
        if (plugin.getConfig().getBoolean("blackjack.sounds.enabled", true)) {
            player.playSound(player.getLocation(), sound.get(), volume, pitch);
        }
    }

    public void cancel(UUID playerId) {
        BukkitTask task = tasks.remove(playerId);
        if (task != null) task.cancel();
    }

    public void cancelAll() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }

    private static boolean viewing(Player player, Inventory inventory) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory);
    }
}
