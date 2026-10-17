package pl.kurdeslawo.casinocore.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Shared resource-pack presentation; leaves item names, lore and game state intact. */
public final class CasinoGuiIcons {
    private CasinoGuiIcons() { }

    public static ItemStack apply(ItemStack stack, String icon) {
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("casinocore", "roulette/gui/" + icon));
        // Glint tints the entire sprite and makes small denomination digits hard to read.
        meta.setEnchantmentGlintOverride(false);
        if (icon.startsWith("background_")) meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack stake(ItemStack stack, long amount, boolean selected) {
        if (amount == 1 || amount == 5 || amount == 25) {
            return apply(stack, "stake_" + amount + (selected ? "_selected" : ""));
        }
        // Configurable denominations retain their correct name/lore and vanilla icon.
        return stack;
    }
}
