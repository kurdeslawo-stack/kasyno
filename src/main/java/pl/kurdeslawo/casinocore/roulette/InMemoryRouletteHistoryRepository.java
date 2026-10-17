package pl.kurdeslawo.casinocore.roulette;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRouletteHistoryRepository implements RouletteHistoryRepository {
    public static final int HISTORY_LIMIT = 5;
    private final Map<UUID, PlayerHistory> histories = new ConcurrentHashMap<>();

    @Override
    public void record(RouletteRoundRecord round) {
        PlayerHistory history =
                histories.computeIfAbsent(round.playerId(), ignored -> new PlayerHistory());
        synchronized (history) {
            history.recent.addFirst(round);
            while (history.recent.size() > HISTORY_LIMIT) {
                history.recent.removeLast();
            }
            history.roundsPlayed = saturatedIncrement(history.roundsPlayed);
            if (round.won()) {
                history.roundsWon = saturatedIncrement(history.roundsWon);
            } else {
                history.roundsLost = saturatedIncrement(history.roundsLost);
            }
            history.totalStaked = saturatedAdd(history.totalStaked, round.stake());
            history.totalPayout = saturatedAdd(history.totalPayout, round.payout());
        }
    }

    @Override
    public List<RouletteRoundRecord> recentRounds(UUID playerId) {
        PlayerHistory history = histories.get(playerId);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return List.copyOf(new ArrayList<>(history.recent));
        }
    }

    @Override
    public RoulettePlayerStats stats(UUID playerId) {
        PlayerHistory history = histories.get(playerId);
        if (history == null) {
            return RoulettePlayerStats.EMPTY;
        }
        synchronized (history) {
            return new RoulettePlayerStats(
                    history.roundsPlayed,
                    history.roundsWon,
                    history.roundsLost,
                    history.totalStaked,
                    history.totalPayout);
        }
    }

    private static long saturatedIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private static long saturatedAdd(long current, long amount) {
        return Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
    }

    private static final class PlayerHistory {
        private final Deque<RouletteRoundRecord> recent = new ArrayDeque<>(HISTORY_LIMIT);
        private long roundsPlayed;
        private long roundsWon;
        private long roundsLost;
        private long totalStaked;
        private long totalPayout;
    }
}
