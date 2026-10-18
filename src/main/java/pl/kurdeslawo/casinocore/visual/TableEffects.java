package pl.kurdeslawo.casinocore.visual;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.util.*;

/** Ambient demonstration only. Game results, payouts and sessions are never accessed. */
public final class TableEffects implements AutoCloseable, Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey originalKey;
    private final Map<UUID, Decoration> tables = new HashMap<>();
    private final BukkitTask task;
    private long ticks;

    private record Decoration(
            ItemDisplay table, boolean blackjack, List<ItemDisplay> items, long start) {}

    public TableEffects(JavaPlugin plugin) {
        this.plugin = plugin;
        originalKey = new NamespacedKey(plugin, "show_original_item");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 2);
    }

    private String type(ItemDisplay display) {
        var meta = display.getItemStack().getItemMeta();
        if (meta == null || meta.getItemModel() == null) return "";
        var key = meta.getItemModel();
        return key.getNamespace().equals("casinocore") ? key.getKey() : "";
    }

    private ItemStack item(String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        var meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("casinocore", model));
        stack.setItemMeta(meta);
        return stack;
    }

    private void restore(ItemDisplay table) {
        byte[] bytes =
                table.getPersistentDataContainer().get(originalKey, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            table.setItemStack(ItemStack.deserializeBytes(bytes));
            table.getPersistentDataContainer().remove(originalKey);
        }
    }

    private void remove(Decoration decoration) {
        decoration.items().forEach(Entity::remove);
        if (decoration.table().isValid()) restore(decoration.table());
    }

    private void discover() {
        Set<UUID> nearby = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Entity entity : player.getNearbyEntities(12, 6, 12)) {
                if (!(entity instanceof ItemDisplay table)) continue;
                if (tables.containsKey(table.getUniqueId())) {
                    nearby.add(table.getUniqueId());
                    continue;
                }
                // Recover an original item after an interrupted previous server process.
                restore(table);
                String model = type(table);
                boolean blackjack = model.equals("blackjack_table");
                if (!blackjack && !model.equals("roulette_table")) continue;
                if ((table.getItemDisplayTransform() != ItemDisplay.ItemDisplayTransform.FIXED
                                && table.getItemDisplayTransform()
                                        != ItemDisplay.ItemDisplayTransform.NONE)
                        || table.getBillboard() != Display.Billboard.FIXED) continue;
                nearby.add(table.getUniqueId());
                if (tables.size() >= 16) continue;
                List<ItemDisplay> parts = new ArrayList<>();
                try {
                    {
                        table.getPersistentDataContainer()
                                .set(
                                        originalKey,
                                        PersistentDataType.BYTE_ARRAY,
                                        table.getItemStack().serializeAsBytes());
                        ItemStack base = table.getItemStack().clone();
                        var meta = base.getItemMeta();
                        meta.setItemModel(new NamespacedKey("casinocore", "effects/" + (blackjack ? "blackjack" : "roulette") + "_table_show"));
                        base.setItemMeta(meta);
                        table.setItemStack(base);
                    }
                    String[] models =
                            blackjack
                                    ? new String[] {
                                        "blackjack/cards/ace_hearts",
                                        "blackjack/cards/king_spades",
                                        "blackjack/cards/queen_diamonds",
                                        "effects/blackjack_stack_ivory", "effects/blackjack_stack_red", "effects/blackjack_stack_green"
                                    }
                                    : new String[] {"roulette_wheel", "roulette_ball",
                                        "effects/roulette_stack_ivory", "effects/roulette_stack_red", "effects/roulette_stack_green"};
                    for (String part : models) {
                        parts.add(
                                table.getWorld()
                                        .spawn(
                                                table.getLocation(),
                                                ItemDisplay.class,
                                                display -> {
                                                    display.setItemStack(item(part));
                                                    display.setItemDisplayTransform(
                                                            ItemDisplay.ItemDisplayTransform.FIXED);
                                                    display.setPersistent(false);
                                                    display.setInvulnerable(true);
                                                    display.setViewRange(.5f);
                                                    display.setTeleportDuration(2);
                                                    // Hidden until the first valid local pose is
                                                    // applied.
                                                    display.setTransformationMatrix(
                                                            new Matrix4f().scale(.0001f));
                                                    display.addScoreboardTag("casinocore_cosmetic");
                                                }));
                    }
                    tables.put(table.getUniqueId(), new Decoration(table, blackjack, parts, ticks));
                } catch (RuntimeException error) {
                    parts.forEach(Entity::remove);
                    restore(table);
                    plugin.getLogger().warning("Table show could not start: " + error.getMessage());
                }
            }
        }
        tables.entrySet()
                .removeIf(
                        entry -> {
                            if (nearby.contains(entry.getKey())
                                    && entry.getValue().table().isValid()) return false;
                            remove(entry.getValue());
                            return true;
                        });
    }

    static Matrix4f wheelPose(double ticks) {
        // Continuous eased rotation, without an angular jump at the loop boundary.
        double angle = ticks * .065 + .8 * Math.sin(ticks * .025);
        return new Matrix4f().translation(-.6875f, .50875f, -.0625f).rotateY((float) angle);
    }

    static Matrix4f ballPose(double ticks) {
        double angle = -ticks * .11;
        return new Matrix4f()
                .translation(
                        -.6875f + (float) Math.sin(angle) * .2875f,
                        .539375f,
                        -.0625f - (float) Math.cos(angle) * .2875f);
    }

    static Matrix4f cardPose(long ticks, int card) {
        // Continuous round trip, staggered by card. No stationary hold on the felt.
        double phase=Math.floorMod(ticks-card*12,96)/96.0;
        double travel=.5-.5*Math.cos(phase*2*Math.PI);
        // Fan outward on the return journey; endpoints and landing stay unchanged.
        double flourish=.085*Math.sin(phase*2*Math.PI)*Math.sin(travel*Math.PI);
        float x=(float)(.875+((card-1)*.32-.875)*travel+flourish*(card-1));
        float z=(float)(-.44+.69*travel);
        float y=(float)(.62-.115*travel+.18*Math.sin(travel*Math.PI)+card*.001);
        return new Matrix4f().translation(x,y,z)
                .rotateY((card-1)*.12f).rotateX((float)(-Math.PI/2)).scale(.28f);
    }

    static Transformation cardTransformation(Transformation parent,long ticks,int card) {
        Matrix4f pose=cardPose(ticks,card);
        return localTransformation(parent, pose.getTranslation(new Vector3f()),
                new Quaternionf().rotateY((card-1)*.12f).rotateX((float)(-Math.PI/2)), .28f);
    }

    static Transformation stackTransformation(Transformation parent, long ticks, int stack, boolean blackjack) {
        double phase = Math.floorMod(ticks-stack*12, 80)/80.0;
        double lift = Math.pow(Math.sin(Math.PI*phase), 2);
        // Squash at contact, stretch during flight; all derivatives are continuous.
        float sy = (float)(.78+.34*lift);
        float sx = (float)(1.10-.14*lift);
        Vector3f anchor = new Vector3f(
                blackjack ? (-6.3f+stack*2-8)/16 : (12f+stack*4-8)/16,
                (blackjack ? 16.2f-8 : 16.05f-8)/16+(float)lift*.16f,
                (blackjack ? .25f-8 : -1.5f-8)/16);
        Transformation pose=localTransformation(parent,anchor,new Quaternionf(),1);
        return new Transformation(pose.getTranslation(),pose.getLeftRotation(),
                new Vector3f(parent.getScale()).mul(sx,sy,sx),pose.getRightRotation());
    }

    private static Transformation localTransformation(Transformation parent, Vector3f offset,
            Quaternionf rotation, float size) {
        // Keep explicit components: decomposing equal scales caused card jitter.
        offset.rotateY((float)Math.PI).rotate(parent.getRightRotation())
                .mul(parent.getScale()).rotate(parent.getLeftRotation()).add(parent.getTranslation());
        Quaternionf right=new Quaternionf(parent.getRightRotation()).rotateY((float)Math.PI)
                .mul(rotation).rotateY((float)-Math.PI);
        return new Transformation(offset,new Quaternionf(parent.getLeftRotation()),
                new Vector3f(parent.getScale()).mul(size),right);
    }

    static Matrix4f renderPose(Matrix4f parent, Matrix4f local) {
        // ItemDisplay applies a 180-degree Y turn inside the item renderer, AFTER
        // its display transform. Conjugate local model-space poses by that turn.
        return new Matrix4f(parent).rotateY((float) Math.PI).mul(local).rotateY((float) -Math.PI);
    }

    private void tick() {
        ticks += 2;
        if (ticks % 40 == 2) discover();
        Iterator<Decoration> iterator = tables.values().iterator();
        while (iterator.hasNext()) {
            Decoration decoration = iterator.next();
            ItemDisplay table = decoration.table();
            if (!table.isValid() || decoration.items().stream().anyMatch(part -> !part.isValid())) {
                remove(decoration);
                iterator.remove();
                continue;
            }
            long age = ticks - decoration.start();
            var t = table.getTransformation();
            Matrix4f parent =
                    new Matrix4f()
                            .translation(t.getTranslation())
                            .rotate(t.getLeftRotation())
                            .scale(t.getScale())
                            .rotate(t.getRightRotation());
            for (int i = 0; i < decoration.items().size(); i++) {
                ItemDisplay part = decoration.items().get(i);
                Matrix4f local =
                        decoration.blackjack()
                                ? new Matrix4f()
                                : (i == 0 ? wheelPose(age) : ballPose(age));
                if (!part.getLocation().equals(table.getLocation()))
                    part.teleport(table.getLocation());
                part.setInterpolationDelay(0);
                boolean chip = decoration.blackjack() ? i >= 3 : i >= 2;
                part.setInterpolationDuration(chip ? 3 : 2);
                int stackStart = decoration.blackjack() ? 3 : 2;
                if (i >= stackStart) {
                    part.setTransformation(stackTransformation(t, age, i-stackStart, decoration.blackjack()));
                } else if (decoration.blackjack()) {
                    part.setTransformation(cardTransformation(t, age, i));
                } else {
                    part.setTransformationMatrix(renderPose(parent, local));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            Decoration decoration = tables.remove(entity.getUniqueId());
            if (decoration != null) remove(decoration);
        }
    }

    @Override
    public void close() {
        task.cancel();
        tables.values().forEach(this::remove);
        tables.clear();
        HandlerList.unregisterAll(this);
    }
}
