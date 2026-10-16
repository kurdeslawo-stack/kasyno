package pl.kurdeslawo.casinocore.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.kurdeslawo.casinocore.economy.EconomyProvider;

class BlackjackManagerTest {
    @Test
    void doubleDownWithdrawsTwiceAndPaysOnce() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.FIVE, BlackjackRank.TEN, BlackjackRank.SIX,
                BlackjackRank.SEVEN, BlackjackRank.TEN));

        manager.selectStake(playerId, 5);
        assertTrue(manager.start(playerId).started());
        manager.finishDeal(playerId);
        assertTrue(manager.doubleDown(playerId));

        assertEquals(110, economy.balance);
        assertEquals(2, economy.withdraws);
        assertEquals(1, economy.deposits);
        assertFalse(manager.doubleDown(playerId));
        assertEquals(1, manager.recent(playerId).size());
    }

    @Test
    void insufficientDoubleDownDoesNotChangeTheRound() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(5);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.FIVE, BlackjackRank.TEN, BlackjackRank.SIX, BlackjackRank.SEVEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.finishDeal(playerId);
        assertFalse(manager.doubleDown(playerId));

        BlackjackSession session = manager.session(playerId).orElseThrow();
        assertEquals(5, session.wager());
        assertEquals(2, session.playerHand().cards().size());
        assertEquals(1, economy.withdraws);
        assertEquals(0, economy.deposits);
    }

    @Test
    void settlementIsIdempotent() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.NINE,
                BlackjackRank.SEVEN, BlackjackRank.TEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.finishDeal(playerId);
        manager.stand(playerId);
        manager.stand(playerId);
        manager.closeSession(playerId);

        assertEquals(105, economy.balance);
        assertEquals(1, economy.withdraws);
        assertEquals(1, economy.deposits);
        assertEquals(1, manager.recent(playerId).size());
    }

    @Test
    void closeDuringDealSafelyStandsAndSettles() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.NINE,
                BlackjackRank.SEVEN, BlackjackRank.TEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.closeSession(playerId);

        assertTrue(manager.session(playerId).isEmpty());
        assertEquals(105, economy.balance);
        assertEquals(1, manager.recent(playerId).size());
    }

    @Test
    void closeBeforeStartDoesNothing() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy, deck(BlackjackRank.TEN));

        manager.openSession(playerId);
        manager.closeSession(playerId);

        assertEquals(100, economy.balance);
        assertEquals(0, economy.withdraws);
        assertTrue(manager.recent(playerId).isEmpty());
    }

    @Test
    void quitClosePathSettlesAnActivePlayerTurnOnce() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.NINE,
                BlackjackRank.SEVEN, BlackjackRank.TEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.finishDeal(playerId);
        manager.closeSession(playerId);
        manager.closeSession(playerId);

        assertEquals(1, economy.withdraws);
        assertEquals(1, economy.deposits);
        assertEquals(1, manager.recent(playerId).size());
    }

    @Test
    void disableClosePathSettlesEveryActiveSession() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = new BlackjackManager(
            economy, List.of(5L), 3, 2, false,
            sequentialDecks(
                deck(BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.NINE,
                    BlackjackRank.SEVEN, BlackjackRank.TEN),
                deck(BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.NINE,
                    BlackjackRank.SEVEN, BlackjackRank.TEN)),
            new InMemoryBlackjackHistoryRepository());

        manager.start(first);
        manager.start(second);
        manager.closeAll();

        assertTrue(manager.session(first).isEmpty());
        assertTrue(manager.session(second).isEmpty());
        assertEquals(2, economy.withdraws);
        assertEquals(2, economy.deposits);
        assertEquals(1, manager.recent(first).size());
        assertEquals(1, manager.recent(second).size());
    }

    @Test
    void naturalBlackjackUsesThreeToTwoPayout() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.ACE, BlackjackRank.NINE, BlackjackRank.KING, BlackjackRank.SEVEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.finishDeal(playerId);

        assertEquals(107, economy.balance);
        assertEquals(BlackjackOutcome.BLACKJACK, manager.recent(playerId).getFirst().outcome());
    }

    @Test
    void twoNaturalBlackjacksProducePushAndRefund() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.ACE, BlackjackRank.ACE, BlackjackRank.KING, BlackjackRank.QUEEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.finishDeal(playerId);

        assertEquals(100, economy.balance);
        assertEquals(BlackjackOutcome.PUSH, manager.recent(playerId).getFirst().outcome());
    }

    @Test
    void activeRoundKeepsRulesCapturedBeforeReload() {
        UUID playerId = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.ACE, BlackjackRank.NINE, BlackjackRank.KING, BlackjackRank.SEVEN));

        manager.selectStake(playerId, 5);
        manager.start(playerId);
        manager.updateConfiguration(List.of(5L), 1, 1, true);
        manager.finishDeal(playerId);

        assertEquals(107, economy.balance);
    }

    @Test
    void rejectedPayoutFreezesTheHandAndRetriesOnlyTheOriginalSettlement() {
        UUID id = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.TEN, BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.EIGHT,
                BlackjackRank.ACE));
        manager.selectStake(id, 5);
        manager.start(id);
        manager.finishDeal(id);
        economy.rejectDeposit = true;
        assertThrows(pl.kurdeslawo.casinocore.economy.EconomyTransactionException.class, () -> manager.stand(id));
        BlackjackSession session = manager.session(id).orElseThrow();
        assertEquals(BlackjackPhase.SETTLING, session.phase());
        assertEquals(BlackjackOutcome.WIN, session.outcome());
        assertEquals(10, session.payout());
        assertThrows(IllegalStateException.class, () -> manager.hit(id));
        assertFalse(manager.doubleDown(id));
        assertFalse(manager.start(id).started());
        assertTrue(manager.recent(id).isEmpty());
        assertEquals(2, session.playerHand().cards().size());
        economy.rejectDeposit = false;
        manager.stand(id);
        manager.stand(id);
        manager.closeSession(id);
        assertEquals(105, economy.balance);
        assertEquals(1, economy.withdraws);
        assertEquals(1, economy.deposits);
        assertEquals(1, manager.recent(id).size());
    }

    @Test
    void removedStakeCannotStartAfterReload() {
        UUID id = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy, deck(BlackjackRank.TEN));
        manager.selectStake(id, 25);
        manager.updateConfiguration(List.of(1L, 5L), 3, 2, false);
        assertFalse(manager.start(id).started());
        assertEquals(0, economy.withdraws);
        assertTrue(manager.recent(id).isEmpty());
    }

    @Test
    void failedDoublePayoutCannotWithdrawOrDrawAgain() {
        UUID id = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.FIVE, BlackjackRank.TEN, BlackjackRank.SIX, BlackjackRank.SEVEN,
                BlackjackRank.TEN, BlackjackRank.ACE));
        manager.selectStake(id, 5);
        manager.start(id);
        manager.finishDeal(id);
        economy.rejectDeposit = true;
        assertThrows(pl.kurdeslawo.casinocore.economy.EconomyTransactionException.class, () -> manager.doubleDown(id));
        assertFalse(manager.doubleDown(id));
        assertThrows(IllegalStateException.class, () -> manager.hit(id));
        BlackjackSession session = manager.session(id).orElseThrow();
        assertEquals(BlackjackPhase.SETTLING, session.phase());
        assertEquals(10, session.wager());
        assertEquals(3, session.playerHand().cards().size());
        assertEquals(2, economy.withdraws);
        economy.rejectDeposit = false;
        manager.closeSession(id);
        manager.closeSession(id);
        assertEquals(110, economy.balance);
        assertEquals(1, economy.deposits);
        assertEquals(1, manager.recent(id).size());
    }

    @Test
    void shutdownRetriesPendingPayoutWithoutNewWithdrawal() {
        UUID id = UUID.randomUUID();
        FakeEconomy economy = new FakeEconomy(100);
        BlackjackManager manager = manager(economy,
            deck(BlackjackRank.ACE, BlackjackRank.NINE, BlackjackRank.KING, BlackjackRank.EIGHT));
        manager.start(id);
        economy.rejectDeposit = true;
        assertThrows(pl.kurdeslawo.casinocore.economy.EconomyTransactionException.class, () -> manager.finishDeal(id));
        assertThrows(pl.kurdeslawo.casinocore.economy.EconomyTransactionException.class, manager::closeAll);
        assertTrue(manager.session(id).isPresent());
        economy.rejectDeposit = false;
        manager.closeAll();
        manager.closeAll();
        assertTrue(manager.session(id).isEmpty());
        assertEquals(1, economy.withdraws);
        assertEquals(1, economy.deposits);
        assertEquals(1, manager.recent(id).size());
    }

    private static BlackjackManager manager(FakeEconomy economy, BlackjackDeck deck) {
        return new BlackjackManager(economy, List.of(1L, 5L, 25L), 3, 2, false,
            () -> deck, new InMemoryBlackjackHistoryRepository());
    }

    private static java.util.function.Supplier<BlackjackDeck> sequentialDecks(BlackjackDeck... decks) {
        ArrayList<BlackjackDeck> remaining = new ArrayList<>(List.of(decks));
        return () -> remaining.removeFirst();
    }

    private static BlackjackDeck deck(BlackjackRank... ranks) {
        List<BlackjackCard> cards = new ArrayList<>();
        for (BlackjackRank rank : ranks) cards.add(new BlackjackCard(rank, BlackjackSuit.SPADES));
        return new BlackjackDeck(cards);
    }

    private static final class FakeEconomy implements EconomyProvider {
        private long balance;
        private int withdraws;
        private int deposits;
        private boolean rejectDeposit;

        private FakeEconomy(long balance) {
            this.balance = balance;
        }

        @Override
        public long getBalance(UUID playerId) {
            return balance;
        }

        @Override
        public boolean has(UUID playerId, long amount) {
            return balance >= amount;
        }

        @Override
        public boolean withdraw(UUID playerId, long amount) {
            withdraws++;
            if (balance < amount) return false;
            balance -= amount;
            return true;
        }

        @Override
        public void deposit(UUID playerId, long amount) {
            if (rejectDeposit) throw pl.kurdeslawo.casinocore.economy.EconomyTransactionException.rejected("Provider rejected payout");
            deposits++;
            balance += amount;
        }
    }
}
