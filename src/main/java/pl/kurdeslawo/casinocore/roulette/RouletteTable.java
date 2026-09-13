package pl.kurdeslawo.casinocore.roulette;

import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

public final class RouletteTable {
    private final UUID id;
    private final Location anchor;

    public RouletteTable(UUID id, Location anchor) {
        this.id = Objects.requireNonNull(id, "id");
        this.anchor = Objects.requireNonNull(anchor, "anchor").clone();
    }

    public UUID id() {
        return id;
    }

    public Location anchor() {
        return anchor.clone();
    }
}
