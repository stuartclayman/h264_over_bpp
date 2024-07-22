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
 * but allow more bytes in the first period - e.g 200ms (0.2 seconds)
 */
public class BPPOptimisticBandwidth extends AbstractBPPFn implements BPPFn {
    // The optimistic period
    float period = 0.0f;

    // How much  extra bandwidth is allowed
    float extra = 0.0f;
    
    /**
     * Construct with the bandwidth in bits per sec, and the period
     * to be more optmistic in allowed bandwidth
     * @param bandwidthBits is int: 838860 bits
     * @param period part of a second,  0.2  == 200ms
     * @param extra how much extra bandwidth is allowed, 0.1 == 10%
     */
    public BPPOptimisticBandwidth(int bandwidthBits, float period, float extra) {
        super(bandwidthBits);
        this.period = period;
        this.extra = extra;
    }
        
    /**
     * Construct with the bandwidth in bits per sec, and the period
     * to be more optmistic in allowed bandwidth
     * @param bandwidthBits is float:  0.8 Mbps
     * @param period part of a second,  0.2  == 200ms
     * @param extra how much extra bandwidth is allowed, 0.1 == 10%
     */
    public BPPOptimisticBandwidth(float bandwidthMegabits, float period, float extra) {
        super(bandwidthMegabits);
        this.period = period;
        this.extra = extra;
    }

    // Calculate how far below the ideal we are
    public int calculateBelow() {

        // if in first period (e.g 0.2 secs == 200ms)
        // allow more bandwidth (e.g. 0.1 == 10% more)
        if (secondOffset < period) {
            idealSendThisSec = (int) (availableBandwidth * (1 + extra)  * secondOffset);
        } else {
            idealSendThisSec = (int) (availableBandwidth * secondOffset);
        }

        if (idealSendThisSec < IP.BASIC_PACKET_SIZE) {
            idealSendThisSec = IP.BASIC_PACKET_SIZE;
        }
        
        // How far behind the ideal are we
        int behind = idealSendThisSec - sentThisSec;

        return behind;
    }

        
}
