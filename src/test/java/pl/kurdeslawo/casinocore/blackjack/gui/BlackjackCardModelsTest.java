package pl.kurdeslawo.casinocore.blackjack.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.kurdeslawo.casinocore.blackjack.BlackjackCard;
import pl.kurdeslawo.casinocore.blackjack.BlackjackRank;
import pl.kurdeslawo.casinocore.blackjack.BlackjackSuit;

class BlackjackCardModelsTest {
    @Test
    void everyCardMapsToOneUniqueGeneratedModel() {
        Set<String> modelPaths = new HashSet<>();
        for (BlackjackSuit suit : BlackjackSuit.values()) {
            for (BlackjackRank rank : BlackjackRank.values()) {
                modelPaths.add(BlackjackCardModels.modelPath(new BlackjackCard(rank, suit)));
            }
        }

        assertEquals(52, modelPaths.size());
        assertEquals("blackjack/cards/ace_spades",
            BlackjackCardModels.modelPath(new BlackjackCard(BlackjackRank.ACE, BlackjackSuit.SPADES)));
        assertEquals("blackjack/cards/10_hearts",
            BlackjackCardModels.modelPath(new BlackjackCard(BlackjackRank.TEN, BlackjackSuit.HEARTS)));
        assertEquals("blackjack/cards/king_diamonds",
            BlackjackCardModels.modelPath(new BlackjackCard(BlackjackRank.KING, BlackjackSuit.DIAMONDS)));
        assertEquals("blackjack/cards/card_back", BlackjackCardModels.CARD_BACK_PATH);

        Path itemDefinitions = Path.of("assets", "resource_pack", "assets", "casinocore", "items");
        for (String modelPath : modelPaths) {
            assertTrue(Files.isRegularFile(itemDefinitions.resolve(modelPath + ".json")), modelPath);
        }
        assertTrue(Files.isRegularFile(itemDefinitions.resolve(BlackjackCardModels.CARD_BACK_PATH + ".json")));
    }
}
