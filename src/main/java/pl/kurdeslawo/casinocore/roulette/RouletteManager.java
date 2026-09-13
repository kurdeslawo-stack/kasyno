package pl.kurdeslawo.casinocore.roulette;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouletteManager {
    private final Map<UUID, RouletteTable> tables = new ConcurrentHashMap<>();
    private List<Long> allowedStakes;

    public RouletteManager(List<Long> allowedStakes) {
        setAllowedStakes(allowedStakes);
    }

    public List<Long> allowedStakes() {
        return allowedStakes;
    }

    public void setAllowedStakes(List<Long> stakes) {
        if (stakes.isEmpty() || stakes.stream().anyMatch(stake -> stake == null || stake <= 0)) {
            throw new IllegalArgumentException("Roulette stakes must contain positive values");
        }
        this.allowedStakes = stakes.stream().distinct().sorted().toList();
    }

    public void registerTable(RouletteTable table) {
        tables.put(table.id(), table);
    }

    public Optional<RouletteTable> table(UUID id) {
        return Optional.ofNullable(tables.get(id));
    }

    public Collection<RouletteTable> tables() {
        return List.copyOf(tables.values());
    }

    public boolean removeTable(UUID id) {
        return tables.remove(id) != null;
    }
}
