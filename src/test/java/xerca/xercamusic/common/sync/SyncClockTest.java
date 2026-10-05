package xerca.xercamusic.common.sync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SyncClockTest {
    @Test void estimatesSymmetricDelayAndIgnoresQueuedSamples(){
        SyncClock c=new SyncClock();
        assertTrue(c.sample(1000,1200,5100));
        assertEquals(2000,c.toLocal(6000));
        c.sample(2000,3000,6800);
        assertEquals(2000,c.toLocal(6000));
        assertEquals(200,c.rtt());
    }
    @Test void rejectsInvalidSamplesAndResetsAcrossConnections(){
        SyncClock c=new SyncClock();
        assertFalse(c.sample(100,99,500));assertFalse(c.sample(0,2_000_001,500));assertFalse(c.ready());
        c.sample(100,120,1000);assertTrue(c.ready());c.reset();assertFalse(c.ready());
    }
    @Test void independentListenersShareSameServerTimeline(){
        SyncClock a=new SyncClock(),b=new SyncClock();
        a.sample(10_000,10_020,1_000_010);b.sample(900_000,900_080,1_000_040);
        long[] events={0,7_000,17_500,33_333,140_000};
        long previousA=0,previousB=0;
        for(long t:events){
            long da=a.toLocal(1_150_000+t),db=b.toLocal(1_150_000+t);
            if(t>0)assertEquals(da-previousA,db-previousB);
            previousA=da;previousB=db;
        }
    }
}
