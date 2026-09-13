package pl.kurdeslawo.casinocore.roulette;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteRandomTest {
    @Test
    void alwaysReturnsNumberFromZeroToTwelve() {
        RouletteRandom random = new RouletteRandom(new Random(123456789L));
        for (int iteration = 0; iteration < 10_000; iteration++) {
            int number = random.nextResult().winningNumber();
            assertTrue(number >= 0 && number <= 12);
        }
    }
}
