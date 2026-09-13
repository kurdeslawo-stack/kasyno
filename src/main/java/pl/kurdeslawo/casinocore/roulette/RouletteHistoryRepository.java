package pl.kurdeslawo.casinocore.roulette;

import java.util.List;
import java.util.UUID;

public interface RouletteHistoryRepository {
    void record(RouletteRoundRecord round);

    List<RouletteRoundRecord> recentRounds(UUID playerId);

    RoulettePlayerStats stats(UUID playerId);
}
