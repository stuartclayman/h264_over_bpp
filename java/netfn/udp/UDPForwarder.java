package netfn.udp;

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

public class UDPForwarder implements ManagementListener {

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
    int packetsPerSecond = 100;  // default: 100

    UDPReceiver receiver = null;
    UDPSender sender = null;
    UDPFn udpFn = null;

    int count = 0;
    int totalIn = 0;
    int totalOut = 0;


    // keep time
    long startTime = 0;
    long lastTime = 0;

    public UDPForwarder(int udpPort, String forwardHost, int forwardPort, float bandwidth, int packetsPerSecond) {
        this.udpPort = udpPort;
        this.forwardHost = forwardHost;
        this.forwardPort = forwardPort;
        setBandwidth(bandwidth);
        setPacketsPerSecond(packetsPerSecond);
    }

    protected void processTraffic() throws IOException {

        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();

        // Setup UDPFn
        udpFn = new UDPFn(bandwidthBits, packetsPerSecond);

        // Setup UDP Sender
        sender = new UDPSender(forwardHost, forwardPort);
        sender.start();

        
        // Timer stuff
        startTime = System.currentTimeMillis();
        lastTime = System.currentTimeMillis();


        while (! receiver.isEOF()) {
            lastTime = System.currentTimeMillis();
            
            DatagramPacket packet = receiver.getPacket();

            if (packet == null) {
                // the receiver has gone away
                break;
            } else {
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

            
    protected void datagramProcess(DatagramPacket packet) throws UnknownHostException {
        int length = packet.getLength();
        
        System.out.printf("IN:   %8d%6d%10d\n", count, length, totalIn);

        DatagramPacket newVal = udpFn.datagramProcess(packet);

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

    // set the bandwidthBits 
    // convert float 0.8 Mbps -> 838860 bits
    public void setBandwidth(float bb) {
        bandwidthBits = (int)(bb * 1024 * 1024);
        bandwidth = bandwidthBits >> 3;
        if (Verbose.level >= 2) {
            System.err.println("UDPForwarder: bandwidthBits = " + bandwidthBits + " bandwidth = " + bandwidth);
        }
    }

    // get the bandwidthBits 
    public int getBandwidth() {
        return bandwidthBits;
    }

    // Adjust the bandwidth
    // Returns the old bandwidth
    public int adjustBandwidth(float bb) {
        int oldBW = bandwidthBits;

        setBandwidth(bb);

        System.out.printf("BW:  %9d%10d\n", count, bandwidthBits);

        // Now inform the UDPFn about the new bandwidth
        udpFn.setBandwidth(bandwidthBits);

        return oldBW;
    }
    

    protected void setPacketsPerSecond(int c) {
        packetsPerSecond = c;
    }
            
    
}
