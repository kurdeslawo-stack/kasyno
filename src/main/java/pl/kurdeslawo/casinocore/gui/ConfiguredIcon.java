package pl.kurdeslawo.casinocore.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

/** Uses only the server owner's vanilla material / numeric CustomModelData definitions. */
public final class ConfiguredIcon {
    private ConfiguredIcon() {}

    public static ItemStack apply(
            ItemStack stack, FileConfiguration config, String key, String state) {
        var template = create(config, key, state, stack.getType());
        stack.setType(template.getType());
        var meta = stack.getItemMeta();
        meta.setCustomModelDataComponent(template.getItemMeta().getCustomModelDataComponent());
        if (key.contains("background_")) meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Object value(
            FileConfiguration config, String path, String normal, Object fallback) {
        if (config.contains(path, true)) return config.get(path);
        if (config.contains(normal, true)) return config.get(normal);
        var defaults = config.getDefaults();
        return defaults == null ? fallback : defaults.get(path, defaults.get(normal, fallback));
    }

    public static ItemStack create(
            FileConfiguration config, String key, String state, Material fallback) {
        String base = "gui.icons." + key;
        String path = base + "." + state;
        String material =
                String.valueOf(
                        value(
                                config,
                                path + ".material",
                                base + ".normal.material",
                                fallback.name()));
        ItemStack stack =
                new ItemStack(
                        java.util.Objects.requireNonNull(
                                Material.matchMaterial(material),
                                "Nieprawidłowy materiał: " + material));
        int cmd = ((Number) value(config, path + ".cmd", base + ".normal.cmd", 0)).intValue();
        var meta = stack.getItemMeta();
        if (cmd != 0) {
            var data = meta.getCustomModelDataComponent();
            data.setFloats(java.util.List.of((float) cmd));
            meta.setCustomModelDataComponent(data);
        }
        meta.setEnchantmentGlintOverride(false);
        stack.setItemMeta(meta);
        return stack;
    }
}
