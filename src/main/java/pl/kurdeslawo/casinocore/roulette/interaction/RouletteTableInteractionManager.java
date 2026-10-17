package pl.kurdeslawo.casinocore.roulette.interaction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RouletteTableInteractionManager {
    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final File storageFile;
    private final List<TableRecord> tables = new ArrayList<>();

    public RouletteTableInteractionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "roulette_interaction");
        this.storageFile = new File(plugin.getDataFolder(), "roulette-tables.yml");
    }

    public void load() {
        tables.clear();
        YamlConfiguration storage = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection section = storage.getConfigurationSection("tables");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String worldName = section.getString(key + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger()
                        .warning("Skipping Roulette hitbox " + key + ": world is not loaded.");
                continue;
            }
            try {
                UUID entityId = UUID.fromString(key);
                Location location =
                        new Location(
                                world,
                                section.getDouble(key + ".x"),
                                section.getDouble(key + ".y"),
                                section.getDouble(key + ".z"),
                                (float) section.getDouble(key + ".yaw"),
                                0.0f);
                location.getChunk().load();
                Interaction interaction =
                        findInteraction(world, entityId).orElseGet(() -> spawn(location));
                configure(interaction);
                tables.add(new TableRecord(interaction.getUniqueId(), location));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid Roulette hitbox record " + key + ".");
            }
        }
        save();
    }

    public Interaction create(Location location) {
        Interaction interaction = spawn(location);
        tables.add(new TableRecord(interaction.getUniqueId(), interaction.getLocation()));
        save();
        return interaction;
    }

    public Optional<Location> removeNearest(Location origin, double radius) {
        double radiusSquared = radius * radius;
        Optional<TableRecord> nearest =
                tables.stream()
                        .filter(table -> table.location().getWorld().equals(origin.getWorld()))
                        .filter(table -> table.location().distanceSquared(origin) <= radiusSquared)
                        .min(
                                Comparator.comparingDouble(
                                        table -> table.location().distanceSquared(origin)));
        if (nearest.isEmpty()) {
            return Optional.empty();
        }
        TableRecord record = nearest.get();
        findInteraction(record.location().getWorld(), record.entityId()).ifPresent(Entity::remove);
        tables.remove(record);
        save();
        return Optional.of(record.location().clone());
    }

    public boolean isRouletteInteraction(Entity entity) {
        return entity instanceof Interaction
                && entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public void updateDimensions() {
        for (TableRecord table : tables) {
            findInteraction(table.location().getWorld(), table.entityId())
                    .ifPresent(this::configure);
        }
    }

    private Interaction spawn(Location location) {
        return location.getWorld().spawn(location, Interaction.class, this::configure);
    }

    private void configure(Interaction interaction) {
        interaction.setInteractionWidth(
                (float) plugin.getConfig().getDouble("roulette.interaction.width", 2.75));
        interaction.setInteractionHeight(
                (float) plugin.getConfig().getDouble("roulette.interaction.height", 1.5));
        interaction.setResponsive(true);
        interaction.addScoreboardTag("casinocore_roulette_interaction");
        interaction.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
    }

    private static Optional<Interaction> findInteraction(World world, UUID entityId) {
        Entity entity = world.getEntity(entityId);
        return entity instanceof Interaction interaction
                ? Optional.of(interaction)
                : Optional.empty();
    }

    private void save() {
        YamlConfiguration storage = new YamlConfiguration();
        for (TableRecord table : tables) {
            String path = "tables." + table.entityId();
            Location location = table.location();
            storage.set(path + ".world", location.getWorld().getName());
            storage.set(path + ".x", location.getX());
            storage.set(path + ".y", location.getY());
            storage.set(path + ".z", location.getZ());
            storage.set(path + ".yaw", location.getYaw());
        }
        try {
            storage.save(storageFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not save Roulette interaction tables", exception);
        }
    }

    private record TableRecord(UUID entityId, Location location) {
        private TableRecord {
            location = location.clone();
        }
    }
}
