package pl.kurdeslawo.casinocore.roulette.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import pl.kurdeslawo.casinocore.gui.CasinoGuiIcons;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.roulette.RouletteBet;
import pl.kurdeslawo.casinocore.roulette.RouletteBetType;
import pl.kurdeslawo.casinocore.roulette.RouletteColor;
import pl.kurdeslawo.casinocore.roulette.RouletteEngine;
import pl.kurdeslawo.casinocore.roulette.RouletteManager;
import pl.kurdeslawo.casinocore.roulette.RoulettePhase;
import pl.kurdeslawo.casinocore.roulette.RouletteResult;
import pl.kurdeslawo.casinocore.roulette.RouletteRoundRecord;
import pl.kurdeslawo.casinocore.roulette.RouletteSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RouletteGui {
    public static final int SIZE = 54;
    public static final int SPIN_SLOT = 40;
    public static final int CLOSE_SLOT = 53;
    private static final int BALANCE_SLOT = 50;
    private static final int HISTORY_SLOT = 7;
    private static final int[] NUMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
    private static final int[] STAKE_SLOTS = {46, 47, 48};
    private static final Map<Integer, RouletteBetType> OUTSIDE_BETS = Map.of(
        28, RouletteBetType.RED, 29, RouletteBetType.BLACK,
        30, RouletteBetType.EVEN, 31, RouletteBetType.ODD,
        32, RouletteBetType.LOW, 33, RouletteBetType.HIGH
    );

    private final JavaPlugin plugin;
    private final RouletteManager manager;
    private final EconomyProvider economy;

    public RouletteGui(JavaPlugin plugin, RouletteManager manager, EconomyProvider economy) {
        this.plugin = plugin;
        this.manager = manager;
        this.economy = economy;
    }

    public void open(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof RouletteGuiHolder holder
            && holder.playerId().equals(player.getUniqueId())) {
            render(player, holder.getInventory(), null);
            return;
        }
        RouletteGuiHolder holder = new RouletteGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
            plain(Component.text("CasinoCore • Ruletka", NamedTextColor.GOLD, TextDecoration.BOLD)));
        holder.attach(inventory);
        manager.openSession(player.getUniqueId());
        render(player, inventory, null);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, Integer highlightedNumber) {
        RouletteSession session = manager.openSession(player.getUniqueId());
        ItemStack filler = CasinoGuiIcons.apply(item(Material.GREEN_STAINED_GLASS_PANE, Component.text(" "), false), "background_felt");
        ItemStack rail = CasinoGuiIcons.apply(item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), false), "background_rail");
        ItemStack border = CasinoGuiIcons.apply(item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), false), "background_border");
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, slot < 9 || slot >= 45 ? rail : slot % 9 == 0 || slot % 9 == 8 ? border : filler);
        }

        renderNumbers(inventory, highlightedNumber, session);
        renderPlayerHistory(inventory, player);
        inventory.setItem(28, outsideBetItem(session, RouletteBetType.RED, Material.REDSTONE,
            "Czerwone", NamedTextColor.RED, "czerwony numer."));
        inventory.setItem(29, outsideBetItem(session, RouletteBetType.BLACK, Material.COAL,
            "Czarne", NamedTextColor.DARK_GRAY, "czarny numer."));
        inventory.setItem(30, outsideBetItem(session, RouletteBetType.EVEN, Material.QUARTZ,
            "Parzyste", NamedTextColor.WHITE, "liczba parzysta."));
        inventory.setItem(31, outsideBetItem(session, RouletteBetType.ODD, Material.AMETHYST_SHARD,
            "Nieparzyste", NamedTextColor.LIGHT_PURPLE, "liczba nieparzysta."));
        inventory.setItem(32, outsideBetItem(session, RouletteBetType.LOW, Material.COPPER_INGOT,
            "Niskie • 1-6", NamedTextColor.GOLD, "liczba od 1 do 6."));
        inventory.setItem(33, outsideBetItem(session, RouletteBetType.HIGH, Material.DIAMOND,
            "Wysokie • 7-12", NamedTextColor.AQUA, "liczba od 7 do 12."));

        List<Long> stakes = manager.allowedStakes();
        Material[] stakeIcons = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int index = 0; index < Math.min(STAKE_SLOTS.length, stakes.size()); index++) {
            long stake = stakes.get(index);
            boolean selected = stake == session.selectedStake();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Wartość zakładu: ", NamedTextColor.GRAY)
                .append(Component.text(stake + " " + coinWord(stake), NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            lore.add(Component.text(selected ? "✓ Aktualna stawka" : "Kliknij, aby ustawić", NamedTextColor.GREEN));
            ItemStack stakeItem = item(stakeIcons[index],
                Component.text((selected ? "✓ " : "") + stake + " " + coinWord(stake),
                    selected ? NamedTextColor.GREEN : NamedTextColor.GOLD, TextDecoration.BOLD), selected, lore);
            CasinoGuiIcons.stake(stakeItem, stake, selected);
            inventory.setItem(STAKE_SLOTS[index], stakeItem);
        }

        inventory.setItem(BALANCE_SLOT, item(Material.SUNFLOWER,
            balanceName(player), false,
            Component.text("Aktualna stawka: ", NamedTextColor.GRAY)
                .append(Component.text(session.selectedStake(), NamedTextColor.GOLD))));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
            Component.text("Zamknij", NamedTextColor.RED), false));
        inventory.setItem(SPIN_SLOT, manager.needsReview(player.getUniqueId()) ? item(Material.BARRIER,
            Component.text("WYMAGANA WERYFIKACJA", NamedTextColor.RED, TextDecoration.BOLD), false,
            Component.text("Skontaktuj się z administracją", NamedTextColor.YELLOW)) : spinItem(session));
    }

    public void highlightNumber(Inventory inventory, int highlightedNumber) {
        if (!(inventory.getHolder() instanceof RouletteGuiHolder holder)) {
            return;
        }
        RouletteSession session = manager.session(holder.playerId()).orElse(null);
        if (session != null) {
            renderNumbers(inventory, highlightedNumber, session);
        }
    }

    private void renderNumbers(Inventory inventory, Integer highlightedNumber, RouletteSession session) {
        for (int number = 0; number <= 12; number++) {
            RouletteColor color = RouletteEngine.colorOf(number);
            Material material = color == RouletteColor.GREEN ? Material.SLIME_BALL
                : color == RouletteColor.RED ? Material.RED_DYE : Material.INK_SAC;
            boolean selected = isSelected(session, RouletteBetType.EXACT_NUMBER, number);
            boolean highlighted = highlightedNumber != null && highlightedNumber == number;
            Component name = Component.text(highlighted ? "▶ Numer " + number + " ◀" : "Numer " + number,
                highlighted ? NamedTextColor.GOLD : resultColor(color), TextDecoration.BOLD);
            List<Component> lore = betLore(
                List.of("Wygrywasz, jeśli wypadnie", "dokładnie ten numer."),
                manager.exactMultiplier(), selected);
            lore.add(2, chanceLine(manager.winProbability(RouletteBetType.EXACT_NUMBER, number)));
            if (highlighted) {
                lore.add(0, Component.text("● Koło wskazuje ten numer", NamedTextColor.YELLOW));
            }
            ItemStack numberItem = item(material, name, selected || highlighted, lore);
            applyIcon(numberItem, "number_" + number + (highlighted ? "_active" : selected ? "_selected" : ""));
            inventory.setItem(NUMBER_SLOTS[number], numberItem);
        }
    }

    private ItemStack outsideBetItem(RouletteSession session, RouletteBetType type, Material material,
                                     String label, NamedTextColor color, String condition) {
        boolean selected = isSelected(session, type, null);
        List<Component> lore = betLore(List.of("Wygrywasz, jeśli wypadnie", condition), manager.outsideMultiplier(), selected);
        lore.add(2, chanceLine(manager.winProbability(type, null)));
        ItemStack stack = item(material, Component.text(label, color, TextDecoration.BOLD), selected, lore);
        applyIcon(stack, type.name().toLowerCase(java.util.Locale.ROOT) + (selected ? "_selected" : ""));
        return stack;
    }

    private ItemStack spinItem(RouletteSession session) {
        if (session.phase() == RoulettePhase.SPINNING) {
            return item(Material.ENDER_EYE,
                Component.text("KOŁO SIĘ KRĘCI...", NamedTextColor.YELLOW, TextDecoration.BOLD), true,
                Component.text("Poczekaj na wynik", NamedTextColor.GRAY));
        }
        if (session.phase() == RoulettePhase.RESULT) {
            boolean won = session.payout() > 0;
            return item(won ? Material.EMERALD : Material.COAL,
                Component.text(won ? "WYGRANA!" : "PRZEGRANA",
                    won ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD), true,
                Component.text("Wynik: ", NamedTextColor.GRAY)
                    .append(Component.text(session.result().winningNumber(),
                        resultColor(session.result().color()), TextDecoration.BOLD)),
                Component.text(won ? "Wygrana: " : "Stracono: ", NamedTextColor.GRAY)
                    .append(Component.text((won ? session.payout() : session.resultStake()) + " coinów",
                        NamedTextColor.YELLOW)));
        }
        RouletteBet bet = session.selectedBet();
        if (bet == null) {
            return item(Material.CLOCK, Component.text("ZAKRĘĆ", NamedTextColor.GOLD, TextDecoration.BOLD), false,
                Component.text("Najpierw wybierz zakład.", NamedTextColor.RED));
        }
        return item(Material.CLOCK, Component.text("ZAKRĘĆ", NamedTextColor.GOLD, TextDecoration.BOLD), true,
            Component.text("Zakład: ", NamedTextColor.GRAY).append(Component.text(betLabel(bet), NamedTextColor.WHITE)),
            Component.text("Stawka: ", NamedTextColor.GRAY).append(Component.text(bet.stake(), NamedTextColor.YELLOW)),
            Component.empty(), Component.text("Kliknij, aby rozpocząć", NamedTextColor.GREEN));
    }

    private void renderPlayerHistory(Inventory inventory, Player player) {
        List<RouletteRoundRecord> rounds = manager.recentRounds(player.getUniqueId());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Ostatnie 5 rund • od najnowszej", NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (rounds.isEmpty()) {
            lore.add(Component.text("Nie rozegrano jeszcze żadnej rundy.", NamedTextColor.GRAY));
        }
        for (int index = 0; index < Math.min(5, rounds.size()); index++) {
            RouletteRoundRecord round = rounds.get(index);
            long net = round.netChange();
            NamedTextColor color = net > 0 ? NamedTextColor.GREEN : net < 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
            lore.add(Component.text((index + 1) + ". Numer ", NamedTextColor.GRAY)
                .append(Component.text(round.result().winningNumber(), resultColor(round.result().color())))
                .append(Component.text(" • " + signed(net) + " coinów", color, TextDecoration.BOLD)));
        }
        inventory.setItem(HISTORY_SLOT, item(Material.WRITABLE_BOOK,
            Component.text("HISTORIA", NamedTextColor.GOLD, TextDecoration.BOLD), false, lore));
    }

    private static List<Component> betLore(List<String> description, int multiplier, boolean selected) {
        List<Component> lore = new ArrayList<>();
        description.forEach(line -> lore.add(Component.text(line, NamedTextColor.GRAY)));
        lore.add(Component.empty());
        lore.add(Component.text("Wypłata: ", NamedTextColor.WHITE)
            .append(Component.text("x" + multiplier, NamedTextColor.YELLOW)));
        lore.add(Component.empty());
        lore.add(Component.text(selected ? "✓ Wybrano" : "Kliknij, aby wybrać", NamedTextColor.GREEN));
        return lore;
    }

    private static Component chanceLine(double probability) {
        return Component.text("Szansa: ", NamedTextColor.GRAY).append(Component.text(
            String.format(java.util.Locale.forLanguageTag("pl-PL"), "%.2f%%", 100 * probability), NamedTextColor.WHITE));
    }

    private static boolean isSelected(RouletteSession session, RouletteBetType type, Integer number) {
        RouletteBet bet = session.selectedBet();
        return bet != null && bet.type() == type && java.util.Objects.equals(bet.selectedNumber(), number);
    }

    private static String betLabel(RouletteBet bet) {
        return betLabel(bet.type(), bet.selectedNumber());
    }

    private static String betLabel(RouletteBetType type, Integer selectedNumber) {
        return switch (type) {
            case EXACT_NUMBER -> "Numer " + selectedNumber;
            case RED -> "Czerwone";
            case BLACK -> "Czarne";
            case EVEN -> "Parzyste";
            case ODD -> "Nieparzyste";
            case LOW -> "Niskie 1-6";
            case HIGH -> "Wysokie 7-12";
        };
    }

    public static NamedTextColor resultColor(RouletteColor color) {
        return switch (color) {
            case GREEN -> NamedTextColor.GREEN;
            case RED -> NamedTextColor.RED;
            case BLACK -> NamedTextColor.GRAY;
        };
    }

    public static Integer numberAt(int slot) {
        for (int number = 0; number < NUMBER_SLOTS.length; number++) {
            if (NUMBER_SLOTS[number] == slot) return number;
        }
        return null;
    }

    public static RouletteBetType outsideBetAt(int slot) {
        return OUTSIDE_BETS.get(slot);
    }

    public static int stakeIndexAt(int slot) {
        for (int index = 0; index < STAKE_SLOTS.length; index++) {
            if (STAKE_SLOTS[index] == slot) return index;
        }
        return -1;
    }

    private static String coinWord(long amount) {
        return amount == 1 ? "coin" : "coinów";
    }

    private static String signed(long amount) {
        return amount > 0 ? "+" + amount : Long.toString(amount);
    }

    private Component balanceName(Player player) {
        try {
            return Component.text("Saldo: " + economy.getBalance(player.getUniqueId()) + " coinów", NamedTextColor.YELLOW);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("ERROR: Economy balance lookup failed for " + player.getUniqueId()
                + ": " + exception.getMessage());
            return Component.text("Saldo niedostępne", NamedTextColor.RED);
        }
    }

    private static ItemStack item(Material material, Component name, boolean glow, Component... lore) {
        return item(material, name, glow, List.of(lore));
    }

    private static ItemStack item(Material material, Component name, boolean glow, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(RouletteGui::plain).toList());
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        String icon = switch (material) {
            case REDSTONE -> "red";
            case COAL, RED_DYE -> "loss";
            case EMERALD, LIME_DYE -> "win";
            case WRITABLE_BOOK -> "stats";
            case SUNFLOWER -> "balance";
            case BARRIER -> "close";
            case QUARTZ -> "even";
            case AMETHYST_SHARD -> "odd";
            case COPPER_INGOT -> "low";
            case DIAMOND -> "high";
            case CLOCK, ENDER_EYE -> "spin";
            default -> null;
        };
        if (icon != null) applyIcon(stack, icon);
        return stack;
    }

    private static void applyIcon(ItemStack stack, String icon) {
        CasinoGuiIcons.apply(stack, icon);
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
