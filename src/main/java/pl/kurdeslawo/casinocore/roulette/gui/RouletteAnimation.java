package pl.kurdeslawo.casinocore.roulette.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
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
    private final RouletteReopenTracker reopenTracker = new RouletteReopenTracker();
    private long fastDelay;
    private long mediumDelay;
    private long slowDelay;
    private long finalDelay;
    private long finalPulseDelay;
    private long resultDuration;
    private boolean soundsEnabled;
    private float tickVolume;
    private float finalVolume;
    private float winVolume;

    public RouletteAnimation(JavaPlugin plugin, RouletteManager manager, RouletteGui gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        reloadConfiguration();
    }

    public void reloadConfiguration() {
        fastDelay = plugin.getConfig().getLong("roulette.animation.fast-delay-ticks", 2L);
        mediumDelay = plugin.getConfig().getLong("roulette.animation.medium-delay-ticks", 3L);
        slowDelay = plugin.getConfig().getLong("roulette.animation.slow-delay-ticks", 5L);
        finalDelay = plugin.getConfig().getLong("roulette.animation.final-delay-ticks", 8L);
        finalPulseDelay =
                plugin.getConfig().getLong("roulette.animation.final-pulse-delay-ticks", 4L);
        resultDuration =
                plugin.getConfig().getLong("roulette.animation.result-duration-ticks", 50L);
        soundsEnabled = plugin.getConfig().getBoolean("roulette.sounds.enabled", true);
        tickVolume = (float) plugin.getConfig().getDouble("roulette.sounds.tick-volume", 0.32);
        finalVolume = (float) plugin.getConfig().getDouble("roulette.sounds.final-volume", 0.95);
        winVolume = (float) plugin.getConfig().getDouble("roulette.sounds.win-volume", 0.9);
    }

    public void start(Player player, Inventory inventory) {
        cancel(player.getUniqueId());
        RouletteSession session = manager.session(player.getUniqueId()).orElseThrow();
        int finalNumber = session.result().winningNumber();
        int finalStep = 22 + finalNumber;
        scheduleStep(player, inventory, 0, finalStep);
    }

    public boolean finishAfterClose(Player player) {
        UUID playerId = player.getUniqueId();
        RouletteSession session = manager.session(playerId).orElse(null);
        if (session == null || session.phase() != RoulettePhase.SPINNING) {
            return false;
        }
        cancel(playerId);
        if (!reopenTracker.markClosedDuringSpin(session)) {
            return false;
        }
        try {
            if (!manager.settle(playerId)) {
                reopenTracker.cancel(playerId);
                return false;
            }
        } catch (RuntimeException exception) {
            reopenTracker.cancel(playerId);
            plugin.getLogger()
                    .severe(
                            "Could not settle closed Roulette round for "
                                    + playerId
                                    + ": "
                                    + exception.getMessage());
            player.sendActionBar(
                    Component.text(
                            "Nie udało się rozliczyć rundy. Skontaktuj się z administratorem.",
                            NamedTextColor.RED));
            return true;
        }

        BukkitTask task =
                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    tasks.remove(playerId);
                                    if (!reopenTracker.consume(playerId, session)) {
                                        return;
                                    }
                                    boolean canReopen =
                                            plugin.isEnabled()
                                                    && player.isOnline()
                                                    && manager.session(playerId).orElse(null)
                                                            == session
                                                    && session.phase() == RoulettePhase.RESULT
                                                    && player.getOpenInventory()
                                                                    .getTopInventory()
                                                                    .getType()
                                                            == InventoryType.CRAFTING;
                                    if (!canReopen) {
                                        manager.closeSession(playerId);
                                        return;
                                    }
                                    gui.open(player);
                                    Inventory reopened =
                                            player.getOpenInventory().getTopInventory();
                                    if (reopened.getHolder() instanceof RouletteGuiHolder) {
                                        presentResult(player, reopened, session);
                                    }
                                });
        tasks.put(playerId, task);
        return true;
    }

    private void scheduleStep(Player player, Inventory inventory, int step, int finalStep) {
        long delay =
                step < 11
                        ? fastDelay
                        : step < 21 ? mediumDelay : step < 27 ? slowDelay : finalDelay;
        BukkitTask task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    UUID playerId = player.getUniqueId();
                                    RouletteSession session =
                                            manager.session(playerId).orElse(null);
                                    if (session == null
                                            || session.phase() != RoulettePhase.SPINNING
                                            || !isStillViewing(player, inventory)) {
                                        tasks.remove(playerId);
                                        return;
                                    }
                                    int highlighted = step % 11;
                                    gui.highlightNumber(inventory, highlighted);
                                    if (soundsEnabled) {
                                        player.playSound(
                                                player.getLocation(),
                                                Sound.BLOCK_NOTE_BLOCK_HAT,
                                                tickVolume,
                                                Math.max(0.82f, 1.25f - (step * 0.012f)));
                                    }
                                    if (step < finalStep) {
                                        scheduleStep(player, inventory, step + 1, finalStep);
                                        return;
                                    }

                                    scheduleFinalPulse(player, inventory, session, highlighted, 0);
                                },
                                delay);
        tasks.put(player.getUniqueId(), task);
    }

    private void scheduleFinalPulse(
            Player player,
            Inventory inventory,
            RouletteSession session,
            int finalNumber,
            int stage) {
        BukkitTask task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    UUID playerId = player.getUniqueId();
                                    RouletteSession current =
                                            manager.session(playerId).orElse(null);
                                    if (current != session
                                            || current.phase() != RoulettePhase.SPINNING
                                            || !isStillViewing(player, inventory)) {
                                        tasks.remove(playerId);
                                        return;
                                    }
                                    if (stage == 4) {
                                        tasks.remove(playerId);
                                        finish(player, inventory, session);
                                        return;
                                    }
                                    if (stage % 2 == 0) {
                                        gui.highlightNumber(inventory, -1);
                                    } else {
                                        gui.highlightNumber(inventory, finalNumber);
                                        if (soundsEnabled) {
                                            player.playSound(
                                                    player.getLocation(),
                                                    Sound.BLOCK_NOTE_BLOCK_BELL,
                                                    finalVolume * 0.58f,
                                                    1.15f);
                                        }
                                    }
                                    scheduleFinalPulse(
                                            player, inventory, session, finalNumber, stage + 1);
                                },
                                finalPulseDelay);
        tasks.put(player.getUniqueId(), task);
    }

    private void finish(Player player, Inventory inventory, RouletteSession session) {
        try {
            if (manager.settle(player.getUniqueId())) {
                presentResult(player, inventory, session);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger()
                    .severe(
                            "Could not settle Roulette round for "
                                    + player.getUniqueId()
                                    + ": "
                                    + exception.getMessage());
            gui.render(player, inventory, null);
            player.sendActionBar(
                    Component.text(
                            "Nie udało się rozliczyć rundy. Skontaktuj się z administratorem.",
                            NamedTextColor.RED));
        }
    }

    private void presentResult(Player player, Inventory inventory, RouletteSession session) {
        gui.render(player, inventory, session.result().winningNumber());
        if (soundsEnabled) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, finalVolume, 0.95f);
        }
        boolean won = session.payout() > 0;
        if (won && soundsEnabled) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, winVolume, 1.15f);
        }
        BukkitTask task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    tasks.remove(player.getUniqueId());
                                    if (manager.session(player.getUniqueId()).orElse(null)
                                                    == session
                                            && isStillViewing(player, inventory)
                                            && manager.resetForNextRound(player.getUniqueId())) {
                                        gui.render(player, inventory, null);
                                    }
                                },
                                resultDuration);
        tasks.put(player.getUniqueId(), task);
    }

    public void cancel(UUID playerId) {
        reopenTracker.cancel(playerId);
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
        reopenTracker.clear();
    }

    private static boolean isStillViewing(Player player, Inventory inventory) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory);
    }
}
