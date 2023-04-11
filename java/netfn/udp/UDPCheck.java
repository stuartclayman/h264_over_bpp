package netfn.udp;

import java.net.DatagramPacket;
import cc.clayman.net.IP;
import cc.clayman.h264.NALType;
import cc.clayman.util.ANSI;
import cc.clayman.util.Verbose;

/**
 * Check a packet containing UDP data
 */
public class UDPCheck {
    // Available bandwidth (in bytes)
    int availableBandwidth = 0;
    // Available bandwidth (in bits)
    int availableBandwidthBits = 0;

    int packetsPerSecond = 0;
    
    // counts
    int count = 0;
    int chunkCount = 0;
    int totalIn = 0;
    int totalOut = 0;
    int countThisSec = 0;  // packet count this second
    int recvThisSec = 0;   // amount sent this second
    int sentThisSec = 0;   // amount sent this second


    // timing
    int seconds = 0;       // no of seconds
    long secondStart = 0;   // when did the second start
    long now = 0;
    long timeOffset = 0;


    // payload
    byte[] payload = null;
    int packetLength = 0;

    
    // Datagram contents
    int command = 0;
    int condition = 0;

    int [] contentSizes = null;
    int [] contentStartPos = null;

    // Empty packet send back to caller
    static final byte[] EMPTY_PACKET = new byte[0];


    public UDPCheck(int availableBandwidthBits, int packetsPerSecond) {
        this.availableBandwidthBits = availableBandwidthBits;
        this.availableBandwidth = availableBandwidthBits >> 3;
        this.packetsPerSecond = packetsPerSecond;

        // set secondStart
        secondStart = System.currentTimeMillis();

        // simple arrays
        contentSizes = new int[1];
        contentStartPos = new int[1];

    }

    // Adjust the bandwidth
    public void setBandwidth(int bitsPerSecond) {
        this.availableBandwidthBits = bitsPerSecond;
        this.availableBandwidth = bitsPerSecond >> 3;
    }    

    /**
     * Check a DatagramPacket
     * @return null to forward existing packet
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public byte[] convert(int count, DatagramPacket packet) throws UnsupportedOperationException {
        return convertB(count, packet);
    }


    /**
     * Check a DatagramPacket
     * Based on bandwidth
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public byte[] convertB(int count, DatagramPacket packet) throws UnsupportedOperationException {
        this.count = count;
        countThisSec++;
        
        // timing
        now = System.currentTimeMillis();
        timeOffset = now - secondStart;
        float secondPart = (float)timeOffset / 1000;
        
        // check bandwidth is enough
        payload = packet.getData();
        packetLength = packet.getLength(); 

        totalIn += packetLength;
        recvThisSec += packetLength;

        int idealSendThisSec = (int) (availableBandwidth * secondPart);

        if (idealSendThisSec < IP.BASIC_PACKET_SIZE) {
            idealSendThisSec = IP.BASIC_PACKET_SIZE;
        }
        
        int behind = idealSendThisSec - sentThisSec;

        if (Verbose.level >= 2) {
            System.err.printf("UDPCheck: " + count + " secondPart: " + secondPart + " countThisSec " + countThisSec +  " recvThisSec " + recvThisSec + " sentThisSec " + sentThisSec + " idealSendThisSec " + idealSendThisSec + " behind " + behind);
        }

        
        if (timeOffset >= 1000) {
            // we crossed a second boundary
            seconds++;
            secondStart = now;
            countThisSec = 0;
            recvThisSec = 0;
            sentThisSec = 0;
            secondPart = 0;
        }


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
        
        if (packetDropLevel < 0) {
            // If we need to drop something, we need to  drop the whole packet
            
            return EMPTY_PACKET;

        } else {
            // Get the network function to forward the packet
            int size = packetLength;
            totalOut += size;
            sentThisSec += size;
            return null;
        }

        
    }

    

    /**
     * Check a DatagramPacket
     * Used in recent papers
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public byte[] convert0(int count, DatagramPacket packet) throws UnsupportedOperationException {
        this.count = count;

        // check bandwidth is enough
        payload = packet.getData();
        packetLength = packet.getLength(); 

        totalIn += packetLength;

        int averagePacketLen = totalIn / count;

        int cond = packetsPerSecond;
        
        int packetDropLevel = ((packetLength * 8) - (availableBandwidthBits / cond)) / 8;

        

        if (Verbose.level >= 2) {
            System.err.printf("UDPCheck: " + count + ": availableBandwidthBits: %-10d availableBandwidth: %-10d len: %-5d, avg: %-5d condition: %-5d packetDropLevel: %d bytes\n", availableBandwidthBits, availableBandwidth, packetLength, averagePacketLen, cond, packetDropLevel);
        }


        if (availableBandwidth < (cond * averagePacketLen)) {

            // drop some content

            return EMPTY_PACKET;

        } else {
            // Get the network function to forward the packet
            return null;
        }

        
    }

    /**
     * Check a DatagramPacket
     * Similar to algorithm used in early papers
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public byte[] convert1(int count, DatagramPacket packet) throws UnsupportedOperationException {
        this.count = count;

        // check bandwidth is enough
        payload = packet.getData();
        packetLength = packet.getLength(); 

        // trafficDropLevel is no of Kbps to drop in a second
        int trafficDropLevel = (availableBandwidthBits / 1024) - (condition * 10);

        // we need to convert packetDropLevel, which is in Kbps, to a level for each packet
        // need packets / per second to workout the correct trim level
        int packetDropLevel = ((trafficDropLevel * 1024) / 8) / packetsPerSecond;


        if (Verbose.level >= 2) {

            //System.err.printf("UDPCheck: " + count + ": availableBandwidthBits (Kbps): %-10d condition (Kbps): %-5d trafficDropLevel (Kbps): %d\n", availableBandwidthBits / 1024, condition * 10, trafficDropLevel);
            //System.err.printf("UDPCheck: " + count + ": trafficDropLevel (bits): %d trafficDropLevel (bytes): %d\n", trafficDropLevel * 1024, (trafficDropLevel * 1024) / 8);

            System.err.printf("UDPCheck: " + count + ": availableBandwidthBits: %-10d availableBandwidth: %-10d len: %-5d, condition: %-5d packetDropLevel: %d bytes\n", availableBandwidthBits, availableBandwidth, packetLength, condition, packetDropLevel);
        }


        if (packetDropLevel < 0) {

            int droppedAmount = dropContent(-packetDropLevel);

            return EMPTY_PACKET;

        } else {
            // Get the network function to forward the packet
            return null;
        }

        
    }

    /**
     * Set no of packetsPerSecond
     */
    public void setPacketsPerSecond(int val) {
        packetsPerSecond = val;
    }


    protected void unpackDatagramContent(DatagramPacket packet) {
        
        // Visit each packet
        // and try to get the data out
        contentSizes[0] = packetLength;
        contentStartPos[0] = 0;


    }

    /**
     * For UDP dropContent() removes the whole packet.
     */
    protected int dropContent(int packetDropLevel) {
        if (Verbose.level >= 3) {
            System.err.println("UDPCheck: " + count + " Trim needed of " + packetDropLevel + " but will drop " + contentSizes[0]);
        }
            

        if (Verbose.level >= 3) {
            System.err.println("UDPCheck: dropContent dropped " + contentSizes[0]);
        }
        
        return contentSizes[0];
    }

}
