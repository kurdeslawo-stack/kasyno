package pl.kurdeslawo.casinocore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;

import pl.kurdeslawo.casinocore.CasinoCorePlugin;
import pl.kurdeslawo.casinocore.economy.LocalEconomyProvider;
import pl.kurdeslawo.casinocore.economy.Money;

import java.util.List;
import java.util.Locale;

/** Operational commands only; no chance manipulation, furniture or player /casino menu. */
public final class CasinoCommand implements CommandExecutor, TabCompleter {
    private final CasinoCorePlugin plugin;

    public CasinoCommand(CasinoCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(
                    plugin.reloadPluginConfiguration()
                            ? "§aPrzeładowano konfigurację."
                            : "§cOdrzucono konfigurację. Sprawdź log serwera.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("transactions"))
            return transactions(sender, args);
        if (args.length == 4
                && args[0].equalsIgnoreCase("coins")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add"))) {
            if (!(plugin.economyProvider() instanceof LocalEconomyProvider local)) {
                sender.sendMessage("§cKomenda dostępna wyłącznie w testowej ekonomii local.");
                return true;
            }
            var target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cGracz musi być online.");
                return true;
            }
            try {
                long amount = Money.parse(args[3]);
                if (args[1].equalsIgnoreCase("set")) local.setBalance(target.getUniqueId(), amount);
                else local.deposit(target.getUniqueId(), amount);
                sender.sendMessage(
                        "§aSaldo: " + Money.format(local.getBalance(target.getUniqueId())) + "₭");
            } catch (RuntimeException e) {
                sender.sendMessage("§cNieprawidłowa kwota (maksymalnie 2 miejsca po przecinku).");
            }
            return true;
        }
        sender.sendMessage(
                "§5CasinoCore "
                        + plugin.getPluginMeta().getVersion()
                        + " — Laina | ruletka 0–10, równe szanse");
        sender.sendMessage(
                "§e/casinocore reload | transactions list | coins <set|add> <nick> <kwota>");
        return true;
    }

    private static boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("casinocore.admin")) return true;
        sender.sendMessage("§cBrak uprawnień.");
        return false;
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
                        "Usage: /casinocore transactions list [page] | retry <round-id> | resolve <round-id> <stake|double|payout> <applied|not-applied> <evidence from economy logs>");
        } catch (RuntimeException exception) {
            sender.sendMessage("Transaction remains protected: " + exception.getMessage());
            plugin.getLogger().severe("RECONCILIATION: " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("casinocore.admin")) return List.of();
        List<String> candidates =
                args.length == 1
                        ? List.of("reload", "transactions", "coins")
                        : args.length == 2 && args[0].equalsIgnoreCase("transactions")
                                ? List.of("list", "retry", "resolve")
                                : args.length == 2 && args[0].equalsIgnoreCase("coins")
                                        ? List.of("set", "add")
                                        : List.of();
        return candidates.stream()
                .filter(n -> n.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)))
                .toList();
    }
}
