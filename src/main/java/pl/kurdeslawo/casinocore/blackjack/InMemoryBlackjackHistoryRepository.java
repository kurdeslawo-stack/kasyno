package pl.kurdeslawo.casinocore.blackjack;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;
public final class InMemoryBlackjackHistoryRepository implements BlackjackHistoryRepository {
    private final Map<UUID,Data> data=new ConcurrentHashMap<>();
    public void record(UUID id, BlackjackRoundRecord r){Data d=data.computeIfAbsent(id,x->new Data()); synchronized(d){d.recent.addFirst(r);while(d.recent.size()>5)d.recent.removeLast();d.played=add(d.played,1);if(r.outcome()==BlackjackOutcome.LOSS)d.losses=add(d.losses,1);else if(r.outcome()==BlackjackOutcome.PUSH)d.pushes=add(d.pushes,1);else {d.wins=add(d.wins,1);if(r.outcome()==BlackjackOutcome.BLACKJACK)d.blackjacks=add(d.blackjacks,1);}d.staked=add(d.staked,r.wager());d.paid=add(d.paid,r.payout());}}
    public List<BlackjackRoundRecord> recent(UUID id){Data d=data.get(id);if(d==null)return List.of();synchronized(d){return List.copyOf(d.recent);}}
    public BlackjackStats stats(UUID id){Data d=data.get(id);if(d==null)return BlackjackStats.EMPTY;synchronized(d){return new BlackjackStats(d.played,d.wins,d.losses,d.pushes,d.blackjacks,d.staked,d.paid);}}
    private static long add(long a,long b){return Long.MAX_VALUE-a<b?Long.MAX_VALUE:a+b;}
    private static final class Data{final Deque<BlackjackRoundRecord> recent=new ArrayDeque<>();long played,wins,losses,pushes,blackjacks,staked,paid;}
}
