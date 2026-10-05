package xerca.xercamusic.common.sync;

/** Monotonic microseconds; prefer the sample with the least network/queue delay. */
public final class SyncClock {
    private long offset, bestRtt = Long.MAX_VALUE;
    public static long now() { return System.nanoTime() / 1000; }
    public synchronized boolean sample(long sent, long received, long server) {
        long rtt = received - sent;
        if (rtt < 0 || rtt > 2_000_000) return false;
        if (rtt <= bestRtt) { bestRtt = rtt; offset = server - sent - rtt / 2; }
        return true;
    }
    public synchronized long toLocal(long serverTime) { return serverTime - offset; }
    public synchronized boolean ready() { return bestRtt != Long.MAX_VALUE; }
    public synchronized long rtt() { return bestRtt; }
    public synchronized void reset() { bestRtt = Long.MAX_VALUE; offset = 0; }
}
