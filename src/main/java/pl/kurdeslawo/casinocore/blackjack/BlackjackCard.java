package pl.kurdeslawo.casinocore.blackjack;

import java.util.Objects;

public record BlackjackCard(BlackjackRank rank, BlackjackSuit suit) {
    public BlackjackCard { Objects.requireNonNull(rank); Objects.requireNonNull(suit); }
}
