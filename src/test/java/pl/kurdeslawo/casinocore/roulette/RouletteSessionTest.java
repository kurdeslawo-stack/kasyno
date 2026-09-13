package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RouletteSessionTest {
    @Test
    void cannotBeginTwoRoundsAtOnce() {
        UUID playerId = UUID.randomUUID();
        RouletteSession session = new RouletteSession(playerId, 5);
        RouletteBet bet = new RouletteBet(playerId, RouletteBetType.RED, 5, null);
        RouletteResult result = new RouletteResult(1, RouletteColor.RED);

        session.beginSpin(bet, result, 10);

        assertThrows(IllegalStateException.class, () -> session.beginSpin(bet, result, 10));
        assertThrows(IllegalStateException.class, () -> session.selectStake(25));
    }
}
