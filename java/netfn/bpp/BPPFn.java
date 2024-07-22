package netfn.bpp;

import java.net.DatagramPacket;
import cc.clayman.util.Verbose;
import java.util.Optional;

/**
 * A NetFn that does some BPP processing
 *
 * There are 4 main phases: (i) getting Timing information when a
 * packet arrives; (ii) doing Pre processing of packet to get the
 * packet size and the determining the ideal number of bytes to send
 * at the particular offset into a second; (iii) to Check the current
 * second to see if a second boundary has been crossed; and (iv) the
 * Decision making and forwarding which processes each packet.
 */
public interface BPPFn  {
    
    /**
     * Process a packet, and possibly trim the contents.
     */
    public Optional<byte[]> process(int count, DatagramPacket packet) throws UnsupportedOperationException;

    /**
     * Get the bandwidth
     */
    public int getBandwidth();
    
    /**
     * Adjust the bandwidth
     */
    public void setBandwidth(int bitsPerSecond);
    
}
