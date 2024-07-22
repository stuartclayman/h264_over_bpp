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
 *
 * Described in "Using Packet Trimming at the Edge for In-Network Video
 * Quality Adaption" in  Annals of Telecommunications, Springer, 2023.
 */
public class BPPBasicBandwidth extends AbstractBPPFn implements BPPFn {

    /**
     * Construct with the bandwidth in bits per sec.
     * @param bandwidthBits is int: 838860 bits
     */
    public BPPBasicBandwidth(int bandwidthBits) {
        super(bandwidthBits);
    }
        
    /**
     * Construct with the bandwidth in bits per sec.
     * @param bandwidthBits is float:  0.8 Mbps
     */
    public BPPBasicBandwidth(float bandwidthMegabits) {
        super(bandwidthMegabits);
    }

}
