package netfn.bpp;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;
import java.net.UnknownHostException;

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

    public BPPForwarder(int udpPort, String forwardHost, int forwardPort, int bandwidth) {
        this.udpPort = udpPort;
        this.forwardHost = forwardHost;
        this.forwardPort = forwardPort;
        setBandwidth(bandwidth);

        System.out.printf("BW:  %9d%10d\n", count, bandwidthBits);

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
        lastTime = System.currentTimeMillis();

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
                totalIn += packet.getLength();
                
                datagramProcess(packet);
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
        
        System.out.printf("IN:   %8d%6d%10d\n", count, length, totalIn);

        DatagramPacket newVal = trimDatagram(packet);

        if (newVal == null) {
            // nothing to send
        } else {
            // send packet
            int newLength = newVal.getLength();
            totalOut += newLength;

            if (length == newLength) {
                // the packet is not changed
                System.out.printf("OUT:  %8d%6d%10d\n", count, newLength, totalOut);
            } else {
                // the packet has chunks removed
                System.out.printf("OUT*: %8d%6d%10d\n", count, newLength, totalOut);
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

        count++;
        countLastSecond++;

        try {
            // increase volumeIn
            volumeIn += datagram.getLength();
            volumeInLastSecond += datagram.getLength();
                
            // check if a BPPFn is set
            byte[] result = null;
            
            if (bppFn != null) {
                // do the processing and conversion
                result = bppFn.process(count, datagram);
            } else {
                // no bpp fn, so just forward
                result = null;
            }
            

            // now patch up the datagram to have modified contents
            // or forward current datagram if result is null
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
