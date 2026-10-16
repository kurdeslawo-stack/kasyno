package pl.kurdeslawo.casinocore.economy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundJournalTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final UUID round = UUID.randomUUID();

    @Test void unknownDebitSurvivesRestartAndNeverCallsProviderAgain() {
        TestEconomy economy = new TestEconomy();
        try (RoundJournal journal = create()) {
            economy.throwAfterDebit = true;
            assertThrows(IllegalStateException.class, () -> journal.withdraw(round, "stake", 5, economy));
            assertEquals(95, economy.balance);
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            economy.throwAfterDebit = false;
            assertTrue(journal.needsReview(player));
            assertThrows(IllegalStateException.class, () -> journal.withdraw(round, "stake", 5, economy));
            assertEquals(1, economy.debits);
            journal.resolve(round, "stake", true, "admin: transaction found in provider log");
            assertTrue(journal.withdraw(round, "stake", 5, economy));
            journal.requireFunded(round);
            assertEquals(1, economy.debits);
        }
    }

    @Test void unknownCreditSurvivesRestartAndReconciliationDoesNotPayTwice() {
        TestEconomy economy = new TestEconomy();
        try (RoundJournal journal = create()) {
            journal.withdraw(round, "stake", 5, economy);
            economy.throwAfterCredit = true;
            assertThrows(IllegalStateException.class, () -> journal.deposit(round, 10, economy));
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            economy.throwAfterCredit = false;
            assertThrows(IllegalStateException.class, () -> journal.deposit(round, 10, economy));
            journal.resolve(round, "payout", true, "admin: payout confirmed");
            journal.deposit(round, 10, economy);
            journal.complete(round, Map.of("payout", "10"));
            assertEquals(105, economy.balance);
            assertEquals(1, economy.credits);
        }
    }

    @Test void crashDuringExternalCallLeavesDurableIntent() {
        TestEconomy economy = new TestEconomy();
        try (RoundJournal journal = create()) {
            economy.haltAfterDebit = true;
            assertThrows(AssertionError.class, () -> journal.withdraw(round, "stake", 5, economy));
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            assertEquals(RoundJournal.State.INTENT, journal.get(round).transactions().get("stake").state());
            assertThrows(IllegalStateException.class, () -> journal.withdraw(round, "stake", 5, economy));
            assertEquals(1, economy.debits);
        }
    }

    @Test void explicitlyRejectedPayoutCanRetry() {
        TestEconomy economy = new TestEconomy();
        try (RoundJournal journal = create()) {
            journal.withdraw(round, "stake", 5, economy);
            economy.rejectCredit = true;
            assertThrows(EconomyTransactionException.class, () -> journal.deposit(round, 10, economy));
            assertFalse(journal.needsReview(player));
            economy.rejectCredit = false;
            journal.deposit(round, 10, economy);
            assertEquals(105, economy.balance);
            assertEquals(1, economy.credits);
        }
    }

    @Test void failureBeforeIntentIsDurableNeverCallsEconomy() throws Exception {
        TestEconomy economy = new TestEconomy();
        try (RoundJournal journal = create()) {
            Files.createDirectory(directory.resolve(round + ".tmp"));
            assertThrows(IllegalStateException.class, () -> journal.withdraw(round, "stake", 5, economy));
            assertEquals(0, economy.debits);
            assertThrows(IllegalStateException.class, () -> journal.entries(null));
        }
    }

    @Test void corruptJournalFailsClosed() throws Exception {
        Files.writeString(directory.resolve(round + ".round"), "not a round");
        assertThrows(IllegalStateException.class, () -> new RoundJournal(directory));
    }

    @Test void alteredButParsableRecordFailsChecksum() throws Exception {
        try (RoundJournal journal = create()) { /* persist a valid record */ }
        Path record = directory.resolve(round + ".round");
        String original = Files.readString(record);
        String changed = original.replace("data.payout=10", "data.payout=20");
        assertNotEquals(original, changed);
        Files.writeString(record, changed);
        assertThrows(IllegalStateException.class, () -> new RoundJournal(directory));
    }

    @Test void twoProcessesCannotOpenSameJournal() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            assertThrows(IllegalStateException.class, () -> new RoundJournal(directory));
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            assertTrue(journal.entries(null).isEmpty());
        }
    }

    @Test void providerIdentityCannotBeSilentlyChanged() {
        try (RoundJournal journal = new RoundJournal(directory)) { journal.bindProvider("vault/providerA"); }
        try (RoundJournal journal = new RoundJournal(directory)) {
            assertThrows(IllegalStateException.class, () -> journal.bindProvider("vault/providerB"));
        }
    }

    @Test void unresolvedPlayerCannotStartAnotherGame() {
        try (RoundJournal journal = create()) {
            assertThrows(IllegalStateException.class,
                () -> journal.begin(UUID.randomUUID(), player, "roulette", Map.of()));
        }
    }

    @Test void completedOrCancelledRoundReleasesOnlyItsOwnersSlot() {
        UUID other = UUID.randomUUID();
        try (RoundJournal journal = create()) {
            UUID otherRound = UUID.randomUUID();
            journal.begin(otherRound, other, "roulette", Map.of());
            journal.cancelUnfunded(round);
            journal.begin(UUID.randomUUID(), player, "blackjack", Map.of());
            assertThrows(IllegalStateException.class,
                () -> journal.begin(UUID.randomUUID(), other, "blackjack", Map.of()));
        }
    }

    @Test void resolvedNotAppliedPayoutCanBeTransferredOnce() {
        TestEconomy economy = new TestEconomy();
        try (RoundJournal journal = create()) {
            journal.withdraw(round, "stake", 5, economy);
            economy.throwBeforeCredit = true;
            assertThrows(IllegalStateException.class, () -> journal.deposit(round, 10, economy));
            journal.resolve(round, "payout", false, "admin: provider logs confirm no transfer");
            economy.throwBeforeCredit = false;
            journal.deposit(round, 10, economy);
            journal.deposit(round, 10, economy);
            assertTrue(journal.get(round).transactions().get("payout").detail().contains("provider logs confirm no transfer"));
            assertEquals(105, economy.balance);
            assertEquals(1, economy.credits);
        }
    }

    private RoundJournal create() {
        RoundJournal journal = new RoundJournal(directory);
        journal.begin(round, player, "blackjack", Map.of("payout", "10"));
        return journal;
    }

    private static final class TestEconomy implements EconomyProvider {
        long balance = 100;
        int debits, credits;
        boolean throwAfterDebit, throwAfterCredit, haltAfterDebit, rejectCredit, throwBeforeCredit;
        public long getBalance(UUID id) { return balance; }
        public boolean has(UUID id, long amount) { return balance >= amount; }
        public boolean withdraw(UUID id, long amount) {
            balance -= amount; debits++;
            if (haltAfterDebit) throw new AssertionError("process lost before response");
            if (throwAfterDebit) throw new IllegalStateException("response lost");
            return true;
        }
        public void deposit(UUID id, long amount) {
            if (rejectCredit) throw EconomyTransactionException.rejected("explicit provider rejection");
            if (throwBeforeCredit) throw new IllegalStateException("no response");
            balance += amount; credits++;
            if (throwAfterCredit) throw new IllegalStateException("response lost");
        }
    }
}
