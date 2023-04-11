package netfn.udp;

import java.net.DatagramPacket;
import cc.clayman.util.Verbose;

/**
 * A NetFn that does some UDP processing
 */
public class UDPFn  {
    // Input / Output counts
    int count = 0;
    int countLastSecond = 0;
    long volumeIn = 0;
    long volumeOut = 0;
    long volumeInLastSecond = 0;
    long volumeOutLastSecond = 0;

    
    // The UDPChecker
    UDPCheck check = null;

    // Construct a UDPFn
    public UDPFn(int bandwidthBits, int packetsPerSecond) {
        // Start the UDPCheck
        check = new UDPCheck(bandwidthBits, packetsPerSecond);
    }
        
    
    /**
     * Process the recevied Datagram
     *
     * The callback for when a Datagram is received by an Intercepter.
     * Return a Datagram to forward Datagram
     * Return null to throw it away.
     */
    public DatagramPacket datagramProcess(DatagramPacket datagram) {

        count++;
        countLastSecond++;

        try {
            // increase volumeIn
            volumeIn += datagram.getLength();
            volumeInLastSecond += datagram.getLength();
                
            // do the conversion
            byte[] result = check.convert(count, datagram);

            // now patch up the datagram to have modified contents

            if (result == null) {
                // nothnig to do but forward the datagram

                // increase volumeOut
                volumeOut += datagram.getLength();
                volumeOutLastSecond += datagram.getLength();

                // Get the network function to forward the packet
                return datagram;

            } else {
                // not null
                // for UDP we dont care about any payload and thus no packet to send
                if (Verbose.level >= 3) {
                    System.err.println("no packet to send");
                }
                    
                // DO NOT get the network function to forward the packet
                // nothing to send
                return null;
            }                
        } catch (Exception e) {
            System.err.println(e.getClass() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Adjust the bandwidth
    public void setBandwidth(int bitsPerSecond) {
        // inform the checker
        check.setBandwidth(bitsPerSecond);
    }
    
}
