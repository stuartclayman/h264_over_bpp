package netfn.bpp;

import java.net.DatagramPacket;
import cc.clayman.net.IP;
import cc.clayman.bpp.BPP;
import cc.clayman.h264.NALType;
import cc.clayman.util.ANSI;
import cc.clayman.util.Verbose;
import java.util.Optional;


/**
 * A NetFn that does some BPP processing based on bandwidth
 * but does no trimming in the first period - e.g 100ms (0.1 seconds)
 */
public class BPPNoTrimBandwidth extends AbstractBPPFn implements BPPFn {
    // The no trim period
    float period = 0.0f;


    /**
     * Construct with the bandwidth in bits per sec, and the period
     * where there is no trimming
     * @param bandwidthBits is int: 838860 bits
     * @param period part of a second,  0.2  == 200ms
     */
    public BPPNoTrimBandwidth(int bandwidthBits, float period) {
        super(bandwidthBits);
        this.period = period;
    }
        
    /**
     * Construct with the bandwidth in bits per sec, and the period
     * where there is no trimming
     * @param bandwidthBits is float:  0.8 Mbps
     * @param period part of a second,  0.2  == 200ms
     */
    public BPPNoTrimBandwidth(float bandwidthMegabits) {
        super(bandwidthMegabits);
    }
    
    // Calculate how much should be trimmed from the packet,
    // given how far behind or ahead we are
    public int calculateTrimAmount(int behind) {

        // work out packetDropLevel
        int packetDropLevel = 0;

        // no drop in first period - e.g 0.1 secs == 100ms 
        if (secondOffset < period) {
            // no drop
            packetDropLevel = 0;
            if (Verbose.level >= 2) {
                System.err.printf(" NO_DROP\n");
            }
        } else {
            if (behind > 0) {
                // fine - we are behind the ideal send amount
                packetDropLevel = 0;
                if (Verbose.level >= 2) {
                    System.err.printf(" NO_DROP\n");
                }
            } else {
                // we are ahead of ideal
                packetDropLevel =  -behind;
                if (Verbose.level >= 1) {
                    System.err.printf("  YES_DROP " + packetDropLevel + "\n");
                }
            }
        }

        return packetDropLevel;
    }
    
}
