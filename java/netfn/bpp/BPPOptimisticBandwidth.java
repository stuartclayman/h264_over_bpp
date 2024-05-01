package netfn.bpp;

import java.net.DatagramPacket;
import cc.clayman.net.IP;
import cc.clayman.bpp.BPP;
import cc.clayman.h264.NALType;
import cc.clayman.util.ANSI;
import cc.clayman.util.Verbose;


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
    
    // ▷ Timing
    public void bppDoTiming() {
        countThisSec++;
        
        // timing
        now = System.currentTimeMillis();
        // Millisecond offset between now and timeStart 
        timeOffset = now - timeStart;
        // What is the offset in this second
        secondOffset = (float)timeOffset / 1000;
    }
        
    // ▷ Pre processing of packet
    public int bppPre(DatagramPacket packet) {
        // check bandwidth is enough
        payload = packet.getData();
        packetLength = packet.getLength(); 

        totalIn += packetLength;
        recvThisSec += packetLength;

        // The ideal no of bytes to send at this offset into a second 
        int idealSendThisSec = 0;

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

        if (Verbose.level >= 2) {
            System.err.printf("BPPUnpack: " + count + " secondOffset: " + secondOffset + " countThisSec " + countThisSec +  " recvThisSec " + recvThisSec + " sentThisSec " + sentThisSec + " idealSendThisSec " + idealSendThisSec + " behind " + behind);
        }

        return behind;
    }

        
    // ▷ Check current second
    public void bppCheckTiming() {
        if (timeOffset >= 1000) {
            // we crossed a second boundary
            seconds++;
            timeStart = now;
            countThisSec = 0;
            recvThisSec = 0;
            sentThisSec = 0;
            secondOffset = 0;
        }

    }
        
    // ▷ Decision making and forwarding
    public byte[] bppTrim(int behind, DatagramPacket packet) {

        // work out packetDropLevel
        int packetDropLevel = 0;

        if (behind > 0) {
            // fine - we are behind the ideal send amount
            packetDropLevel = 0;
            if (Verbose.level >= 2) {
                System.err.printf(" NO_DROP\n");
            }
        } else {
            packetDropLevel =  behind;
            if (Verbose.level >= 1) {
                System.err.printf("  YES_DROP " + packetDropLevel + "\n");
            }
        }


        // Check Packet

        // Look into the packet headers
        unpackDatagramHeaders(packet);

        if (packetDropLevel < 0) {
            // If we need to drop something, we need to look at the content
            unpackDatagramContent(packet);

            int droppedAmount = dropContent(-packetDropLevel);

            int size = packetLength - droppedAmount;
            totalOut += size;
            sentThisSec += size;

            // Now rebuild the packet payload, from the original packet
            byte[] newPayload = packContent();

            return newPayload;

        } else {
            // Get the network function to forward the packet
            int size = packetLength;
            totalOut += size;
            sentThisSec += size;
            return null;
        }

        
    }
}
