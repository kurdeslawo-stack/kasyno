package pl.kurdeslawo.casinocore.roulette;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.RoundJournal;

class RouletteRecoveryTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final Account economy = new Account();

    @Test void crashPreservesOriginalDrawAndPaysOnlyOnce() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            start(manager(journal, 1));
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            RouletteManager recovered = manager(journal, 2);
            recover(recovered);
            recover(recovered);
            assertEquals(105, economy.balance);
            assertEquals(1, economy.deposits);
            assertEquals(1, recovered.recentRounds(player).size());
            assertEquals(1, recovered.recentRounds(player).getFirst().result().winningNumber());
        }
    }

    @Test void creditedButUnknownPayoutCannotBeRetriedBeforeReconciliation() {
        UUID round;
        try (RoundJournal journal = new RoundJournal(directory)) {
            RouletteManager manager = manager(journal, 1);
            start(manager);
            round = manager.session(player).orElseThrow().roundId();
            economy.throwAfterCredit = true;
            assertThrows(IllegalStateException.class, () -> manager.settle(player));
            assertThrows(IllegalStateException.class, () -> manager.settle(player));
            assertEquals(105, economy.balance);
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            RouletteManager manager = manager(journal, 2);
            journal.resolve(round, "payout", true, "admin: external payout confirmed");
            manager.recoverRound(round);
            assertEquals(105, economy.balance);
            assertEquals(1, economy.deposits);
            assertEquals(1, manager.recentRounds(player).size());
        }
    }

    @Test void unknownStakeCannotBeRechargedOrPaid() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            RouletteManager manager = manager(journal, 1);
            economy.throwAfterDebit = true;
            assertThrows(IllegalStateException.class, () -> start(manager));
            assertFalse(manager.startRound(player).started());
            assertThrows(IllegalStateException.class, () -> manager.settle(player));
            assertEquals(95, economy.balance);
            assertEquals(1, economy.withdrawals);
            assertEquals(0, economy.deposits);
        }
    }

    @Test void completedHistorySurvivesMultipleRestarts() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            RouletteManager manager = manager(journal, 1);
            start(manager);
            manager.settle(player);
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            RouletteManager manager = manager(journal, 2);
            recover(manager);
            assertEquals(1, manager.playerStats(player).roundsPlayed());
            assertEquals(1, manager.recentRounds(player).size());
            assertEquals(1, economy.deposits);
        }
    }

    private void start(RouletteManager manager) {
        manager.selectBet(player, RouletteBetType.RED, null);
        assertTrue(manager.startRound(player).started());
    }
    private void recover(RouletteManager manager) {
        List<String> errors = new ArrayList<>();
        manager.recoverAll(errors::add);
        assertEquals(List.of(), errors);
    }
    private RouletteManager manager(RoundJournal journal, int winning) {
        Random random = new Random(1) {
            public int nextInt(int origin, int bound) { return winning; }
        };
        return new RouletteManager(economy, new RouletteRandom(random), List.of(5L), 2, 12,
            new InMemoryRouletteHistoryRepository(), journal);
    }
    private static final class Account implements EconomyProvider {
        long balance = 100;
        int withdrawals, deposits;
        boolean throwAfterDebit, throwAfterCredit;
        public long getBalance(UUID id) { return balance; }
        public boolean has(UUID id, long amount) { return balance >= amount; }
        public boolean withdraw(UUID id, long amount) {
            balance -= amount; withdrawals++;
            if (throwAfterDebit) throw new IllegalStateException("Response lost after debit");
            return true;
        }
        public void deposit(UUID id, long amount) {
            balance += amount; deposits++;
            if (throwAfterCredit) throw new IllegalStateException("Response lost after credit");
        }
    }
}
