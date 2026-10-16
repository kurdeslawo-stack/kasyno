package pl.kurdeslawo.casinocore.blackjack;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;
import pl.kurdeslawo.casinocore.economy.RoundJournal;

class BlackjackRecoveryTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final Account economy = new Account();

    @Test void fundedRoundSurvivesAbruptRestartAndHistoryIsRestoredOnce() {
        try (RoundJournal journal = new RoundJournal(directory)) { manager(journal).start(player); }
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager recovered = manager(journal);
            recover(recovered);
            recover(recovered);
            assertEquals(105, economy.balance);
            assertEquals(1, recovered.recent(player).size());
            assertEquals(1, recovered.stats(player).played());
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager recovered = manager(journal);
            recover(recovered);
            assertEquals(1, recovered.recent(player).size());
            assertEquals(1, economy.deposits);
            assertEquals(1, economy.withdrawals);
        }
    }

    @Test void creditThenThrowIsBlockedUntilEvidenceConfirmsApplied() {
        UUID round;
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = manager(journal);
            manager.start(player);
            round = manager.session(player).orElseThrow().roundId();
            economy.throwAfterCredit = true;
            assertThrows(IllegalStateException.class, () -> manager.stand(player));
            economy.throwAfterCredit = false;
            assertThrows(IllegalStateException.class, () -> manager.stand(player));
            assertThrows(IllegalStateException.class, () -> manager.closeSession(player));
            assertEquals(105, economy.balance);
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = manager(journal);
            List<String> failures = new ArrayList<>();
            manager.recoverAll(failures::add);
            assertEquals(1, failures.size());
            journal.resolve(round, "payout", true, "admin: external transaction found");
            manager.recoverRound(round);
            assertEquals(105, economy.balance);
            assertEquals(1, economy.deposits);
            assertEquals(1, manager.recent(player).size());
        }
    }

    @Test void debitThenThrowCannotStartOrWithdrawAgain() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = manager(journal);
            economy.throwAfterDebit = true;
            assertThrows(IllegalStateException.class, () -> manager.start(player));
            economy.throwAfterDebit = false;
            assertFalse(manager.start(player).started());
            assertThrows(IllegalStateException.class, () -> manager.stand(player));
            UUID round = manager.session(player).orElseThrow().roundId();
            journal.resolve(round, "stake", true, "admin: stake confirmed");
            manager.recoverRound(round);
            assertEquals(105, economy.balance);
            assertEquals(1, economy.withdrawals);
            assertEquals(1, manager.recent(player).size());
        }
    }

    @Test void uncommittedDebitCanBeCancelledWithoutRefundOrPayout() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = manager(journal);
            economy.throwBeforeDebit = true;
            assertThrows(IllegalStateException.class, () -> manager.start(player));
            UUID round = manager.session(player).orElseThrow().roundId();
            journal.resolve(round, "stake", false, "admin: provider confirms no debit");
            manager.recoverRound(round);
            assertTrue(journal.get(round).cancelled());
            assertEquals(100, economy.balance);
            assertEquals(0, economy.deposits);
            economy.throwBeforeDebit = false;
            assertTrue(manager.start(player).started());
            assertEquals(95, economy.balance);
        }
    }

    @Test void doublePaidBeforeSnapshotIsRecoveredWithOneCardAndNoSecondDebit() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = doubleManager(journal);
            manager.start(player);
            manager.finishDeal(player);
            // Crash window: double debit confirmed, hand mutation not yet checkpointed.
            journal.withdraw(manager.session(player).orElseThrow().roundId(), "double", 5, economy);
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = doubleManager(journal);
            recover(manager);
            assertEquals(110, economy.balance);
            assertEquals(2, economy.withdrawals);
            assertEquals(1, economy.deposits);
            assertEquals(10, manager.recent(player).getFirst().wager());
            assertEquals(21, manager.recent(player).getFirst().playerValue());
        }
    }

    @Test void unknownDoubleNeverPaysUntilReconciled() {
        UUID round;
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = doubleManager(journal);
            manager.start(player);
            manager.finishDeal(player);
            round = manager.session(player).orElseThrow().roundId();
            economy.throwAfterDebit = true;
            assertThrows(IllegalStateException.class, () -> manager.doubleDown(player));
            assertThrows(IllegalStateException.class, () -> manager.closeSession(player));
        }
        economy.throwAfterDebit = false;
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = doubleManager(journal);
            journal.resolve(round, "double", true, "admin: second debit confirmed");
            manager.recoverRound(round);
            assertEquals(110, economy.balance);
            assertEquals(2, economy.withdrawals);
            assertEquals(1, economy.deposits);
        }
    }

    @Test void payoutConfirmedBeforeCompletionIsNotPaidAgainAfterRestart() {
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = manager(journal);
            manager.start(player);
            manager.finishDeal(player);
            BlackjackSession session = manager.session(player).orElseThrow();
            session.drawDealerTo(session.roundEngine());
            session.prepareSettlement();
            journal.checkpoint(session.roundId(), session.snapshot());
            journal.deposit(session.roundId(), session.payout(), economy);
        }
        try (RoundJournal journal = new RoundJournal(directory)) {
            BlackjackManager manager = manager(journal);
            recover(manager);
            assertEquals(105, economy.balance);
            assertEquals(1, economy.deposits);
            assertEquals(1, manager.recent(player).size());
        }
    }

    private void recover(BlackjackManager manager) {
        List<String> errors = new ArrayList<>();
        manager.recoverAll(errors::add);
        assertEquals(List.of(), errors);
    }

    private BlackjackManager manager(RoundJournal journal) {
        return manager(journal, BlackjackRank.TEN, BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.EIGHT);
    }

    private BlackjackManager doubleManager(RoundJournal journal) {
        return manager(journal, BlackjackRank.FIVE, BlackjackRank.TEN, BlackjackRank.SIX,
            BlackjackRank.SEVEN, BlackjackRank.TEN);
    }

    private BlackjackManager manager(RoundJournal journal, BlackjackRank... ranks) {
        return new BlackjackManager(economy, List.of(5L), 3, 2, false,
            () -> new BlackjackDeck(java.util.Arrays.stream(ranks)
                .map(r -> new BlackjackCard(r, BlackjackSuit.SPADES)).toList()),
            new InMemoryBlackjackHistoryRepository(), journal);
    }

    private static final class Account implements EconomyProvider {
        long balance = 100;
        int withdrawals, deposits;
        boolean throwBeforeDebit, throwAfterDebit, throwAfterCredit;
        public long getBalance(UUID id) { return balance; }
        public boolean has(UUID id, long amount) { return balance >= amount; }
        public boolean withdraw(UUID id, long amount) {
            if (throwBeforeDebit) throw new IllegalStateException("response missing");
            if (balance < amount) return false;
            balance -= amount; withdrawals++;
            if (throwAfterDebit) throw new IllegalStateException("response missing after debit");
            return true;
        }
        public void deposit(UUID id, long amount) {
            balance += amount; deposits++;
            if (throwAfterCredit) throw new IllegalStateException("response missing after credit");
        }
    }
}
