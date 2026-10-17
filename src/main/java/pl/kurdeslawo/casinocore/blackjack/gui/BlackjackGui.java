package pl.kurdeslawo.casinocore.blackjack.gui;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import pl.kurdeslawo.casinocore.blackjack.BlackjackCard;
import pl.kurdeslawo.casinocore.blackjack.BlackjackManager;
import pl.kurdeslawo.casinocore.blackjack.BlackjackOutcome;
import pl.kurdeslawo.casinocore.blackjack.BlackjackPhase;
import pl.kurdeslawo.casinocore.blackjack.BlackjackRank;
import pl.kurdeslawo.casinocore.blackjack.BlackjackRoundRecord;
import pl.kurdeslawo.casinocore.blackjack.BlackjackSession;
import pl.kurdeslawo.casinocore.blackjack.BlackjackSuit;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.gui.CasinoGuiIcons;

public final class BlackjackGui {
    public static final int SIZE = 54;
    public static final int STATUS = 13;
    public static final int HIT = 45;
    public static final int STAND = 47;
    public static final int DOUBLE = 49;
    public static final int CLOSE = 53;

    private static final int BALANCE = 50;
    private static final int HISTORY = 8;
    private static final int[] STAKES = {19, 20, 21};
    private static final int[] DEALER = {0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12};
    private static final int[] PLAYER = {27, 28, 29, 30, 31, 32, 33, 34, 36, 37, 38};

    private final JavaPlugin plugin;
    private final BlackjackManager manager;
    private final EconomyProvider economy;

    public BlackjackGui(JavaPlugin plugin, BlackjackManager manager, EconomyProvider economy) {
        this.plugin = plugin;
        this.manager = manager;
        this.economy = economy;
    }

