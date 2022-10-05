package test.bpp;

import java.net.DatagramPacket;
import cc.clayman.util.Verbose;

/**
 * A NetFn that does some BPP processing
 */
public class BPPFn  {
    // Input / Output counts
    int count = 0;
    int countLastSecond = 0;
    long volumeIn = 0;
    long volumeOut = 0;
    long volumeInLastSecond = 0;
    long volumeOutLastSecond = 0;

    
    // The BPPUnpacker
    BPPUnpack unpack = null;

    // Construct a BPPFn
    public BPPFn(int bandwidthBits, int packetsPerSecond) {
        // Start the BPPUnpack
        unpack = new BPPUnpack(bandwidthBits, packetsPerSecond);
    }
        
    
    /**
     * Process the recevied Datagram
     *
     * The callback for when a Datagram is received by an Intercepter.
     * Return a Datagram to forward Datagram, Return null to throw it away.
     */
    public DatagramPacket datagramProcess(DatagramPacket datagram) {

        count++;
        countLastSecond++;

        try {
            // increase volumeIn
            volumeIn += datagram.getLength();
            volumeInLastSecond += datagram.getLength();
                
            // do the conversion
            byte[] result = unpack.convert(count, datagram);

            // now patch up the datagram to have modified contents

            if (result == null) {
                // nothnig to do but forward the datagram

                // increase volumeOut
                volumeOut += datagram.getLength();
                volumeOutLastSecond += datagram.getLength();

                // Get the network function to forward the packet
                return datagram;

            } else {
                // we have new payload
                if (Verbose.level >= 3) {
                    System.err.println("datagram length = " + datagram.getLength() + " newPayload length = " + result.length);
                }
                    
                DatagramPacket newDatagram = new DatagramPacket(result, result.length);
                newDatagram.setAddress(datagram.getAddress());
                newDatagram.setPort(datagram.getPort());

                // increase volumeOut
                volumeOut += newDatagram.getLength();
                volumeOutLastSecond += newDatagram.getLength();

                // DO NOT get the network function to forward the packet
                // send our one
                return newDatagram;
            }                
        } catch (Exception e) {
            System.err.println(e.getClass() + ": " + e.getMessage());
            return null;
        }
    }
    
}
