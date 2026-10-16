package pl.kurdeslawo.casinocore.blackjack;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class BlackjackSession {
    private final UUID playerId;
    private UUID roundId;
    private long selectedStake;
    private long wager;
    private BlackjackDeck deck;
    private BlackjackEngine roundEngine;
    private BlackjackHand playerHand = new BlackjackHand();
    private BlackjackHand dealerHand = new BlackjackHand();
    private BlackjackPhase phase = BlackjackPhase.BETTING;
    private BlackjackOutcome outcome;
    private long payout;
    private boolean funded;
    private boolean settlementClaimed;
    private boolean settled;
    public BlackjackSession(UUID playerId, long stake) { this.playerId = Objects.requireNonNull(playerId); selectStake(stake); }
    public synchronized void selectStake(long stake) { require(BlackjackPhase.BETTING); if (stake <= 0) throw new IllegalArgumentException(); selectedStake = stake; }
    public synchronized void begin(BlackjackDeck deck, BlackjackEngine engine, BlackjackCard p1, BlackjackCard d1, BlackjackCard p2, BlackjackCard d2) {
        require(BlackjackPhase.BETTING); this.deck = Objects.requireNonNull(deck); this.roundEngine=Objects.requireNonNull(engine); playerHand = new BlackjackHand(); dealerHand = new BlackjackHand();
        playerHand.add(p1); dealerHand.add(d1); playerHand.add(p2); dealerHand.add(d2); wager = selectedStake; funded = true; phase = BlackjackPhase.DEALING;
        outcome = null; payout = 0; settled = false; settlementClaimed = false;
        roundId = UUID.randomUUID();
    }
    public synchronized void playerTurn() { require(BlackjackPhase.DEALING); phase = BlackjackPhase.PLAYER_TURN; }
    public synchronized BlackjackCard hit() { require(BlackjackPhase.PLAYER_TURN); BlackjackCard card = deck.draw(); playerHand.add(card); return card; }
    public synchronized void doubleWager() { require(BlackjackPhase.PLAYER_TURN); if (playerHand.cards().size() != 2) throw new IllegalStateException(); wager = Math.multiplyExact(wager, 2); }
    public synchronized void drawDealerTo(BlackjackEngine engine) { while (engine.dealerShouldHit(dealerHand)) dealerHand.add(deck.draw()); }
    public synchronized void prepareSettlement() {
        if (phase == BlackjackPhase.SETTLING || phase == BlackjackPhase.RESULT) return;
        if (!funded) throw new IllegalStateException("Round is not funded");
        // Freeze the hand before attempting any external transaction.
        outcome = roundEngine.resolve(playerHand, dealerHand);
        payout = roundEngine.totalReturn(outcome, wager);
        phase = BlackjackPhase.SETTLING;
    }
    public synchronized boolean claimSettlement() { if (phase != BlackjackPhase.SETTLING || !funded || settled || settlementClaimed) return false; settlementClaimed = true; return true; }
    public synchronized void complete(BlackjackOutcome outcome, long payout) { if (!settlementClaimed) throw new IllegalStateException(); this.outcome=outcome; this.payout=payout; settled=true; settlementClaimed=false; phase=BlackjackPhase.RESULT; }
    public synchronized void failSettlement() { settlementClaimed=false; }
    public synchronized void reset() { require(BlackjackPhase.RESULT); funded=false; settled=false; wager=0;payout=0;outcome=null;deck=null;roundEngine=null;playerHand=new BlackjackHand();dealerHand=new BlackjackHand();phase=BlackjackPhase.BETTING; }
    private void require(BlackjackPhase expected) { if (phase != expected) throw new IllegalStateException("Blackjack session is in " + phase); }
    public UUID playerId(){return playerId;} public synchronized long selectedStake(){return selectedStake;} public synchronized long wager(){return wager;}
    public synchronized BlackjackHand playerHand(){return playerHand;} public synchronized BlackjackHand dealerHand(){return dealerHand;}
    public synchronized BlackjackPhase phase(){return phase;} public synchronized BlackjackOutcome outcome(){return outcome;} public synchronized long payout(){return payout;}
    public synchronized boolean funded(){return funded;} public synchronized boolean settled(){return settled;}
    public synchronized BlackjackEngine roundEngine(){return roundEngine;}
    public synchronized UUID roundId() { return roundId; }

    public synchronized Map<String, String> snapshot() {
        Map<String, String> data = new HashMap<>();
        data.put("stake", Long.toString(selectedStake));
        data.put("wager", Long.toString(wager));
        data.put("phase", phase.name());
        data.put("playerCards", encodeCards(playerHand.cards()));
        data.put("dealerCards", encodeCards(dealerHand.cards()));
        data.put("remaining", encodeCards(deck.remaining()));
        data.put("numerator", Integer.toString(roundEngine.blackjackNumerator()));
        data.put("denominator", Integer.toString(roundEngine.blackjackDenominator()));
        data.put("soft17", Boolean.toString(roundEngine.hitSoft17()));
        data.put("outcome", outcome == null ? "" : outcome.name());
        data.put("payout", Long.toString(payout));
        return data;
    }

    public static BlackjackSession restore(UUID player, UUID round, Map<String, String> data) {
        BlackjackSession s = new BlackjackSession(player, Long.parseLong(data.get("stake")));
        s.roundId = round;
        s.wager = Long.parseLong(data.get("wager"));
        s.phase = BlackjackPhase.valueOf(data.get("phase"));
        decodeCards(data.get("playerCards")).forEach(s.playerHand::add);
        decodeCards(data.get("dealerCards")).forEach(s.dealerHand::add);
        s.deck = new BlackjackDeck(decodeCards(data.get("remaining")));
        s.roundEngine = new BlackjackEngine(Integer.parseInt(data.get("numerator")),
            Integer.parseInt(data.get("denominator")), Boolean.parseBoolean(data.get("soft17")));
        s.outcome = data.get("outcome").isEmpty() ? null : BlackjackOutcome.valueOf(data.get("outcome"));
        s.payout = Long.parseLong(data.get("payout"));
        s.funded = true;
        s.settled = s.phase == BlackjackPhase.RESULT;
        return s;
    }

    private static String encodeCards(List<BlackjackCard> cards) {
        return cards.stream().map(c -> c.rank().name() + ":" + c.suit().name()).collect(Collectors.joining(","));
    }

    private static List<BlackjackCard> decodeCards(String cards) {
        if (cards.isEmpty()) return List.of();
        return Arrays.stream(cards.split(",")).map(value -> {
            String[] parts = value.split(":");
            return new BlackjackCard(BlackjackRank.valueOf(parts[0]), BlackjackSuit.valueOf(parts[1]));
        }).toList();
    }
}
