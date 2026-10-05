package xerca.xercamusic.client;
import xerca.xercamusic.common.sync.SyncClock;
/** Capture MIDI time before Minecraft's main-thread queue. */
final class InputTimestamp {
    private static final ThreadLocal<Long> TIME=new ThreadLocal<>();
    static long now(){Long t=TIME.get();return t==null?SyncClock.now():t;}
    static void run(long time,Runnable action){TIME.set(time);try{action.run();}finally{TIME.remove();}}
}
