package pl.kurdeslawo.casinocore.blackjack;

import java.math.BigInteger;

public final class BlackjackEngine {
    private final int blackjackNumerator;
    private final int blackjackDenominator;
    private final boolean hitSoft17;

    public BlackjackEngine(int numerator, int denominator, boolean hitSoft17) {
        if (numerator <= 0 || denominator <= 0)
            throw new IllegalArgumentException("Blackjack payout ratio must be positive");
        this.blackjackNumerator = numerator;
        this.blackjackDenominator = denominator;
        this.hitSoft17 = hitSoft17;
    }

    public boolean dealerShouldHit(BlackjackHand hand) {
        return hand.value() < 17 || (hitSoft17 && hand.value() == 17 && hand.isSoft());
    }

    public BlackjackOutcome resolve(BlackjackHand player, BlackjackHand dealer) {
        if (player.isBlackjack())
            return dealer.isBlackjack() ? BlackjackOutcome.PUSH : BlackjackOutcome.BLACKJACK;
        if (dealer.isBlackjack() || player.isBust()) return BlackjackOutcome.LOSS;
        if (dealer.isBust()) return BlackjackOutcome.WIN;
        int playerValue = player.value();
        int dealerValue = dealer.value();
        if (playerValue > dealerValue) return BlackjackOutcome.WIN;
        if (playerValue < dealerValue) return BlackjackOutcome.LOSS;
        return BlackjackOutcome.PUSH;
    }

    public long totalReturn(BlackjackOutcome outcome, long wager) {
        if (wager <= 0) throw new IllegalArgumentException("Wager must be positive");
        return switch (outcome) {
            case LOSS -> 0L;
            case PUSH -> wager;
            case WIN -> Math.multiplyExact(wager, 2L);
            case BLACKJACK ->
                    BigInteger.valueOf(wager)
                            .multiply(BigInteger.valueOf(blackjackNumerator))
                            .divide(BigInteger.valueOf(blackjackDenominator))
                            .add(BigInteger.valueOf(wager))
                            .longValueExact();
        };
    }

    public long maximumReturn(long stake) {
        return Math.max(
                Math.multiplyExact(stake, 4L), totalReturn(BlackjackOutcome.BLACKJACK, stake));
    }

    public int blackjackNumerator() {
        return blackjackNumerator;
    }

    public int blackjackDenominator() {
        return blackjackDenominator;
    }

    public boolean hitSoft17() {
        return hitSoft17;
    }
}
