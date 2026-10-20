package pl.kurdeslawo.casinocore.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

public final class BlackjackDeck {
    private final List<BlackjackCard> cards;
    private int next;

    public BlackjackDeck(RandomGenerator random) {
        cards = new ArrayList<>(52);
        for (BlackjackSuit suit : BlackjackSuit.values())
            for (BlackjackRank rank : BlackjackRank.values())
                cards.add(new BlackjackCard(rank, suit));
        Collections.shuffle(cards, new java.util.Random(random.nextLong()));
    }

    public BlackjackDeck(List<BlackjackCard> cards) {
        this.cards = new ArrayList<>(cards);
    }

    public BlackjackCard draw() {
        if (next >= cards.size()) throw new IllegalStateException("Blackjack deck is empty");
        return cards.get(next++);
    }

    public List<BlackjackCard> remaining() {
        return List.copyOf(cards.subList(next, cards.size()));
    }
}
