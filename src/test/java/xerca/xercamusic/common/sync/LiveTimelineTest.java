package xerca.xercamusic.common.sync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LiveTimelineTest {
    @Test void acceptsChordAndReleaseAtIdenticalTime(){
        assertTrue(LiveTimeline.valid(1000,0,127,1000,1200));
        assertTrue(LiveTimeline.valid(1000,95,0,1000,1200));
    }
    @Test void rejectsUntrustedRanges(){
        assertFalse(LiveTimeline.valid(0,96,100,0,0));
        assertFalse(LiveTimeline.valid(0,1,128,0,0));
        assertFalse(LiveTimeline.valid(-1,1,0,0,0));
        assertFalse(LiveTimeline.valid(Long.MAX_VALUE,1,0,0,0));
    }
    @Test void rejectsBackwardsAndFarFutureOrStaleEvents(){
        assertFalse(LiveTimeline.valid(99,1,100,100,200));
        assertFalse(LiveTimeline.valid(250001,1,100,0,0));
        assertFalse(LiveTimeline.valid(0,1,0,0,5000001));
    }
    @Test void arrivalJitterDoesNotRewriteRelativeTiming(){
        long previous=0;long[] times={0,17000,34000,51000,68000};
        long[] arrivals={50000,50000,90000,100000,100000};
        for(int i=0;i<times.length;i++){
            assertTrue(LiveTimeline.valid(times[i],60,100,previous,arrivals[i]));
            previous=times[i];
        }
    }
}
