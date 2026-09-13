package pl.kurdeslawo.casinocore.roulette.gui;

import org.junit.jupiter.api.Test;
import pl.kurdeslawo.casinocore.roulette.RouletteBet;
import pl.kurdeslawo.casinocore.roulette.RouletteBetType;
import pl.kurdeslawo.casinocore.roulette.RouletteColor;
import pl.kurdeslawo.casinocore.roulette.RouletteResult;
import pl.kurdeslawo.casinocore.roulette.RouletteSession;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteReopenTrackerTest {
    @Test
    void closedSpinningRoundRemainsMarkedAfterSettlementUntilConsumed() {
        RouletteSession session = spinningSession();
        RouletteReopenTracker tracker = new RouletteReopenTracker();

        assertTrue(tracker.markClosedDuringSpin(session));
        session.claimSettlement();
        session.completeSettlement(10);

        assertTrue(tracker.isPending(session.playerId()));
        assertTrue(tracker.consume(session.playerId(), session));
        assertFalse(tracker.consume(session.playerId(), session));
    }

    @Test
    void selectingSessionIsNotMarkedForReopen() {
        RouletteSession session = new RouletteSession(UUID.randomUUID(), 5);
        RouletteReopenTracker tracker = new RouletteReopenTracker();

        assertFalse(tracker.markClosedDuringSpin(session));
        assertFalse(tracker.isPending(session.playerId()));
    }

    @Test
    void quitCancelsPendingReopen() {
        RouletteSession session = spinningSession();
        RouletteReopenTracker tracker = new RouletteReopenTracker();
        tracker.markClosedDuringSpin(session);

        tracker.cancel(session.playerId());

        assertFalse(tracker.isPending(session.playerId()));
    }

    @Test
    void pluginDisableClearsAllPendingReopens() {
        RouletteSession first = spinningSession();
        RouletteSession second = spinningSession();
        RouletteReopenTracker tracker = new RouletteReopenTracker();
        tracker.markClosedDuringSpin(first);
        tracker.markClosedDuringSpin(second);

        tracker.clear();

        assertFalse(tracker.isPending(first.playerId()));
        assertFalse(tracker.isPending(second.playerId()));
    }

    private static RouletteSession spinningSession() {
        UUID playerId = UUID.randomUUID();
        RouletteSession session = new RouletteSession(playerId, 5);
        RouletteBet bet = new RouletteBet(playerId, RouletteBetType.RED, 5, null);
        session.selectBet(bet);
        session.beginSpin(bet, new RouletteResult(1, RouletteColor.RED), 10);
        return session;
    }
}
