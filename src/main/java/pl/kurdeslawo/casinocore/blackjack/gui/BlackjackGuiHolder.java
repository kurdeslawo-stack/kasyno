package pl.kurdeslawo.casinocore.blackjack.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class BlackjackGuiHolder implements InventoryHolder {
    private final UUID playerId;
    private Inventory inventory;
    private boolean revealingDealer;

    public BlackjackGuiHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public boolean revealingDealer() {
        return revealingDealer;
    }

    public void setRevealingDealer(boolean revealing) {
        revealingDealer = revealing;
    }
}
