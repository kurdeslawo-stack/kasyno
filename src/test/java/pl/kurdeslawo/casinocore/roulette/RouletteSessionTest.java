package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouletteSessionTest {
    @Test
    void cannotBeginTwoRoundsAtOnce() {
        UUID playerId = UUID.randomUUID();
        RouletteSession session = new RouletteSession(playerId, 5);
        RouletteBet bet = new RouletteBet(playerId, RouletteBetType.RED, 5, null);
        RouletteResult result = new RouletteResult(1, RouletteColor.RED);

        session.selectBet(bet);
        session.beginSpin(bet, result, 10);

        assertThrows(IllegalStateException.class, () -> session.beginSpin(bet, result, 10));
        assertThrows(IllegalStateException.class, () -> session.selectStake(25));
    }

    @Test
    void changingStakeUpdatesTheSelectedBetAndResetKeepsTheLastChoiceAndResult() {
        UUID playerId = UUID.randomUUID();
        RouletteSession session = new RouletteSession(playerId, 1);
        session.selectBet(new RouletteBet(playerId, RouletteBetType.EXACT_NUMBER, 1, 7));

        session.selectStake(25);
        RouletteBet updated = session.selectedBet();
        assertEquals(25, updated.stake());
        assertEquals(7, updated.selectedNumber());

        RouletteResult result = new RouletteResult(7, RouletteColor.RED);
        session.beginSpin(updated, result, 300);
        session.claimSettlement();
        session.completeSettlement(300);
        session.resetForNextRound();

        assertEquals(updated, session.selectedBet());
        assertEquals(result, session.result());
        assertEquals(300, session.payout());
        assertEquals(25, session.resultStake());

        session.selectStake(1);
        assertEquals(25, session.resultStake());
    }
}
