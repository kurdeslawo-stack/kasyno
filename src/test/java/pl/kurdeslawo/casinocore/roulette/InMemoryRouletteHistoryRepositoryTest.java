package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRouletteHistoryRepositoryTest {
    @Test
    void recordsWinsLossesAndCalculatesNetBalance() {
        UUID playerId = UUID.randomUUID();
        InMemoryRouletteHistoryRepository history = new InMemoryRouletteHistoryRepository();
        RouletteRoundRecord win = round(playerId, 7, RouletteBetType.RED, null, 5, 10);
        RouletteRoundRecord loss = round(playerId, 0, RouletteBetType.EVEN, null, 5, 0);

        history.record(win);
        history.record(loss);

        List<RouletteRoundRecord> recent = history.recentRounds(playerId);
        assertEquals(List.of(loss, win), recent);
        assertTrue(win.won());
        assertFalse(loss.won());
        assertEquals(5, win.netChange());
        assertEquals(-5, loss.netChange());

        RoulettePlayerStats stats = history.stats(playerId);
        assertEquals(2, stats.roundsPlayed());
        assertEquals(1, stats.roundsWon());
        assertEquals(1, stats.roundsLost());
        assertEquals(10, stats.totalStaked());
        assertEquals(10, stats.totalPayout());
        assertEquals(0, stats.netBalance());
    }

    @Test
    void retainsOnlyFiveNewestRoundsWhileKeepingAllTimeStats() {
        UUID playerId = UUID.randomUUID();
        InMemoryRouletteHistoryRepository history = new InMemoryRouletteHistoryRepository();
        for (int number = 0; number < 6; number++) {
            history.record(round(playerId, number, RouletteBetType.EXACT_NUMBER, number, 1, 0));
        }

        List<RouletteRoundRecord> recent = history.recentRounds(playerId);
        assertEquals(5, recent.size());
        assertEquals(5, recent.getFirst().result().winningNumber());
        assertEquals(1, recent.getLast().result().winningNumber());
        assertEquals(6, history.stats(playerId).roundsPlayed());
    }

    private static RouletteRoundRecord round(UUID playerId, int result, RouletteBetType type,
                                             Integer selectedNumber, long stake, long payout) {
        return new RouletteRoundRecord(playerId,
            new RouletteResult(result, RouletteEngine.colorOf(result)), type, selectedNumber, stake, payout);
    }
}
