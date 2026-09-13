package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertTrue(manager.startRound(playerId, RouletteBetType.RED, null).started());
        assertFalse(manager.startRound(playerId, RouletteBetType.BLACK, null).started());
        assertEquals(95, economy.getBalance(playerId));

        assertTrue(manager.settle(playerId));
        assertEquals(105, economy.getBalance(playerId));
        long settledBalance = economy.getBalance(playerId);
        assertFalse(manager.settle(playerId));
        assertEquals(settledBalance, economy.getBalance(playerId));
    }

    private static final class FakeEconomy implements EconomyProvider {
        private final UUID playerId;
        private long balance;

        private FakeEconomy(UUID playerId, long balance) {
            this.playerId = playerId;
            this.balance = balance;
        }

        @Override public long getBalance(UUID id) { check(id); return balance; }
        @Override public boolean has(UUID id, long amount) { return getBalance(id) >= amount; }
        @Override public boolean withdraw(UUID id, long amount) {
            if (!has(id, amount)) return false;
            balance -= amount;
            return true;
        }
        @Override public void deposit(UUID id, long amount) { check(id); balance += amount; }
        private void check(UUID id) {
            if (!playerId.equals(id)) throw new IllegalArgumentException("Unexpected player");
        }
    }
}
