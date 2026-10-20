package pl.kurdeslawo.casinocore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import pl.kurdeslawo.casinocore.CasinoCorePlugin;

import java.util.List;
import java.util.Locale;

/** Furniture runs /ruletka <nick> as console. The caller, not the target, needs permission. */
public final class OpenGameCommand implements CommandExecutor, TabCompleter {
    private final CasinoCorePlugin plugin;
    private final boolean blackjack;

    public OpenGameCommand(CasinoCorePlugin plugin, boolean blackjack) {
        this.plugin = plugin;
        this.blackjack = blackjack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String permission = "casinocore." + (blackjack ? "blackjack" : "roulette") + ".open";
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§e/" + label + " <nick>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cGracz musi być online.");
            return true;
        }
        if (blackjack) plugin.blackjackGui().open(target);
        else plugin.rouletteGui().open(target);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1
                || !sender.hasPermission(
                        "casinocore." + (blackjack ? "blackjack" : "roulette") + ".open"))
            return List.of();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(
                        n ->
                                n.toLowerCase(Locale.ROOT)
                                        .startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
    }
}
