package pl.kurdeslawo.casinocore.blackjack.gui;
import org.bukkit.inventory.*; import java.util.UUID;
public final class BlackjackGuiHolder implements InventoryHolder {
    private final UUID id;
    private Inventory inventory;
    private boolean revealingDealer;
    public BlackjackGuiHolder(UUID id) { this.id = id; }
    public UUID playerId() { return id; }
    void attach(Inventory inventory) { this.inventory = inventory; }
    public Inventory getInventory() { return inventory; }
    public boolean revealingDealer() { return revealingDealer; }
    public void setRevealingDealer(boolean revealing) { revealingDealer = revealing; }
}
