package pl.kurdeslawo.casinocore.roulette.gui;

import pl.kurdeslawo.casinocore.roulette.RoulettePhase;
import pl.kurdeslawo.casinocore.roulette.RouletteSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouletteReopenTracker {
    private final Map<UUID, RouletteSession> pending = new ConcurrentHashMap<>();

    public boolean markClosedDuringSpin(RouletteSession session) {
        if (session.phase() != RoulettePhase.SPINNING) {
            return false;
        }
        pending.put(session.playerId(), session);
        return true;
    }

    public boolean consume(UUID playerId, RouletteSession session) {
        return pending.remove(playerId, session);
    }

    public void cancel(UUID playerId) {
        pending.remove(playerId);
    }

    public void clear() {
        pending.clear();
    }

    public boolean isPending(UUID playerId) {
        return pending.containsKey(playerId);
    }
}
