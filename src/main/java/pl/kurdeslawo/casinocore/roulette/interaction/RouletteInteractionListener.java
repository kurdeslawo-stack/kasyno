package pl.kurdeslawo.casinocore.roulette.interaction;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import pl.kurdeslawo.casinocore.roulette.gui.RouletteGui;

public final class RouletteInteractionListener implements Listener {
    private final RouletteTableInteractionManager interactions;
    private final RouletteGui gui;

    public RouletteInteractionListener(RouletteTableInteractionManager interactions, RouletteGui gui) {
        this.interactions = interactions;
        this.gui = gui;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !interactions.isRouletteInteraction(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        gui.open(event.getPlayer());
    }
}
