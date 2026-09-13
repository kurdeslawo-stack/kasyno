package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteEngineTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private final RouletteEngine engine = new RouletteEngine(2, 12);

    @Test
    void redAndBlackUseConfiguredNumberSets() {
        assertTrue(wins(RouletteBetType.RED, null, 1));
        assertTrue(wins(RouletteBetType.RED, null, 12));
        assertFalse(wins(RouletteBetType.RED, null, 2));
        assertTrue(wins(RouletteBetType.BLACK, null, 2));
        assertTrue(wins(RouletteBetType.BLACK, null, 11));
        assertFalse(wins(RouletteBetType.BLACK, null, 3));
    }

    @Test
    void evenOddLowAndHighRespectBoundaries() {
        assertTrue(wins(RouletteBetType.EVEN, null, 2));
        assertFalse(wins(RouletteBetType.EVEN, null, 3));
        assertTrue(wins(RouletteBetType.ODD, null, 3));
        assertFalse(wins(RouletteBetType.ODD, null, 4));
        assertTrue(wins(RouletteBetType.LOW, null, 1));
        assertTrue(wins(RouletteBetType.LOW, null, 6));
        assertFalse(wins(RouletteBetType.LOW, null, 7));
        assertTrue(wins(RouletteBetType.HIGH, null, 7));
        assertTrue(wins(RouletteBetType.HIGH, null, 12));
        assertFalse(wins(RouletteBetType.HIGH, null, 6));
    }

    @Test
    void exactNumberWinsOnlyOnSelectedNumber() {
        assertTrue(wins(RouletteBetType.EXACT_NUMBER, 8, 8));
        assertFalse(wins(RouletteBetType.EXACT_NUMBER, 8, 9));
    }

    @Test
    void zeroLosesEveryOutsideBet() {
        for (RouletteBetType type : RouletteBetType.values()) {
            if (type != RouletteBetType.EXACT_NUMBER) {
                assertFalse(wins(type, null, 0), type.name());
            }
        }
    }

    @Test
    void payoutsAreTotalReturnsAndDoNotAddStakeTwice() {
        RouletteResult red = result(1);
        RouletteBet redBet = new RouletteBet(PLAYER, RouletteBetType.RED, 5, null);
        RouletteBet exactBet = new RouletteBet(PLAYER, RouletteBetType.EXACT_NUMBER, 5, 1);

        assertEquals(10, engine.totalReturn(redBet, red));
        assertEquals(60, engine.totalReturn(exactBet, red));
    }

    private boolean wins(RouletteBetType type, Integer selected, int result) {
        return engine.wins(new RouletteBet(PLAYER, type, 1, selected), result(result));
    }

    private static RouletteResult result(int number) {
        return new RouletteResult(number, RouletteEngine.colorOf(number));
    }
}
