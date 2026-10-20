package pl.kurdeslawo.casinocore.blackjack;

import java.util.List;
import java.util.UUID;

public interface BlackjackHistoryRepository {
    void record(UUID playerId, BlackjackRoundRecord round);

    List<BlackjackRoundRecord> recent(UUID playerId);

    BlackjackStats stats(UUID playerId);
}
