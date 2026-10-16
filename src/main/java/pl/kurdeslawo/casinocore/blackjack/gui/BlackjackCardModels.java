package pl.kurdeslawo.casinocore.blackjack.gui;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import pl.kurdeslawo.casinocore.blackjack.BlackjackCard;
import pl.kurdeslawo.casinocore.blackjack.BlackjackRank;

public final class BlackjackCardModels {
    public static final String CARD_BACK_PATH = "blackjack/cards/card_back";

    private BlackjackCardModels() {}

    public static NamespacedKey modelKey(BlackjackCard card) {
        return new NamespacedKey("casinocore", modelPath(card));
    }

    public static NamespacedKey cardBackKey() {
        return new NamespacedKey("casinocore", CARD_BACK_PATH);
    }

    public static String modelPath(BlackjackCard card) {
        return "blackjack/cards/" + rankName(card.rank()) + "_"
            + card.suit().name().toLowerCase(Locale.ROOT);
    }

    private static String rankName(BlackjackRank rank) {
        return switch (rank) {
            case ACE -> "ace";
            case JACK -> "jack";
            case QUEEN -> "queen";
            case KING -> "king";
            default -> Integer.toString(rank.value());
        };
    }
}
