package pl.kurdeslawo.casinocore.blackjack;

public record BlackjackRoundRecord(
        int playerValue, int dealerValue, long wager, long payout, BlackjackOutcome outcome) {
    public long netChange() {
        return payout - wager;
    }
}
