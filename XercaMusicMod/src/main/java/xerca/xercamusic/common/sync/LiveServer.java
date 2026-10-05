package xerca.xercamusic.common.sync;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.common.NeoForge;
import xerca.xercamusic.common.block.BlockInstrument;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.sync.LivePackets.*;

/** One server authority and one immutable timeline per performer. No note timers or threads. */
public final class LiveServer {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Budget> BUDGETS=new HashMap<>();
    public static final long BUFFER_US=150_000;
    private LiveServer() {}
    public static void init() {
        NeoForge.EVENT_BUS.addListener(LiveServer::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{SESSIONS.clear();BUDGETS.clear();});
    }
    private static boolean valid(ServerPlayer p,Batch b) {
        if(!p.isAlive() || p.isSpectator() || b.instrument()<0 || b.instrument()>=Items.INSTRUMENTS.size()) return false;
        IItemInstrument instrument=Items.INSTRUMENTS.get(b.instrument());
        if(b.block()==null) return p.getMainHandItem().getItem()==instrument;
        return p.level().hasChunkAt(b.block()) && AudioSpace.distance(p.level(),p.position(),Vec3.atCenterOf(b.block()))<16
            && p.level().getBlockState(b.block()).getBlock() instanceof BlockInstrument block && block.getItemInstrument()==instrument;
    }
    public static void handle(Batch b,IPayloadContext ctx) {
        if(!(ctx.player() instanceof ServerPlayer p)) return;
        long now=SyncClock.now(); Session s=SESSIONS.get(p.getUUID());
        Budget budget=BUDGETS.computeIfAbsent(p.getUUID(),id->new Budget(now));
        if(now-budget.window>=1_000_000){budget.window=now;budget.events=0;budget.packets=0;}
        if(++budget.packets>128 || (budget.events+=b.notes().size())>600){if(s!=null)abort(s);return;}
        if(!valid(p,b)) { if(s!=null) abort(s); return; }
        if(s==null || s.batch.session()!=b.session()) {
            if(b.sequence()!=0 || b.session()==0 || b.notes().isEmpty() || b.notes().getFirst().time()!=0) return;
            // A client cannot churn sessions to evade the per-player admission limit.
            if(s!=null) { if(now-s.received<50_000) return; abort(s); }
            s=new Session(p,b,now); SESSIONS.put(p.getUUID(),s);
        }
        if(b.sequence()!=s.sequence || b.instrument()!=s.batch.instrument() || !Objects.equals(b.block(),s.batch.block()) || p.level()!=s.level) return;
        if(now-s.window>=1_000_000) {s.window=now;s.events=0;s.packets=0;}
        if(s.events+b.notes().size()>600 || ++s.packets>128) {abort(s);return;}
        long previous=s.last;
        for(Note n:b.notes()) {
            if(!LiveTimeline.valid(n.time(),n.key(),n.velocity(),previous,now-s.received)) {abort(s);return;}
            previous=n.time();
        }
        s.sequence++; s.events+=b.notes().size(); s.last=previous; s.touched=now;
        Vec3 origin=b.block()==null?p.position():Vec3.atCenterOf(b.block());
        // Retain recipients for guaranteed release when they move out of audible range.
        for(ServerPlayer listener:p.server.getPlayerList().getPlayers()) {
            if(listener!=p && listener.level()==p.level() && AudioSpace.distance(p.level(),listener.position(),origin)<1024) {
                s.listeners.add(listener.getUUID());
                if(!b.end())PacketDistributor.sendToPlayer(listener,new Delivery(p.getUUID(),s.anchor,b,false));
            }
        }
        s.batch=b;
        if(b.end()) {
            for(UUID id:s.listeners) {
                ServerPlayer listener=p.server.getPlayerList().getPlayer(id);
                if(listener!=null)PacketDistributor.sendToPlayer(listener,new Delivery(p.getUUID(),s.anchor,b,false));
            }
            SESSIONS.remove(p.getUUID());
        }
    }
    private static void releaseOthers(Session s,Batch b) {
        for(UUID id:s.listeners) {
            ServerPlayer p=s.player.server.getPlayerList().getPlayer(id);
            if(p!=null) PacketDistributor.sendToPlayer(p,new Delivery(s.player.getUUID(),s.anchor,b,true));
        }
    }
    private static void abort(Session s) {
        releaseOthers(s,new Batch(s.batch.session(),s.sequence,s.batch.instrument(),s.batch.block(),List.of(),true));
        SESSIONS.remove(s.player.getUUID());
    }
    private static void tick(ServerTickEvent.Post e) {
        long now=SyncClock.now();
        BUDGETS.entrySet().removeIf(entry->e.getServer().getPlayerList().getPlayer(entry.getKey())==null);
        for(Session s:List.copyOf(SESSIONS.values())) {
            s.listeners.removeIf(id->e.getServer().getPlayerList().getPlayer(id)==null);
            if(s.player.hasDisconnected() || now-s.touched>3_000_000 || s.player.level()!=s.level || !valid(s.player,s.batch)) abort(s);
        }
    }
    private static final class Budget {long window;int events,packets;Budget(long now){window=now;}}
    private static final class Session {
        final ServerPlayer player;
        final net.minecraft.world.level.Level level;
        final long anchor,received;
        final Set<UUID> listeners=new HashSet<>();
        Batch batch; int sequence,events,packets; long last,window,touched;
        Session(ServerPlayer p,Batch b,long now) {player=p;level=p.level();batch=b;received=window=touched=now;anchor=now+BUFFER_US;}
    }
}
