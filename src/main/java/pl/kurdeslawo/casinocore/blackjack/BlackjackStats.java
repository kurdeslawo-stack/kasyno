package pl.kurdeslawo.casinocore.blackjack;

public record BlackjackStats(
        long played, long wins, long losses, long pushes, long blackjacks, long staked, long paid) {
    public static final BlackjackStats EMPTY = new BlackjackStats(0, 0, 0, 0, 0, 0, 0);

    public long net() {
        return paid - staked;
    }
}
