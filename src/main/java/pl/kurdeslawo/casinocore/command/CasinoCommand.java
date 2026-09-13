package pl.kurdeslawo.casinocore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.kurdeslawo.casinocore.CasinoCorePlugin;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.LocalEconomyProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CasinoCommand implements CommandExecutor, TabCompleter {
    private final CasinoCorePlugin plugin;

    public CasinoCommand(CasinoCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("CasinoCore " + plugin.getPluginMeta().getVersion()
                + " | Roulette stakes: " + plugin.rouletteManager().allowedStakes());
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "balance" -> balance(sender);
            case "coins" -> coins(sender, args);
            default -> {
                sender.sendMessage("Usage: /casino [reload|balance|coins set <player> <amount>|coins add <player> <amount>]");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("casinocore.admin")) {
            sender.sendMessage("You do not have permission to reload CasinoCore.");
            return true;
        }
        plugin.reloadPluginConfiguration();
        sender.sendMessage("CasinoCore configuration reloaded.");
        return true;
    }

    private boolean balance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use /casino balance.");
            return true;
        }
        sender.sendMessage("Balance: " + plugin.economyProvider().getBalance(player.getUniqueId()) + " coins.");
        return true;
    }

    private boolean coins(CommandSender sender, String[] args) {
        if (!sender.hasPermission("casinocore.admin")) {
            sender.sendMessage("You do not have permission to manage test coins.");
            return true;
        }
        if (args.length != 4 || !(args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add"))) {
            sender.sendMessage("Usage: /casino coins <set|add> <player> <amount>");
            return true;
        }

        EconomyProvider provider = plugin.economyProvider();
        if (!(provider instanceof LocalEconomyProvider localProvider)) {
            sender.sendMessage("Coin administration is available only for the local development economy.");
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

        sender.sendMessage("Balance for " + displayName(target) + " is now "
            + localProvider.getBalance(target.getUniqueId()) + " coins.");
        return true;
    }

    private static String displayName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return matching(List.of("reload", "balance", "coins"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("coins")) {
            return matching(List.of("set", "add"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("coins")) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
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
