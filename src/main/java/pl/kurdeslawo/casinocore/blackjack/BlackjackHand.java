package pl.kurdeslawo.casinocore.blackjack;

import java.util.ArrayList;
import java.util.List;

public final class BlackjackHand {
    private final List<BlackjackCard> cards = new ArrayList<>();
    public void add(BlackjackCard card) { cards.add(card); }
    public List<BlackjackCard> cards() { return List.copyOf(cards); }
    public int value() {
        int value = cards.stream().mapToInt(card -> card.rank().value()).sum();
        int aces = (int) cards.stream().filter(card -> card.rank() == BlackjackRank.ACE).count();
        while (value > 21 && aces-- > 0) value -= 10;
        return value;
    }
    public boolean isSoft() {
        int acesLowValue = cards.stream().mapToInt(card -> card.rank() == BlackjackRank.ACE ? 1 : card.rank().value()).sum();
        return cards.stream().anyMatch(card -> card.rank() == BlackjackRank.ACE) && acesLowValue + 10 <= 21;
    }
    public boolean isBlackjack() { return cards.size() == 2 && value() == 21; }
    public boolean isBust() { return value() > 21; }
}
