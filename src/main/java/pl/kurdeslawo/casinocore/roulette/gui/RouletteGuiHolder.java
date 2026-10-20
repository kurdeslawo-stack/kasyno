package pl.kurdeslawo.casinocore.roulette.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class RouletteGuiHolder implements InventoryHolder {
    private final UUID playerId;
    private Inventory inventory;
    private java.util.List<Long> renderedStakes = java.util.List.of();

    void showStakes(java.util.List<Long> stakes) {
        renderedStakes = java.util.List.copyOf(stakes);
    }

    java.util.List<Long> renderedStakes() {
        return renderedStakes;
    }

    public RouletteGuiHolder(UUID playerId) {
        this.playerId = playerId;
    }

    void attach(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Inventory is already attached");
        }
        this.inventory = inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Inventory has not been attached yet");
        }
        return inventory;
    }
}
