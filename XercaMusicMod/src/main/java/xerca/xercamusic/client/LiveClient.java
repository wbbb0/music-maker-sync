package xerca.xercamusic.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import xerca.xercamusic.common.Mod;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.sync.SyncClock;
import xerca.xercamusic.common.sync.LivePackets.*;

@EventBusSubscriber(modid=Mod.MODID,value=Dist.CLIENT)
public final class LiveClient {
    private static final SyncClock CLOCK=new SyncClock();
    private record RemoteKey(UUID player,long session) {}
    private static final Map<RemoteKey,Remote> REMOTES=new HashMap<>();
    private static final Set<Long> PROBES=new HashSet<>();
    private static long lastProbe,received,played,dropped,maxLate;
    private LiveClient() {}
    public static final class Sender {
        private final long session=UUID.randomUUID().getMostSignificantBits();
        private final int instrument; private final BlockPos block;
        private final List<Note> notes=new ArrayList<>(32);
        private long origin=-1,lastEvent,lastSend; private int sequence; private boolean closed;
        Sender(IItemInstrument i,BlockPos b){instrument=i.getInstrumentId();block=b;}
        void note(int key,float volume,boolean stop) {
            if(closed)return;
            long now=InputTimestamp.now(); if(origin<0) origin=now;
            long elapsed=Math.max(lastEvent,Math.max(0,now-origin));lastEvent=elapsed;
            notes.add(new Note(elapsed,key,stop?0:Math.max(1,Math.min(127,Math.round(volume*127)))));
            if(notes.size()==32)flush(false);
        }
        void pump(){if(origin>=0 && SyncClock.now()-lastSend> (notes.isEmpty()?1_000_000:8_000))flush(false);}
        void close(){if(closed)return;if(origin>=0)flush(true);closed=true;}
        private void flush(boolean end) {
            if(Minecraft.getInstance().getConnection()!=null) ModClient.sendToServer(new Batch(session,sequence++,instrument,block,List.copyOf(notes),end));
            notes.clear();lastSend=SyncClock.now();
        }
    }
    public static void clock(Clock p) {
        synchronized(PROBES){if(!PROBES.remove(p.sent()))return;}
        CLOCK.sample(p.sent(),SyncClock.now(),p.server());
    }
    public static void receive(Delivery p) {
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null || mc.player==null || p.player().equals(mc.player.getUUID()))return;
        Batch b=p.batch(); RemoteKey key=new RemoteKey(p.player(),b.session()); Remote r=REMOTES.get(key);
        if(p.abort()){if(r!=null){r.stop();REMOTES.remove(key);}return;}
        if(b.instrument()<0 || b.instrument()>=Items.INSTRUMENTS.size())return;
        if(r==null) {
            if(b.notes().isEmpty())return;
            // A normally ended session still owns buffered notes during a quick instrument switch.
            for(var old:List.copyOf(REMOTES.entrySet())) {
                if(old.getKey().player().equals(p.player()) && !old.getValue().end){old.getValue().stop();REMOTES.remove(old.getKey());}
            }
            if(REMOTES.size()>=128 || REMOTES.keySet().stream().filter(k->k.player().equals(p.player())).count()>=4)return;
            long localAnchor=CLOCK.ready()?CLOCK.toLocal(p.anchor()):SyncClock.now()+150_000-(b.notes().isEmpty()?0:b.notes().getFirst().time());
            r=new Remote(b.session(),localAnchor,b.instrument(),b.block());REMOTES.put(key,r);
        }
        if(b.sequence()<=r.sequence)return;r.sequence=b.sequence();r.touched=SyncClock.now();
        if(r.queue.size()+b.notes().size()>2048){r.stop();REMOTES.remove(key);dropped+=b.notes().size();return;}
        r.queue.addAll(b.notes());received+=b.notes().size();r.end|=b.end();
    }
    private static void pump() {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null || mc.player==null)return;
        long now=SyncClock.now();
        if(now-lastProbe>(CLOCK.ready()?5_000_000:250_000)) {
            synchronized(PROBES){if(PROBES.size()>=8)PROBES.clear();PROBES.add(now);}
            ModClient.sendToServer(new Probe(now));lastProbe=now;
        }
        if(mc.screen instanceof GuiInstrument gui)gui.pumpLive();
        for(var entry:List.copyOf(REMOTES.entrySet())) {
            Remote r=entry.getValue();Player owner=mc.level.getPlayerByUUID(entry.getKey().player());
            if(owner==null || now-r.touched>4_000_000 || (r.block!=null && !mc.level.hasChunkAt(r.block))) {r.stop();REMOTES.remove(entry.getKey());continue;}
            int budget=128;
            while(budget-->0 && !r.queue.isEmpty() && r.anchor+r.queue.peekFirst().time()<=now) {
                Note n=r.queue.removeFirst();long late=now-r.anchor-n.time();maxLate=Math.max(maxLate,late);
                if(n.key()<0 || n.key()>=96)continue;
                NoteSound old=r.sounds[n.key()];
                if(old!=null){old.stopSound();r.sounds[n.key()]=null;}
                if(n.velocity()==0)continue;
                if(late>100_000){dropped++;continue;}
                var ins=Items.INSTRUMENTS.get(r.instrument).getSound(IItemInstrument.idToNote(n.key()));
                if(ins==null)continue;
                var pos=r.block==null?owner.position():net.minecraft.world.phys.Vec3.atCenterOf(r.block);
                NoteSound sound=ModClient.playNote(ins.sound(),pos.x,pos.y,pos.z,r.block==null?SoundSource.PLAYERS:SoundSource.BLOCKS,n.velocity()/127f,ins.pitch(),(byte)-1);
                sound.follow(owner,r.block);r.sounds[n.key()]=sound;played++;
                if(Boolean.getBoolean("xercamusic.syncDiagnostics"))Mod.LOGGER.info("LIVE_NOTE session={} key={} target={} actual={} lateUs={}",r.session,n.key(),r.anchor+n.time(),now,late);
            }
            if(r.end && r.queue.isEmpty()){r.stop();REMOTES.remove(entry.getKey());}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre e){pump();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){pump();}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){reset();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){reset();}
    private static void reset(){REMOTES.values().forEach(Remote::stop);REMOTES.clear();synchronized(PROBES){PROBES.clear();}CLOCK.reset();lastProbe=0;received=played=dropped=maxLate=0;}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("musicsync").executes(ctx->{
            ctx.getSource().sendSuccess(() -> Component.literal("Music sync: RTT="+(CLOCK.ready()?CLOCK.rtt()/1000+"ms":"pending")+" received="+received+" played="+played+" dropped="+dropped+" maxLate="+maxLate/1000+"ms sessions="+REMOTES.size()), false);return 1;
        }));
        if(Boolean.getBoolean("xercamusic.syncDiagnostics")) e.getDispatcher().register(Commands.literal("musicsyncdemo").executes(ctx->{
            if(Minecraft.getInstance().screen instanceof GuiInstrument gui)gui.diagnosticMidi();
            else ModClient.showInstrumentGui();
            return 1;
        }));
        if(Boolean.getBoolean("xercamusic.syncDiagnostics")) e.getDispatcher().register(Commands.literal("musicsyncswitch").executes(ctx->{
            var player=Minecraft.getInstance().player;
            if(player!=null && player.getMainHandItem().getItem() instanceof IItemInstrument instrument) {
                for(int key:new int[]{39,40}) {
                    Sender sender=new Sender(instrument,null);
                    sender.note(key,.8f,false);sender.note(key,0,true);sender.close();
                }
            }
            return 1;
        }));
    }
    private static final class Remote {
        final long session,anchor;final int instrument;final BlockPos block;
        int sequence=-1;long touched;boolean end;
        final ArrayDeque<Note> queue=new ArrayDeque<>();final NoteSound[] sounds=new NoteSound[96];
        Remote(long s,long a,int i,BlockPos b){session=s;anchor=a;instrument=i;block=b;}
        void stop(){for(NoteSound s:sounds)if(s!=null)s.stopSound();queue.clear();}
    }
}
