package pl.kurdeslawo.casinocore.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import pl.kurdeslawo.casinocore.blackjack.gui.BlackjackGuiHolder;
import pl.kurdeslawo.casinocore.roulette.gui.RouletteGuiHolder;

/** Announce committed changes to the admin and players currently using casino menus. */
public final class CasinoSettingsFeedback {
    public static final String SOUND = "minecraft:block.bell.use";

    private CasinoSettingsFeedback() {}

    public static void play(JavaPlugin plugin, CommandSender sender) {
        if (sender instanceof Player admin && admin.isOnline()) play(admin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.equals(sender) || !player.isOnline()) continue;
            var holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof RouletteGuiHolder || holder instanceof BlackjackGuiHolder)
                play(player);
        }
    }

    private static void play(Player player) {
        player.playSound(player.getLocation(), SOUND, SoundCategory.MASTER, 2.0f, 0.8f);
        player.sendActionBar(
                Component.text(
                        "Zmieniono ustawienia kasyna • od następnej rundy", NamedTextColor.YELLOW));
    }
}
