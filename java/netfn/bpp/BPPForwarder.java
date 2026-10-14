package netfn.bpp;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.util.Optional;
import com.sun.management.OperatingSystemMXBean;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.net.*;
import cc.clayman.util.Verbose;

import netfn.mgmt.*;

// Collect packets with UDPReceiver
// Forward packets with UDPSender
// Patch up DatagramPacket address before forwarding

public class BPPForwarder implements ManagementListener {

    // listen port
    int udpPort = 6799;

    // forward host
    String forwardHost = "localhost";
    // forward port
    int forwardPort = 6798;

    
    int columns = 80;    // default no of cols on terminal

    // in bits
    int bandwidthBits = 1024 * 1024;   // default: 1 Mb
    int bandwidth = 0;

    UDPReceiver receiver = null;
    UDPSender sender = null;
    BPPFn bppFn = null;

    int totalIn = 0;
    int totalOut = 0;
    
    // Input / Output counts
    int count = 0;
    int countLastSecond = 0;
    long volumeIn = 0;
    long volumeOut = 0;
    long volumeInLastSecond = 0;
    long volumeOutLastSecond = 0;



    // keep time
    long startTime = 0;
    long lastTime = 0;
    long seconds = 0;
    long lastSecond = 0;

    // keep usage info
    long totalUsage = 0;
    long perSecUsage = 0;
    long perSecCPU = 0;
    OperatingSystemMXBean osMBean = null;
    
    public BPPForwarder(int udpPort, String forwardHost, int forwardPort, int bandwidth) {
        this.udpPort = udpPort;
        this.forwardHost = forwardHost;
        this.forwardPort = forwardPort;
        setBandwidth(bandwidth);

        System.out.printf("BW:  %9d%10d\n", count, bandwidthBits);
        System.out.printf("Verbose: %d\n", Verbose.level);

        try {
            osMBean =  java.lang.management.ManagementFactory.newPlatformMXBeanProxy(java.lang.management.ManagementFactory.getPlatformMBeanServer(),  java.lang.management.ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,  OperatingSystemMXBean.class);
        } catch (Exception e) {
            System.err.println("MXBean error: " + e);
        }
    }

    protected void processTraffic() throws IOException {

        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();

        // Setup UDP Sender
        sender = new UDPSender(forwardHost, forwardPort);
        sender.start();

        
        // Timer stuff
        startTime = System.currentTimeMillis();
        lastSecond = startTime;

        // sit and listen
        while (! receiver.isEOF()) {
            lastTime = System.currentTimeMillis();
            
            // get a packet
            DatagramPacket packet = receiver.getPacket();

            if (packet == null) {
                // the receiver has nothing to pass on
                break;
            } else {
                // process the packet
                count++;
                countLastSecond++;
                totalIn += packet.getLength();
                
                datagramProcess(packet);

                int scale = 25;
                
                // Do something every second divided by scale
                if (lastTime - lastSecond >= (1000/scale)) {
                    // do something
                    lastSecond = lastTime;
                    seconds++;

                    // Once per second msg
                    if ((Message.status & Message.BPPFN_SECOND) == Message.BPPFN_SECOND) {
                        System.err.println("seconds: " + ((float)seconds/(float)scale) + " cpu: " + perSecCPU + " usage: " + perSecUsage + " packets: " + countLastSecond + " in: " + (volumeInLastSecond * 8) + " out: " + (volumeOutLastSecond * 8) + " trimmed: " + ((volumeInLastSecond * 8) - (volumeOutLastSecond * 8)));
                    }
                    
                    // update values
                    // traffic
                    volumeInLastSecond = 0;
                    volumeOutLastSecond = 0;
                    countLastSecond = 0;
                    // cpu
                    totalUsage += perSecUsage;
                    perSecUsage = 0;
                    perSecCPU = 0;

                }
            }

        }

        // stop receiver
        receiver.stop();

        // stop sender
        sender.stop();
        
    }

            
    /**
     * Process the recevied Datagram
     */
    protected void datagramProcess(DatagramPacket packet) throws UnknownHostException {
        int length = packet.getLength();
        
        if ((Message.status & Message.PROCESS_DATAGRAM) == Message.PROCESS_DATAGRAM) {
            System.out.printf("IN:   %8d%6d%10d\n", count, length, totalIn);
        }

        // Track time to do thr trim
        DatagramPacket newVal;

        // start time of the trim
        long beforeTime = System.nanoTime();
        long cpuBefore = osMBean.getProcessCpuTime();   // from MXBean - seems more accurate
        
        newVal = trimDatagram(packet);

        long cpuAfter = osMBean.getProcessCpuTime();
        long afterTime = System.nanoTime();

        long elapsedCPU = cpuAfter - cpuBefore;
        long elapsedNS = afterTime - beforeTime;
        
        //if (elapsedNS != elapsedCPU) {
        //    System.out.println("different: " + elapsedCPU + "  /   " + elapsedNS);
        //}

        perSecCPU += elapsedCPU;
        perSecUsage += elapsedNS;

        
        // Check trim return value
        if (newVal == null) {
            // nothing to send
            // so packet is dropped
            if ((Message.status & Message.PROCESS_DATAGRAM) == Message.PROCESS_DATAGRAM) {
                System.out.printf("DROPPED:  %8d%10d\n", count, totalOut);
            }
        } else {
            // send packet
            int newLength = newVal.getLength();
            totalOut += newLength;

            if (length == newLength) {
                // the packet is not changed
                if ((Message.status & Message.PROCESS_DATAGRAM) == Message.PROCESS_DATAGRAM) {
                    System.out.printf("OUT:  %8d%6d%10d\n", count, newLength, totalOut);
                }
            } else {
                // the packet has chunks removed
                if ((Message.status & Message.PROCESS_DATAGRAM) == Message.PROCESS_DATAGRAM) {
                    System.out.printf("OUT*: %8d%6d%10d\n", count, newLength, totalOut);
                }
            }
        
            sender.sendPayload(newVal);
        }
    }

