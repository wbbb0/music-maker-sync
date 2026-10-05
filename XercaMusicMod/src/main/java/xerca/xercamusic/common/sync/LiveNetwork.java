package xerca.xercamusic.common.sync;
import java.util.WeakHashMap;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.HandlerThread;
import xerca.xercamusic.common.sync.LivePackets.*;
public final class LiveNetwork {
    private static final WeakHashMap<Connection,Long> LAST_PROBE=new WeakHashMap<>();
    private LiveNetwork() {}
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Batch.TYPE,Batch.CODEC,LiveServer::handle);
        registrar.playToClient(Delivery.TYPE,Delivery.CODEC,(p,ctx)-> xerca.xercamusic.client.LiveClient.receive(p));
        var network=registrar.executesOn(HandlerThread.NETWORK);
        network.playToServer(Probe.TYPE,Probe.CODEC,(p,ctx)->{
            long now=SyncClock.now();
            synchronized(LAST_PROBE){long last=LAST_PROBE.getOrDefault(ctx.connection(),Long.MIN_VALUE/2);if(now-last<100_000)return;LAST_PROBE.put(ctx.connection(),now);}
            ctx.reply(new Clock(p.sent(),now));
        });
        network.playToClient(Clock.TYPE,Clock.CODEC,(p,ctx)->xerca.xercamusic.client.LiveClient.clock(p));
    }
}
