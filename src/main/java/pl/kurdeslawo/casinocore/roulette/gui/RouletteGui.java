package pl.kurdeslawo.casinocore.roulette.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.roulette.RouletteBetType;
import pl.kurdeslawo.casinocore.roulette.RouletteColor;
import pl.kurdeslawo.casinocore.roulette.RouletteManager;
import pl.kurdeslawo.casinocore.roulette.RoulettePhase;
import pl.kurdeslawo.casinocore.roulette.RouletteSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RouletteGui {
    public static final int SIZE = 54;
    public static final int CLOSE_SLOT = 53;
    private static final int RESULT_SLOT = 40;
    private static final int BALANCE_SLOT = 50;
    private static final int[] NUMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
    private static final int[] STAKE_SLOTS = {46, 47, 48};
    private static final Map<Integer, RouletteBetType> OUTSIDE_BETS = Map.of(
        28, RouletteBetType.RED,
        29, RouletteBetType.BLACK,
        30, RouletteBetType.EVEN,
        31, RouletteBetType.ODD,
        32, RouletteBetType.LOW,
        33, RouletteBetType.HIGH
    );

    private final RouletteManager manager;
    private final EconomyProvider economy;

    public RouletteGui(RouletteManager manager, EconomyProvider economy) {
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
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("CasinoCore Roulette", NamedTextColor.GOLD));
        holder.attach(inventory);
        manager.openSession(player.getUniqueId());
        render(player, inventory, null);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, Integer highlightedNumber) {
        RouletteSession session = manager.openSession(player.getUniqueId());
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        renderNumbers(inventory, highlightedNumber);

        inventory.setItem(28, betItem(Material.RED_WOOL, "RED", NamedTextColor.RED));
        inventory.setItem(29, betItem(Material.BLACK_WOOL, "BLACK", NamedTextColor.DARK_GRAY));
        inventory.setItem(30, betItem(Material.WHITE_WOOL, "EVEN", NamedTextColor.WHITE));
        inventory.setItem(31, betItem(Material.LIGHT_GRAY_WOOL, "ODD", NamedTextColor.GRAY));
        inventory.setItem(32, betItem(Material.LIME_WOOL, "LOW · 1-6", NamedTextColor.GREEN));
        inventory.setItem(33, betItem(Material.BLUE_WOOL, "HIGH · 7-12", NamedTextColor.BLUE));

        List<Long> stakes = manager.allowedStakes();
        for (int index = 0; index < Math.min(STAKE_SLOTS.length, stakes.size()); index++) {
            long stake = stakes.get(index);
            boolean selected = stake == session.selectedStake();
            inventory.setItem(STAKE_SLOTS[index], item(selected ? Material.GOLD_INGOT : Material.GOLD_NUGGET,
                Component.text(stake + (stake == 1 ? " coin" : " coins"), selected ? NamedTextColor.YELLOW : NamedTextColor.GOLD),
                Component.text(selected ? "Selected stake" : "Click to select", NamedTextColor.GRAY)));
        }

        inventory.setItem(BALANCE_SLOT, item(Material.SUNFLOWER,
            Component.text("Balance: " + economy.getBalance(player.getUniqueId()) + " coins", NamedTextColor.YELLOW),
            Component.text("Stake: " + session.selectedStake(), NamedTextColor.GRAY)));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text("Close", NamedTextColor.RED)));

        if (session.phase() == RoulettePhase.SPINNING) {
            inventory.setItem(RESULT_SLOT, item(Material.CLOCK,
                Component.text("Wheel spinning...", NamedTextColor.GOLD),
                Component.text("Your bet is locked", NamedTextColor.GRAY)));
        } else if (session.phase() == RoulettePhase.RESULT && session.result() != null) {
            boolean won = session.payout() > 0;
            inventory.setItem(RESULT_SLOT, item(won ? Material.EMERALD : Material.COAL,
                Component.text("Result: " + session.result().winningNumber(), won ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text(won ? "Won " + session.payout() + " coins" : "No payout", NamedTextColor.GRAY)));
        } else {
            inventory.setItem(RESULT_SLOT, item(Material.COMPASS,
                Component.text("Choose a bet", NamedTextColor.AQUA),
                Component.text("The stake is withdrawn once after selection", NamedTextColor.GRAY)));
        }
    }

    public void highlightNumber(Inventory inventory, int highlightedNumber) {
        renderNumbers(inventory, highlightedNumber);
    }

    private void renderNumbers(Inventory inventory, Integer highlightedNumber) {
        for (int number = 0; number <= 12; number++) {
            RouletteColor color = pl.kurdeslawo.casinocore.roulette.RouletteEngine.colorOf(number);
            Material material = color == RouletteColor.GREEN ? Material.LIME_CONCRETE
                : color == RouletteColor.RED ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            if (highlightedNumber != null && highlightedNumber == number) {
                material = Material.YELLOW_CONCRETE;
            }
            NamedTextColor textColor = color == RouletteColor.RED ? NamedTextColor.RED
                : color == RouletteColor.BLACK ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN;
            inventory.setItem(NUMBER_SLOTS[number], item(material,
                Component.text("Number " + number, textColor),
                Component.text("Exact number · total return x" + manager.exactMultiplier(), NamedTextColor.GRAY)));
        }
    }

    public static Integer numberAt(int slot) {
        for (int number = 0; number < NUMBER_SLOTS.length; number++) {
            if (NUMBER_SLOTS[number] == slot) {
                return number;
            }
        }
        return null;
    }

    public static RouletteBetType outsideBetAt(int slot) {
        return OUTSIDE_BETS.get(slot);
    }

    public static int stakeIndexAt(int slot) {
        for (int index = 0; index < STAKE_SLOTS.length; index++) {
            if (STAKE_SLOTS[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    private ItemStack betItem(Material material, String label, NamedTextColor color) {
        return item(material, Component.text(label, color), Component.text("Total return x" + manager.outsideMultiplier(), NamedTextColor.GRAY));
    }

    private static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) {
            meta.lore(List.of(lore));
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
