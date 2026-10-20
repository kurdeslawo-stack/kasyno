package pl.kurdeslawo.casinocore.blackjack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBlackjackHistoryRepository implements BlackjackHistoryRepository {
    private static final int HISTORY_LIMIT = 5;

    private final Map<UUID, PlayerHistory> histories = new ConcurrentHashMap<>();

    @Override
    public void record(UUID playerId, BlackjackRoundRecord round) {
        PlayerHistory history = histories.computeIfAbsent(playerId, x -> new PlayerHistory());
        synchronized (history) {
            history.recent.addFirst(round);
            while (history.recent.size() > HISTORY_LIMIT) history.recent.removeLast();
            history.played = saturatedAdd(history.played, 1);
            if (round.outcome() == BlackjackOutcome.LOSS)
                history.losses = saturatedAdd(history.losses, 1);
            else if (round.outcome() == BlackjackOutcome.PUSH)
                history.pushes = saturatedAdd(history.pushes, 1);
            else {
                history.wins = saturatedAdd(history.wins, 1);
                if (round.outcome() == BlackjackOutcome.BLACKJACK)
                    history.blackjacks = saturatedAdd(history.blackjacks, 1);
            }
            history.staked = saturatedAdd(history.staked, round.wager());
            history.paid = saturatedAdd(history.paid, round.payout());
        }
    }

    @Override
    public List<BlackjackRoundRecord> recent(UUID playerId) {
        PlayerHistory history = histories.get(playerId);
        if (history == null) return List.of();
        synchronized (history) {
            return List.copyOf(history.recent);
        }
    }

    @Override
    public BlackjackStats stats(UUID playerId) {
        PlayerHistory history = histories.get(playerId);
        if (history == null) return BlackjackStats.EMPTY;
        synchronized (history) {
            return new BlackjackStats(
                    history.played,
                    history.wins,
                    history.losses,
                    history.pushes,
                    history.blackjacks,
                    history.staked,
                    history.paid);
        }
    }

    private static long saturatedAdd(long current, long amount) {
        return Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
    }

    private static final class PlayerHistory {
        final Deque<BlackjackRoundRecord> recent = new ArrayDeque<>();
        long played, wins, losses, pushes, blackjacks, staked, paid;
    }
}
