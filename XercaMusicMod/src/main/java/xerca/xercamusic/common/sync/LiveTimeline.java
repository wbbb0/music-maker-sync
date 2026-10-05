package xerca.xercamusic.common.sync;
/** Admission limits for the immutable input timeline, independent of packet arrival order. */
public final class LiveTimeline {
    private LiveTimeline() {}
    public static boolean valid(long time,int key,int velocity,long previous,long elapsed) {
        return key>=0 && key<96 && velocity>=0 && velocity<=127 && time>=previous
            && time<=elapsed+250_000 && time>=Math.max(0,elapsed-5_000_000);
    }
}
