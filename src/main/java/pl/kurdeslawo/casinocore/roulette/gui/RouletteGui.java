package pl.kurdeslawo.casinocore.roulette.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.Money;
import pl.kurdeslawo.casinocore.gui.ConfiguredIcon;
import pl.kurdeslawo.casinocore.roulette.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RouletteGui {
    public static final int SIZE = 54;
    public static final int CLOSE_SLOT = 53;
    public static final int STATUS_SLOT = 26;
    public static final int HISTORY_SLOT = 17;
    public static final int BALANCE_SLOT = 8;
    // Clockwise ring matching the furniture GUI. Both green slots select the same zero.
    private static final int[] NUMBER_SLOTS = {11, 12, 13, 14, 15, 24, 33, 32, 31, 30, 29};
    private static final int ZERO_MIRROR_SLOT = 20;
    private static final int[] STAKE_SLOTS = {9, 18, 27};
    private static final Map<Integer, RouletteBetType> OUTSIDE_BETS =
            Map.of(
                    47,
                    RouletteBetType.RED,
                    48,
                    RouletteBetType.BLACK,
                    49,
                    RouletteBetType.LOW,
                    50,
                    RouletteBetType.HIGH);
    private final JavaPlugin plugin;
    private final RouletteManager manager;
    private final EconomyProvider economy;

    public RouletteGui(JavaPlugin plugin, RouletteManager manager, EconomyProvider economy) {
        this.plugin = plugin;
        this.manager = manager;
        this.economy = economy;
    }

    public void open(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder()
                        instanceof RouletteGuiHolder holder
                && holder.playerId().equals(player.getUniqueId())) {
            RouletteSession session = manager.openSession(player.getUniqueId());
            // Do not redraw over the animation when a furniture command is spammed.
            if (session.phase() != RoulettePhase.SPINNING)
                render(player, holder.getInventory(), null);
            return;
        }
        RouletteGuiHolder holder = new RouletteGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("title"));
        holder.attach(inventory);
        manager.openSession(player.getUniqueId());
        render(player, inventory, null);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, Integer highlighted) {
        RouletteSession session = manager.openSession(player.getUniqueId());
        if (inventory.getHolder() instanceof RouletteGuiHolder holder)
            holder.showStakes(manager.allowedStakes());
        ItemStack background =
                icon(
                        "background",
                        "normal",
                        Material.GRAY_STAINED_GLASS_PANE,
                        Component.empty(),
                        List.of());
        var meta = background.getItemMeta();
        meta.setHideTooltip(true);
        background.setItemMeta(meta);
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, background);
        if (highlighted == null && session.phase() == RoulettePhase.RESULT)
            highlighted = session.result().winningNumber();
        renderNumbers(inventory, highlighted, session);
        OUTSIDE_BETS.forEach(
                (slot, type) -> {
                    boolean chosen = selected(session, type, null);
                    inventory.setItem(
                            slot,
                            icon(
                                    "outside." + type.name().toLowerCase(java.util.Locale.ROOT),
                                    chosen ? "selected" : "normal",
                                    outsideMaterial(type),
                                    text("bets." + type.name().toLowerCase(java.util.Locale.ROOT)),
                                    betLore(session, type, null, chosen)));
                });
        for (int i = 0; i < manager.allowedStakes().size(); i++) {
            long stake = manager.allowedStakes().get(i);
            List<Component> lore = new ArrayList<>();
            if (session.phase() == RoulettePhase.SPINNING) lore.add(text("wait"));
            else if (session.phase() == RoulettePhase.RESULT) lore.add(text("result-wait"));
            else if (session.selectedBet() == null) lore.add(text("choose-bet"));
            else {
                lore.add(text("selected-bet", "bet", betLabel(session.selectedBet())));
                lore.add(text("stake-start"));
            }
            inventory.setItem(
                    STAKE_SLOTS[i],
                    icon(
                            "stakes." + i,
                            "normal",
                            Material.GOLD_NUGGET,
                            text("stake", "amount", Money.format(stake)),
                            lore));
        }
        List<Component> history = new ArrayList<>();
        for (var round : manager.recentRounds(player.getUniqueId()))
            history.add(
                    text(
                            round.payout() == 0
                                    ? "history-loss"
                                    : round.payout() > round.stake()
                                            ? "history-win"
                                            : round.payout() < round.stake()
                                                    ? "history-partial-loss"
                                                    : "history-push",
                            "stake",
                            Money.format(round.stake()),
                            "payout",
                            Money.format(round.payout()),
                            "net",
                            (round.payout() > round.stake() ? "+" : "")
                                    + Money.format(round.payout() - round.stake())));
        if (history.isEmpty()) history.add(text("no-history"));
        inventory.setItem(
                HISTORY_SLOT, icon("history", "normal", Material.BOOK, text("history"), history));
        Component balance;
        try {
            balance =
                    text(
                            "balance",
                            "amount",
                            Money.format(economy.getBalance(player.getUniqueId())));
        } catch (RuntimeException e) {
            balance = text("balance-error");
            plugin.getLogger().severe("Saldo ruletki: " + e.getMessage());
        }
        inventory.setItem(
                BALANCE_SLOT, icon("balance", "normal", Material.SUNFLOWER, balance, List.of()));
        inventory.setItem(
                CLOSE_SLOT, icon("close", "normal", Material.BARRIER, text("close"), List.of()));
        renderStatus(inventory, session);
    }

    private void renderStatus(Inventory inventory, RouletteSession session) {
        if (manager.needsReview(session.playerId())) {
            inventory.setItem(
                    STATUS_SLOT,
                    icon(
                            "status",
                            "error",
                            Material.BARRIER,
                            text("review"),
                            List.of(text("contact-admin"))));
        } else if (session.phase() == RoulettePhase.SPINNING) {
            inventory.setItem(
                    STATUS_SLOT,
                    icon(
                            "status",
                            "spinning",
                            Material.CLOCK,
                            text("spinning"),
                            List.of(text("wait"))));
        } else if (session.phase() == RoulettePhase.RESULT) {
            boolean win = session.payout() > 0;
            inventory.setItem(
                    STATUS_SLOT,
                    icon(
                            "status",
                            win ? "win" : "loss",
                            win ? Material.EMERALD : Material.REDSTONE,
                            text(win ? "win" : "loss"),
                            List.of(
                                    text("result", "number", coloredNumber(session.result())),
                                    text(
                                            win ? "won" : "lost",
                                            "amount",
                                            Money.format(
                                                    win
                                                            ? session.payout()
                                                            : session.resultStake())))));
        } else {
            inventory.setItem(
                    STATUS_SLOT,
                    icon(
                            "status",
                            "normal",
                            Material.PAPER,
                            text("ready"),
                            List.of(text("select-help"))));
        }
    }

    public void highlightNumber(Inventory inventory, int highlighted) {
        if (inventory.getHolder() instanceof RouletteGuiHolder holder)
            manager.session(holder.playerId())
                    .ifPresent(session -> renderNumbers(inventory, highlighted, session));
    }

    private void renderNumbers(Inventory inventory, Integer highlighted, RouletteSession session) {
        for (int n = 0; n <= 10; n++) {
            boolean chosen = selected(session, RouletteBetType.EXACT_NUMBER, n);
            boolean ball = highlighted != null && highlighted == n;
            String state =
                    ball ? (chosen ? "selected-ball" : "ball") : (chosen ? "selected" : "normal");
            Material fallback =
                    ball
                            ? (chosen ? Material.SEA_LANTERN : Material.GLOWSTONE)
                            : n == 0
                                    ? Material.LIME_WOOL
                                    : n % 2 == 1 ? Material.RED_WOOL : Material.BLACK_WOOL;
            var lore = betLore(session, RouletteBetType.EXACT_NUMBER, n, chosen);
            if (ball) lore.addFirst(text("ball"));
            ItemStack stack =
                    icon(
                            "numbers." + n,
                            state,
                            fallback,
                            text("number", "number", Integer.toString(n)),
                            lore);
            if (n > 1 && stack.getItemMeta().getCustomModelDataComponent().getFloats().isEmpty())
                stack.setAmount(n);
            inventory.setItem(NUMBER_SLOTS[n], stack);
            if (n == 0) inventory.setItem(ZERO_MIRROR_SLOT, stack.clone());
        }
    }

    private List<Component> betLore(
            RouletteSession session, RouletteBetType type, Integer number, boolean chosen) {
        List<Component> lines = new ArrayList<>();
        lines.add(text("condition." + type.name().toLowerCase(java.util.Locale.ROOT)));
        lines.add(
                text(
                        "chance",
                        "chance",
                        String.format(
                                java.util.Locale.forLanguageTag("pl-PL"),
                                "%.2f",
                                100 * manager.winProbability(type, number))));
        lines.add(Component.empty());
        lines.add(
                text(
                        "payout",
                        "multiplier",
                        (type == RouletteBetType.EXACT_NUMBER
                                        ? manager.exactMultiplier()
                                        : manager.outsideMultiplier())
                                .toPlainString()
                                .replace('.', ',')));
        lines.add(Component.empty());
        lines.add(text(chosen ? "selected" : "select"));
        return lines;
    }

    private static boolean selected(RouletteSession s, RouletteBetType type, Integer number) {
        var bet = s.selectedBet();
        return bet != null
                && bet.type() == type
                && java.util.Objects.equals(bet.selectedNumber(), number);
    }

    private String betLabel(RouletteBet bet) {
        if (bet.type() == RouletteBetType.EXACT_NUMBER)
            return LegacyComponentSerializer.legacySection()
                    .serialize(text("exact-bet", "number", Integer.toString(bet.selectedNumber())));
        return LegacyComponentSerializer.legacySection()
                .serialize(text("bets." + bet.type().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private ItemStack icon(
            String key, String state, Material fallback, Component name, List<Component> lore) {
        var stack = ConfiguredIcon.create(plugin.getConfig(), key, state, fallback);
        var meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String key, String... replacements) {
        String value =
                java.util.Objects.requireNonNullElse(
                        plugin.getConfig().getString("roulette.text." + key), "");
        for (int i = 0; i < replacements.length; i += 2)
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        // Config labels may contain section colors; accept both conventional formats.
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(value.replace('§', '&'))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Material outsideMaterial(RouletteBetType t) {
        return switch (t) {
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case LOW -> Material.DEAD_BUSH;
            case HIGH -> Material.WHEAT;
            default -> throw new IllegalArgumentException();
        };
    }

    public static String coloredNumber(RouletteResult r) {
        return (r.color() == RouletteColor.GREEN
                        ? "&a"
                        : r.color() == RouletteColor.RED ? "&c" : "&8")
                + r.winningNumber();
    }

    public static NamedTextColor resultColor(RouletteColor c) {
        return switch (c) {
            case RED -> NamedTextColor.RED;
            case BLACK -> NamedTextColor.DARK_GRAY;
            case GREEN -> NamedTextColor.GREEN;
        };
    }

    public static Integer numberAt(int slot) {
        if (slot == ZERO_MIRROR_SLOT) return 0;
        for (int n = 0; n < NUMBER_SLOTS.length; n++) if (NUMBER_SLOTS[n] == slot) return n;
        return null;
    }

    public static RouletteBetType outsideBetAt(int slot) {
        return OUTSIDE_BETS.get(slot);
    }

    public static int stakeIndexAt(int slot) {
        for (int i = 0; i < STAKE_SLOTS.length; i++) if (STAKE_SLOTS[i] == slot) return i;
        return -1;
    }
}