    public void open(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackGuiHolder current
            && current.playerId().equals(player.getUniqueId())) {
            render(player, current.getInventory());
            return;
        }
        BlackjackGuiHolder holder = new BlackjackGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
            plain(Component.text("CasinoCore • Blackjack", NamedTextColor.GOLD, TextDecoration.BOLD)));
        holder.attach(inventory);
        manager.openSession(player.getUniqueId());
        render(player, inventory);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory) {
        BlackjackSession session = manager.openSession(player.getUniqueId());
        int playerVisible = session.phase() == BlackjackPhase.DEALING ? 0 : session.playerHand().cards().size();
        int dealerVisible = session.phase() == BlackjackPhase.DEALING
            ? 0
            : session.phase() == BlackjackPhase.PLAYER_TURN ? 2 : session.dealerHand().cards().size();
        boolean revealing = inventory.getHolder() instanceof BlackjackGuiHolder holder && holder.revealingDealer();
        render(player, inventory, playerVisible, revealing ? 2 : dealerVisible,
            revealing || session.phase() != BlackjackPhase.RESULT);
    }

    public void render(Player player, Inventory inventory, int playerVisible, int dealerVisible, boolean hideHole) {
        BlackjackSession session = manager.openSession(player.getUniqueId());
        ItemStack filler = CasinoGuiIcons.apply(item(Material.GREEN_STAINED_GLASS_PANE, Component.text(" "), false), "background_felt");
        ItemStack rail = CasinoGuiIcons.apply(item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), false), "background_rail");
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, slot >= 45 || slot / 9 == 2 ? rail : filler);

        List<BlackjackCard> playerCards = session.playerHand().cards();
        List<BlackjackCard> dealerCards = session.dealerHand().cards();
        for (int index = 0; index < Math.min(playerVisible, Math.min(playerCards.size(), PLAYER.length)); index++) {
            inventory.setItem(PLAYER[index], card(playerCards.get(index)));
        }
        for (int index = 0; index < Math.min(dealerVisible, Math.min(dealerCards.size(), DEALER.length)); index++) {
            inventory.setItem(DEALER[index], hideHole && index == 1 ? hidden() : card(dealerCards.get(index)));
        }

        boolean revealing = inventory.getHolder() instanceof BlackjackGuiHolder holder && holder.revealingDealer();
        inventory.setItem(STATUS, revealing ? item(Material.CLOCK,
            Component.text("RUCH KRUPIERA...", NamedTextColor.YELLOW, TextDecoration.BOLD), false,
            Component.text("Poczekaj na wynik", NamedTextColor.GRAY)) : status(session));
        renderStakes(inventory, session);
        inventory.setItem(BALANCE, balance(player));
        inventory.setItem(CLOSE, item(Material.BARRIER, Component.text("Zamknij", NamedTextColor.RED), false));
        renderActions(inventory, session);
        if (!revealing) renderHistory(inventory, player);
    }

    private void renderStakes(Inventory inventory, BlackjackSession session) {
        List<Long> stakes = manager.stakes();
        Material[] icons = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int index = 0; index < Math.min(3, stakes.size()); index++) {
            long value = stakes.get(index);
            boolean selected = value == session.selectedStake();
            inventory.setItem(STAKES[index], CasinoGuiIcons.stake(item(icons[index],
                Component.text((selected ? "✓ " : "") + value + " coinów",
                    selected ? NamedTextColor.GREEN : NamedTextColor.GOLD, TextDecoration.BOLD), selected,
                Component.text(selected ? "✓ Aktualna stawka" : "Kliknij, aby ustawić", NamedTextColor.GREEN)), value, selected));
        }
    }

    private ItemStack status(BlackjackSession session) {
        if (manager.needsReview(session.playerId())) return item(Material.BARRIER,
            Component.text("WYMAGANA WERYFIKACJA", NamedTextColor.RED, TextDecoration.BOLD), false,
            Component.text("Transakcja ma nieznany wynik", NamedTextColor.GRAY),
            Component.text("Skontaktuj się z administracją", NamedTextColor.YELLOW));
        return switch (session.phase()) {
            case BETTING -> item(Material.CLOCK,
                Component.text("ROZDAJ", NamedTextColor.GOLD, TextDecoration.BOLD), true,
                Component.text("Stawka: ", NamedTextColor.GRAY)
                    .append(Component.text(session.selectedStake(), NamedTextColor.YELLOW)),
                Component.empty(), Component.text("Kliknij, aby rozpocząć", NamedTextColor.GREEN));
            case DEALING -> item(Material.PAPER,
                Component.text("ROZDAWANIE...", NamedTextColor.YELLOW, TextDecoration.BOLD), true,
                Component.text("Karty trafiają na stół", NamedTextColor.GRAY));
            case PLAYER_TURN -> item(Material.COMPASS,
                Component.text("TWÓJ RUCH", NamedTextColor.AQUA, TextDecoration.BOLD), true,
                Component.text("Wartość ręki: ", NamedTextColor.GRAY)
                    .append(Component.text(session.playerHand().value(), NamedTextColor.WHITE)));
            case RESULT -> result(session);
            case SETTLING -> item(Material.BARRIER,
                Component.text("OCZEKIWANIE NA ROZLICZENIE", NamedTextColor.YELLOW, TextDecoration.BOLD), false,
                Component.text("Runda jest zabezpieczona", NamedTextColor.GRAY),
                Component.text("Kliknij, aby ponowić wypłatę", NamedTextColor.GREEN));
        };
    }

    private ItemStack result(BlackjackSession session) {
        boolean win = session.outcome() == BlackjackOutcome.WIN || session.outcome() == BlackjackOutcome.BLACKJACK;
        NamedTextColor color = win ? NamedTextColor.GREEN
            : session.outcome() == BlackjackOutcome.LOSS ? NamedTextColor.RED : NamedTextColor.YELLOW;
        String name = switch (session.outcome()) {
            case BLACKJACK -> "BLACKJACK!";
            case WIN -> "WYGRANA!";
            case LOSS -> "PRZEGRANA";
            case PUSH -> "REMIS";
        };
        Material material = win ? Material.EMERALD
            : session.outcome() == BlackjackOutcome.LOSS ? Material.REDSTONE : Material.GOLD_INGOT;
        return item(material, Component.text(name, color, TextDecoration.BOLD), true,
            Component.text("Ty: " + session.playerHand().value() + " • Krupier: "
                + session.dealerHand().value(), NamedTextColor.GRAY),
            Component.text("Wypłata: ", NamedTextColor.GRAY)
                .append(Component.text(session.payout(), NamedTextColor.YELLOW)));
    }

    private void renderActions(Inventory inventory, BlackjackSession session) {
        boolean active = session.phase() == BlackjackPhase.PLAYER_TURN && !manager.needsReview(session.playerId());
        boolean canDouble = active && session.playerHand().cards().size() == 2;
        inventory.setItem(HIT, item(Material.ARROW,
            Component.text("DOBIERZ", active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, TextDecoration.BOLD),
            active, Component.text(active ? "Weź kolejną kartę" : "Niedostępne", NamedTextColor.GRAY)));
        inventory.setItem(STAND, item(Material.SHIELD,
            Component.text("STÓJ", active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, TextDecoration.BOLD),
            active, Component.text(active ? "Zakończ swoją turę" : "Niedostępne", NamedTextColor.GRAY)));
        inventory.setItem(DOUBLE, item(Material.ANVIL,
            Component.text("PODWÓJ", canDouble ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY, TextDecoration.BOLD),
            canDouble, Component.text(canDouble ? "Podwój stawkę i dobierz jedną kartę" : "Niedostępne",
                NamedTextColor.GRAY)));
    }

    private void renderHistory(Inventory inventory, Player player) {
        List<BlackjackRoundRecord> rounds = manager.recent(player.getUniqueId());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Ostatnie 5 rund • od najnowszej", NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (rounds.isEmpty()) lore.add(Component.text("Nie rozegrano jeszcze żadnej rundy.", NamedTextColor.GRAY));
        for (int index = 0; index < Math.min(5, rounds.size()); index++) {
            BlackjackRoundRecord round = rounds.get(index);
            long net = round.netChange();
            NamedTextColor color = net > 0 ? NamedTextColor.GREEN : net < 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
            lore.add(Component.text((index + 1) + ". " + outcomeName(round.outcome()), NamedTextColor.GRAY)
                .append(Component.text(" • " + signed(net) + " coinów", color, TextDecoration.BOLD)));
        }
        inventory.setItem(HISTORY, item(Material.WRITABLE_BOOK,
            Component.text("HISTORIA", NamedTextColor.GOLD, TextDecoration.BOLD), false,
            lore.toArray(Component[]::new)));
    }

    private ItemStack balance(Player player) {
        try {
            return item(Material.SUNFLOWER,
                Component.text("Saldo: " + economy.getBalance(player.getUniqueId()), NamedTextColor.YELLOW), false);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Blackjack balance lookup failed: " + exception.getMessage());
            return item(Material.BARRIER, Component.text("Saldo niedostępne", NamedTextColor.RED), false);
        }
    }

    private static ItemStack card(BlackjackCard card) {
        boolean red = card.suit() == BlackjackSuit.HEARTS || card.suit() == BlackjackSuit.DIAMONDS;
        ItemStack stack = item(Material.PAPER,
            Component.text(rank(card.rank()) + " " + suit(card.suit()),
                red ? NamedTextColor.RED : NamedTextColor.WHITE, TextDecoration.BOLD),
            false,
            Component.text(cardName(card), NamedTextColor.GRAY));
        setItemModel(stack, BlackjackCardModels.modelKey(card));
        return stack;
    }

    private static ItemStack hidden() {
        ItemStack stack = item(Material.PAPER,
            Component.text("ZAKRYTA KARTA", NamedTextColor.DARK_GRAY, TextDecoration.BOLD), true,
            Component.text("Krupier odsłoni ją po Twojej turze", NamedTextColor.GRAY));
        setItemModel(stack, BlackjackCardModels.cardBackKey());
        return stack;
    }

    private static String rank(BlackjackRank rank) {
        return switch (rank) {
            case ACE -> "A";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            default -> Integer.toString(rank.value());
        };
    }

    private static String suit(BlackjackSuit suit) {
        return switch (suit) {
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS -> "♣";
            case SPADES -> "♠";
        };
    }

    private static String cardName(BlackjackCard card) {
        String rankName = switch (card.rank()) {
            case ACE -> "As";
            case JACK -> "Walet";
            case QUEEN -> "Dama";
            case KING -> "Król";
            default -> Integer.toString(card.rank().value());
        };
        String suitName = switch (card.suit()) {
            case HEARTS -> "kier";
            case DIAMONDS -> "karo";
            case CLUBS -> "trefl";
            case SPADES -> "pik";
        };
        return rankName + " " + suitName;
    }

    private static String outcomeName(BlackjackOutcome outcome) {
        return switch (outcome) {
            case BLACKJACK -> "BLACKJACK";
            case WIN -> "WYGRANA";
            case LOSS -> "PRZEGRANA";
            case PUSH -> "REMIS";
        };
    }

    private static String signed(long number) {
        return number > 0 ? "+" + number : Long.toString(number);
    }

    public static int stakeAt(int slot) {
        for (int index = 0; index < STAKES.length; index++) {
            if (STAKES[index] == slot) return index;
        }
        return -1;
    }

    private static ItemStack item(Material material, Component name, boolean glow, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(BlackjackGui::plain).toList());
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        String icon = switch (material) {
            case WRITABLE_BOOK -> "stats";
            case SUNFLOWER -> "balance";
            case BARRIER -> "close";
            case EMERALD -> "win";
            case REDSTONE -> "loss";
            case GOLD_INGOT -> "balance";
            case ARROW -> "hit" + (glow ? "" : "_disabled");
            case SHIELD -> "stand" + (glow ? "" : "_disabled");
            case ANVIL -> "double" + (glow ? "" : "_disabled");
            case CLOCK, PAPER, COMPASS -> "deal";
            default -> null;
        };
        if (icon != null) CasinoGuiIcons.apply(stack, icon);
        return stack;
    }

    private static void setItemModel(ItemStack stack, org.bukkit.NamespacedKey modelKey) {
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(modelKey);
        stack.setItemMeta(meta);
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
