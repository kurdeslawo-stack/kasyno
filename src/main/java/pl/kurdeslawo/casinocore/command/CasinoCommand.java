package pl.kurdeslawo.casinocore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import pl.kurdeslawo.casinocore.CasinoCorePlugin;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.LocalEconomyProvider;
import pl.kurdeslawo.casinocore.gui.CasinoSettingsFeedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CasinoCommand implements CommandExecutor, TabCompleter {
    private final CasinoCorePlugin plugin;

    public CasinoCommand(CasinoCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(
                    "CasinoCore "
                            + plugin.getPluginMeta().getVersion()
                            + " | Roulette stakes: "
                            + plugin.rouletteManager().allowedStakes());
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "balance" -> balance(sender);
            case "coins" -> coins(sender, args);
            case "roulette" -> roulette(sender, args);
            case "blackjack" -> blackjack(sender, args);
            case "transactions" -> transactions(sender, args);
            case "chances" -> chances(sender, args);
            default -> help(sender);
        };
    }

    private boolean chances(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        try {
            if (args.length == 1) {
                var random = plugin.rouletteRandom();
                sender.sendMessage("§6CasinoCore • Szanse (wagi są względne, nie procentowe)");
                for (int start : new int[] {0, 7}) {
                    List<String> values = new ArrayList<>();
                    for (int n = start; n < Math.min(start + 7, 13); n++) {
                        values.add(n + ": " + percent(random.probability(n)));
                    }
                    sender.sendMessage("§7Ruletka: " + String.join(" | ", values));
                }
                sender.sendMessage(
                        "§7Blackjack: "
                                + (plugin.blackjackManager().dealerHitsSoft17()
                                        ? "H17 — krupier dobiera na miękkim 17"
                                        : "standard — krupier stoi na miękkim 17"));
                sender.sendMessage(
                        "§7Ruletka: szansa każdego zakładu jest widoczna w jego opisie w GUI.");
                sender.sendMessage(
                        "§7Blackjack nie ma stałego % wygranych; wynik zależy od kart i decyzji gracza.");
                sender.sendMessage(
                        "§7Zysk za blackjacka: "
                                + plugin.blackjackManager().blackjackProfitNumerator()
                                + "/"
                                + plugin.blackjackManager().blackjackProfitDenominator()
                                + " stawki (zaokrąglany w dół).");
                sender.sendMessage(
                        "§e/casino chances roulette <0-12> <waga> | blackjack <standard|h17>");
                return true;
            }
            boolean saved;
            if (args.length == 4 && args[1].equalsIgnoreCase("roulette")) {
                int number = Integer.parseInt(args[2]);
                long weight = Long.parseLong(args[3]);
                if (number < 0
                        || number > 12
                        || weight < 0
                        || weight > pl.kurdeslawo.casinocore.roulette.RouletteRandom.MAX_WEIGHT) {
                    throw new IllegalArgumentException("Numer: 0–12, waga: 0–1000000");
                }
                saved = plugin.updateRouletteWeight(number, weight);
            } else if (args.length == 3
                    && args[1].equalsIgnoreCase("blackjack")
                    && (args[2].equalsIgnoreCase("standard") || args[2].equalsIgnoreCase("h17"))) {
                saved = plugin.updateBlackjackDifficulty(args[2].equalsIgnoreCase("h17"));
            } else {
                sender.sendMessage(
                        "§e/casino chances roulette <0-12> <waga> | blackjack <standard|h17>");
                return true;
            }
            if (saved) {
                plugin.getLogger()
                        .info(
                                "Chance settings changed by "
                                        + sender.getName()
                                        + ": "
                                        + String.join(" ", args));
                sender.sendMessage(
                        "§aZapisano w config.yml. Nowe ustawienia obowiązują od kolejnej rundy.");
                CasinoSettingsFeedback.play(plugin, sender);
            } else
                sender.sendMessage(
                        "§cNie zapisano ustawień. Sprawdź config i log serwera; przynajmniej jedna waga musi być dodatnia.");
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§cNiepoprawne ustawienie: " + exception.getMessage());
        }
        return true;
    }

    private static String percent(double value) {
        return String.format(Locale.forLanguageTag("pl-PL"), "%.2f%%", 100 * value);
    }

    private boolean transactions(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        try {
            if ((args.length == 2 || args.length == 3) && args[1].equalsIgnoreCase("list")) {
                int page = args.length == 3 ? Integer.parseInt(args[2]) : 1;
                if (page < 1) throw new IllegalArgumentException("Page must be positive");
                var pending =
                        plugin.roundJournal().entries(null).stream()
                                .filter(e -> !e.complete())
                                .toList();
                sender.sendMessage(
                        "Pending rounds: "
                                + pending.size()
                                + " | page "
                                + page
                                + "/"
                                + Math.max(1, (pending.size() + 19) / 20));
                pending.stream()
                        .skip((page - 1L) * 20)
                        .limit(20)
                        .forEach(
                                e ->
                                        sender.sendMessage(
                                                e.id()
                                                        + " "
                                                        + e.game()
                                                        + " player="
                                                        + e.player()
                                                        + " "
                                                        + e.transactions()));
            } else if (args.length == 3 && args[1].equalsIgnoreCase("retry")) {
                java.util.UUID id = java.util.UUID.fromString(args[2]);
                var entry = plugin.roundJournal().get(id);
                var payout = entry.transactions().get("payout");
                if (entry.complete()
                        || entry.needsReview()
                        || payout == null
                        || payout.state()
                                != pl.kurdeslawo.casinocore.economy.RoundJournal.State.REJECTED) {
                    throw new IllegalStateException(
                            "Only explicitly rejected payouts may be retried");
                }
                if (entry.game().equals("blackjack")) plugin.blackjackManager().recoverRound(id);
                else plugin.rouletteManager().recoverRound(id);
                sender.sendMessage("Rejected payout retried using its original round.");
            } else if (args.length >= 6 && args[1].equalsIgnoreCase("resolve")) {
                java.util.UUID id = java.util.UUID.fromString(args[2]);
                boolean applied;
                if (args[4].equalsIgnoreCase("applied")) applied = true;
                else if (args[4].equalsIgnoreCase("not-applied")) applied = false;
                else
                    throw new IllegalArgumentException(
                            "Use applied or not-applied after checking economy logs");
                String evidence =
                        sender.getName()
                                + ": "
                                + String.join(
                                        " ", java.util.Arrays.copyOfRange(args, 5, args.length));
                plugin.roundJournal().resolve(id, args[3], applied, evidence);
                plugin.getLogger()
                        .warning(
                                "RECONCILIATION: "
                                        + id
                                        + " "
                                        + args[3]
                                        + " "
                                        + args[4]
                                        + " "
                                        + evidence);
                if (plugin.roundJournal().get(id).game().equals("blackjack"))
                    plugin.blackjackManager().recoverRound(id);
                else plugin.rouletteManager().recoverRound(id);
                sender.sendMessage(
                        "Reconciliation saved. No unknown transaction was automatically replayed.");
            } else
                sender.sendMessage(
                        "Usage: /casino transactions list [page] | retry <round-id> | resolve <round-id> <stake|double|payout> <applied|not-applied> <evidence from economy logs>");
        } catch (RuntimeException exception) {
            sender.sendMessage("Transaction remains protected: " + exception.getMessage());
            plugin.getLogger().severe("RECONCILIATION: " + exception.getMessage());
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        String previous = chanceSettingsKey();
        boolean reloaded = plugin.reloadPluginConfiguration();
        sender.sendMessage(
                reloaded
                        ? "CasinoCore configuration reloaded."
                        : "CasinoCore configuration is invalid or requires a restart. Check the console.");
        if (reloaded && !previous.equals(chanceSettingsKey()))
            CasinoSettingsFeedback.play(plugin, sender);
        return true;
    }

    private String chanceSettingsKey() {
        var roulette = plugin.rouletteManager();
        var blackjack = plugin.blackjackManager();
        return plugin.rouletteRandom().weights()
                + ":"
                + roulette.outsideMultiplier()
                + ":"
                + roulette.exactMultiplier()
                + ":"
                + blackjack.dealerHitsSoft17()
                + ":"
                + blackjack.blackjackProfitNumerator()
                + ":"
                + blackjack.blackjackProfitDenominator();
    }

    private boolean balance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use /casino balance.");
            return true;
        }
        try {
            sender.sendMessage(
                    "Balance: "
                            + plugin.economyProvider().getBalance(player.getUniqueId())
                            + " coins.");
        } catch (RuntimeException exception) {
            plugin.getLogger()
                    .severe(
                            "ERROR: Economy balance lookup failed for "
                                    + player.getUniqueId()
                                    + ": "
                                    + exception.getMessage());
            sender.sendMessage("The economy is temporarily unavailable. Check the server console.");
        }
        return true;
    }

    private boolean roulette(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Roulette commands must be used by a player.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /casino roulette <open|create|remove>");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                if (!requirePermission(sender, "casinocore.roulette.open")) {
                    yield true;
                }
                plugin.rouletteGui().open(player);
                yield true;
            }
            case "create" -> {
                if (!requirePermission(sender, "casinocore.roulette.manage")) {
                    yield true;
                }
                Interaction interaction = plugin.interactionManager().create(player.getLocation());
                sender.sendMessage(
                        "Created Roulette interaction hitbox: " + interaction.getUniqueId());
                yield true;
            }
            case "remove" -> {
                if (!requirePermission(sender, "casinocore.roulette.manage")) {
                    yield true;
                }
                double radius =
                        plugin.getConfig().getDouble("roulette.interaction.remove-radius", 4.0);
                boolean removed =
                        plugin.interactionManager()
                                .removeNearest(player.getLocation(), radius)
                                .isPresent();
                sender.sendMessage(
                        removed
                                ? "Removed the nearest Roulette hitbox."
                                : "No Roulette hitbox found nearby.");
                yield true;
            }
            default -> {
                sender.sendMessage("Usage: /casino roulette <open|create|remove>");
                yield true;
            }
        };
    }

    private boolean blackjack(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Blackjack must be opened by a player.");
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("open")) {
            sender.sendMessage("Usage: /casino blackjack open");
            return true;
        }
        if (!requirePermission(sender, "casinocore.blackjack.open")) return true;
        plugin.blackjackGui().open(player);
        return true;
    }

    private boolean coins(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length != 4
                || !(args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add"))) {
            sender.sendMessage("Usage: /casino coins <set|add> <player> <amount>");
            return true;
        }
        EconomyProvider provider = plugin.economyProvider();
        if (!(provider instanceof LocalEconomyProvider localProvider)) {
            sender.sendMessage(
                    "Coin administration is available only for the local development economy.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
            if (amount < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            sender.sendMessage("Amount must be a non-negative whole number.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        try {
            if (args[1].equalsIgnoreCase("set")) {
                localProvider.setBalance(target.getUniqueId(), amount);
            } else {
                localProvider.deposit(target.getUniqueId(), amount);
            }
        } catch (ArithmeticException exception) {
            sender.sendMessage("The resulting balance would exceed the supported limit.");
            return true;
        }
        sender.sendMessage(
                "Balance for "
                        + displayName(target)
                        + " is now "
                        + localProvider.getBalance(target.getUniqueId())
                        + " coins.");
        return true;
    }

    private static boolean requireAdmin(CommandSender sender) {
        return requirePermission(sender, "casinocore.admin");
    }

    private static boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage("You do not have permission to use this command.");
        return false;
    }

    private static boolean help(CommandSender sender) {
        sender.sendMessage(
                "Usage: /casino [reload|balance|coins set|coins add|roulette open|roulette create|roulette remove|blackjack open|transactions list|chances]");
        return true;
    }

    private static String displayName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("balance");
            if (sender.hasPermission("casinocore.admin")) {
                commands.add("reload");
                commands.add("coins");
                commands.add("transactions");
                commands.add("chances");
            }
            if (sender.hasPermission("casinocore.roulette.open")
                    || sender.hasPermission("casinocore.roulette.manage")) {
                commands.add("roulette");
            }
            if (sender.hasPermission("casinocore.blackjack.open")) commands.add("blackjack");
            return matching(commands, args[0]);
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("coins")
                && sender.hasPermission("casinocore.admin")) {
            return matching(List.of("set", "add"), args[1]);
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("transactions")
                && sender.hasPermission("casinocore.admin")) {
            return matching(List.of("list", "resolve", "retry"), args[1]);
        }
        if (args.length > 1
                && args[0].equalsIgnoreCase("chances")
                && sender.hasPermission("casinocore.admin")) {
            if (args.length == 2) return matching(List.of("roulette", "blackjack"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("blackjack"))
                return matching(List.of("standard", "h17"), args[2]);
            if (args.length == 3 && args[1].equalsIgnoreCase("roulette"))
                return matching(
                        java.util.stream.IntStream.rangeClosed(0, 12)
                                .mapToObj(Integer::toString)
                                .toList(),
                        args[2]);
            if (args.length == 4 && args[1].equalsIgnoreCase("roulette"))
                return matching(List.of("0", "1", "2", "5"), args[3]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("roulette")) {
            List<String> rouletteCommands = new ArrayList<>();
            if (sender.hasPermission("casinocore.roulette.open")) {
                rouletteCommands.add("open");
            }
            if (sender.hasPermission("casinocore.roulette.manage")) {
                rouletteCommands.add("create");
                rouletteCommands.add("remove");
            }
            return matching(rouletteCommands, args[1]);
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("blackjack")
                && sender.hasPermission("casinocore.blackjack.open"))
            return matching(List.of("open"), args[1]);
        if (args.length == 3
                && args[0].equalsIgnoreCase("coins")
                && sender.hasPermission("casinocore.admin")) {
            return matching(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
