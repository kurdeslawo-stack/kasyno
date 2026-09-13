package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteManagerTest {
    @Test
    void spamCannotWithdrawASecondStakeOrSettleTwice() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(playerId, 100);
        Random alwaysRedOne = new Random(7L) {
            @Override
            public int nextInt(int origin, int bound) {
                return 1;
            }
        };
        RouletteManager manager = new RouletteManager(economy, new RouletteRandom(alwaysRedOne),
            List.of(1L, 5L, 25L), 2, 12);
        manager.selectStake(playerId, 5);
        assertFalse(manager.startRound(playerId).started());
        assertEquals(100, economy.getBalance(playerId));
        manager.selectBet(playerId, RouletteBetType.RED, null);

        assertEquals(100, economy.getBalance(playerId));
        assertTrue(manager.startRound(playerId).started());
        assertFalse(manager.startRound(playerId).started());
        assertEquals(95, economy.getBalance(playerId));
        assertEquals(1, economy.withdrawCalls);

        assertTrue(manager.settle(playerId));
        assertEquals(105, economy.getBalance(playerId));
        long settledBalance = economy.getBalance(playerId);
        assertFalse(manager.settle(playerId));
        assertEquals(settledBalance, economy.getBalance(playerId));
        assertEquals(1, economy.depositCalls);
        assertEquals(1, manager.recentRounds(playerId).size());
    }

    @Test
    void closingAnActiveRoundSettlesOnceAndRemovesTheSession() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(playerId, 100);
        RouletteManager manager = winningRedManager(economy);
        manager.selectStake(playerId, 5);
        manager.selectBet(playerId, RouletteBetType.RED, null);
        assertTrue(manager.startRound(playerId).started());

        manager.closeSession(playerId);
        manager.closeSession(playerId);

        assertEquals(105, economy.getBalance(playerId));
        assertEquals(1, economy.withdrawCalls);
        assertEquals(1, economy.depositCalls);
        assertTrue(manager.session(playerId).isEmpty());
        assertEquals(1, manager.recentRounds(playerId).size());
    }

    @Test
    void failedSettlementRemainsPendingAndCanBeRetriedSafely() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(playerId, 100);
        RouletteManager manager = winningRedManager(economy);
        manager.selectStake(playerId, 5);
        manager.selectBet(playerId, RouletteBetType.RED, null);
        assertTrue(manager.startRound(playerId).started());
        economy.failNextDeposit = true;

        assertThrows(IllegalStateException.class, () -> manager.closeSession(playerId));
        assertTrue(manager.session(playerId).isPresent());
        assertEquals(95, economy.getBalance(playerId));
        assertTrue(manager.recentRounds(playerId).isEmpty());

        manager.closeSession(playerId);
        assertEquals(105, economy.getBalance(playerId));
        assertEquals(2, economy.depositCalls);
        assertTrue(manager.session(playerId).isEmpty());
        assertEquals(1, manager.recentRounds(playerId).size());
    }

    @Test
    void closingWhileSelectingDoesNotCreateHistoryOrWithdrawFunds() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(playerId, 100);
        RouletteManager manager = winningRedManager(economy);
        manager.selectBet(playerId, RouletteBetType.RED, null);

        manager.closeSession(playerId);

        assertEquals(100, economy.getBalance(playerId));
        assertEquals(0, economy.withdrawCalls);
        assertTrue(manager.recentRounds(playerId).isEmpty());
    }

    @Test
    void losingRoundIsRecordedWithoutPayout() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(playerId, 100);
        RouletteManager manager = winningRedManager(economy);
        manager.selectStake(playerId, 5);
        manager.selectBet(playerId, RouletteBetType.BLACK, null);
        assertTrue(manager.startRound(playerId).started());

        assertTrue(manager.settle(playerId));

        RouletteRoundRecord round = manager.recentRounds(playerId).getFirst();
        assertFalse(round.won());
        assertEquals(0, round.payout());
        assertEquals(-5, round.netChange());
        assertEquals(95, economy.getBalance(playerId));
        assertEquals(0, economy.depositCalls);
    }

    private static RouletteManager winningRedManager(FakeEconomy economy) {
        Random alwaysRedOne = new Random(7L) {
            @Override
            public int nextInt(int origin, int bound) {
                return 1;
            }
        };
        return new RouletteManager(economy, new RouletteRandom(alwaysRedOne),
            List.of(1L, 5L, 25L), 2, 12);
    }

    private static final class FakeEconomy implements EconomyProvider {
        private final UUID playerId;
        private long balance;
        private int withdrawCalls;
        private int depositCalls;
        private boolean failNextDeposit;

        private FakeEconomy(UUID playerId, long balance) {
            this.playerId = playerId;
            this.balance = balance;
        }

        @Override public long getBalance(UUID id) { check(id); return balance; }
        @Override public boolean has(UUID id, long amount) { return getBalance(id) >= amount; }
        @Override public boolean withdraw(UUID id, long amount) {
            withdrawCalls++;
            if (!has(id, amount)) return false;
            balance -= amount;
            return true;
        }
        @Override public void deposit(UUID id, long amount) {
            check(id);
            depositCalls++;
            if (failNextDeposit) {
                failNextDeposit = false;
                throw new IllegalStateException("simulated provider outage");
            }
            balance += amount;
        }
        private void check(UUID id) {
            if (!playerId.equals(id)) throw new IllegalArgumentException("Unexpected player");
        }
    }
}