    /**
     * Try and trim the recevied Datagram
     *
     * Return a Datagram to forward Datagram
     * Return null to throw it away.
     */
    protected DatagramPacket trimDatagram(DatagramPacket datagram) {


        try {
            // increase volumeIn
            volumeIn += datagram.getLength();
            volumeInLastSecond += datagram.getLength();
                
            // check if a BPPFn is set
            Optional<byte[]> result = null;
            
            if (bppFn != null) {
                // do the processing and conversion
                result = bppFn.process(count, datagram);
            } else {
                // no bpp fn, so just forward
                result = Optional.empty();
            }
            

            // now patch up the datagram to have modified contents
            // or forward current datagram if result is null
            if (result == null) {
                // we got a null - so drop the packet
                return null;
                
            } else if (! result.isPresent()) {
                // nothnig to do but forward the datagram

                // increase volumeOut
                volumeOut += datagram.getLength();
                volumeOutLastSecond += datagram.getLength();

                // Get the network function to forward the packet
                return datagram;

            } else {
                // we have new payload
                byte[] content = result.get();
                
                if (Verbose.level >= 3) {
                    System.err.println("datagram length = " + datagram.getLength() + " newPayload length = " + content.length);
                }
                    
                DatagramPacket newDatagram = new DatagramPacket(content, content.length);
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

    // Set the BPP  Function
    // @return old BPP function
    public BPPFn setBPPFn(BPPFn bppF) {
        BPPFn old = bppFn;

        bppFn = bppF;

        return old;
    }
    
    // Get the current BPP  Function
    public BPPFn getBPPFn() {
        return bppFn;
    }
    
    // set the bandwidthBits
    // passed in as bits / sec
    public void setBandwidth(int bb) {
        bandwidthBits = bb;
        bandwidth = bandwidthBits >> 3;
        if (Verbose.level >= 2) {
            System.err.println("BPPForwarder: bandwidthBits = " + bandwidthBits + " bandwidth = " + bandwidth);
        }
    }

    // get the bandwidthBits 
    public int getBandwidth() {
        return bandwidthBits;
    }

    // Adjust the bandwidth
    // Returns the old bandwidth
    public int adjustBandwidth(int bb) {
        // Save old bandwidthBits
        int oldBW = bandwidthBits;

        // calculate new bandwidthBits
        setBandwidth(bb);

        System.out.printf("BW:  %9d%10d\n", count, bandwidthBits);

        // Now inform the BPPFn about the new bandwidth
        bppFn.setBandwidth(bandwidthBits);

        return oldBW;
    }
        
}
