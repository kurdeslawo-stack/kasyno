package pl.kurdeslawo.casinocore.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BlackjackEngineTest {
    private final BlackjackEngine engine = new BlackjackEngine(3, 2, false);

    @Test
    void cardAndAceValuesAreCorrect() {
        BlackjackHand hand = hand(BlackjackRank.ACE, BlackjackRank.SIX);
        assertEquals(17, hand.value());
        assertTrue(hand.isSoft());

        hand.add(card(BlackjackRank.KING));
        assertEquals(17, hand.value());
        assertFalse(hand.isSoft());

        BlackjackHand twoAces = hand(BlackjackRank.ACE, BlackjackRank.ACE, BlackjackRank.FIVE);
        assertEquals(17, twoAces.value());
        assertTrue(twoAces.isSoft());
    }

    @Test
    void detectsBlackjackAndBust() {
        assertTrue(hand(BlackjackRank.ACE, BlackjackRank.KING).isBlackjack());
        assertTrue(hand(BlackjackRank.KING, BlackjackRank.QUEEN, BlackjackRank.TWO).isBust());
    }

    @Test
    void dealerSoftSeventeenRuleIsConfigurable() {
        BlackjackHand softSeventeen = hand(BlackjackRank.ACE, BlackjackRank.SIX);
        assertFalse(engine.dealerShouldHit(softSeventeen));
        assertTrue(new BlackjackEngine(3, 2, true).dealerShouldHit(softSeventeen));
        assertTrue(engine.dealerShouldHit(hand(BlackjackRank.TEN, BlackjackRank.SIX)));
    }

    @Test
    void resolvesWinLossAndPush() {
        assertEquals(BlackjackOutcome.WIN,
            engine.resolve(hand(BlackjackRank.TEN, BlackjackRank.NINE),
                hand(BlackjackRank.TEN, BlackjackRank.EIGHT)));
        assertEquals(BlackjackOutcome.LOSS,
            engine.resolve(hand(BlackjackRank.TEN, BlackjackRank.SEVEN),
                hand(BlackjackRank.TEN, BlackjackRank.EIGHT)));
        assertEquals(BlackjackOutcome.PUSH,
            engine.resolve(hand(BlackjackRank.TEN, BlackjackRank.EIGHT),
                hand(BlackjackRank.KING, BlackjackRank.EIGHT)));
    }

    @Test
    void blackjackPayoutUsesDocumentedWholeCoinFloor() {
        assertEquals(12, engine.totalReturn(BlackjackOutcome.BLACKJACK, 5));
        assertEquals(2, engine.totalReturn(BlackjackOutcome.BLACKJACK, 1));
        assertEquals(10, engine.totalReturn(BlackjackOutcome.WIN, 5));
        assertEquals(5, engine.totalReturn(BlackjackOutcome.PUSH, 5));
        assertEquals(0, engine.totalReturn(BlackjackOutcome.LOSS, 5));
    }

    @Test
    void validatesFullReturnAndAllowsLargeIntermediateProducts() {
        BlackjackEngine highRatio = new BlackjackEngine(100, 1, false);
        assertThrows(ArithmeticException.class, () -> highRatio.maximumReturn(Long.MAX_VALUE / 100));
        BlackjackEngine balancedRatio = new BlackjackEngine(100, 100, false);
        long stake = Long.MAX_VALUE / 4;
        assertEquals(stake * 2, balancedRatio.totalReturn(BlackjackOutcome.BLACKJACK, stake));
        assertEquals(stake * 4, balancedRatio.maximumReturn(stake));
        assertTrue(highRatio.maximumReturn(100_000_000_000_000L) > 9_007_199_254_740_992L);
    }

    private static BlackjackHand hand(BlackjackRank... ranks) {
        BlackjackHand hand = new BlackjackHand();
        for (BlackjackRank rank : ranks) hand.add(card(rank));
        return hand;
    }

    private static BlackjackCard card(BlackjackRank rank) {
        return new BlackjackCard(rank, BlackjackSuit.SPADES);
    }
}
