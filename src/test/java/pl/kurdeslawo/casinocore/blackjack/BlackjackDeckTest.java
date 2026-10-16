package pl.kurdeslawo.casinocore.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class BlackjackDeckTest {
    @Test
    void standardDeckContainsEveryCardExactlyOnce() {
        BlackjackDeck deck = new BlackjackDeck(RandomGenerator.getDefault());
        HashSet<BlackjackCard> cards = new HashSet<>();
        for (int index = 0; index < 52; index++) cards.add(deck.draw());

        assertEquals(52, cards.size());
        assertThrows(IllegalStateException.class, deck::draw);
    }
}
