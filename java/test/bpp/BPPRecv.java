package test.bpp;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALProcessor;
import cc.clayman.processor.UDPChunkStreamer;
import cc.clayman.net.*;
import cc.clayman.terminal.ChunkDisplay;
import cc.clayman.util.Verbose;

// Collect packets with UDPReceiver

public class BPPRecv {

    static UDPReceiver receiver = null;
    static BPPFn bppFn = null;

    static int count = 0;
    static int totalIn = 0;
    static int totalOut = 0;


    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    // listen port
    static int udpPort = 6799;

    static int columns = 80;    // default no of cols on terminal

    // in bits
    static int bandwidthBits = 1024 * 1024;   // default: 1 Mb
    static int bandwidth = 0;
    static int packetsPerSecond = 100;  // default: 100
    
    
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
        } else if (args.length >= 1) {
            
            // have flags too

            int argc = 0;

            while (argc < args.length) {  // allow for port at end
                String arg0 = args[argc];

                if (arg0.equals("-p")) {
                    // Port
                    argc++;

                    String val = args[argc];
                    udpPort = Integer.parseInt(val);

                } else if (arg0.equals("-c")) {            
                    // columns
                    argc++;

                    String val = args[argc];
                    columns = Integer.parseInt(val);

                } else if (arg0.equals("-b")) {   /* -b bandwidthBits -- the estimated bandwidth (in Mbits) e.g 0.8 or 1.2 */
                    // get next arg
                    argc++;

                    String countValue =  args[argc];

                    try {
                        setBandwidth(Float.parseFloat(countValue));
                    } catch (Exception e) {
                        System.err.println("Bad bandwidth value: " + countValue);
                    }

                 } else if (arg0.equals("-r")) {   /* -r rate -- the no of packets per second */
                    // get next arg
                    argc++;

                    String countValue =  args[argc];

                    try {
                        setPacketsPerSecond(Integer.parseInt(countValue));
                    } catch (Exception e) {
                        System.err.println("Bad packets per second " + countValue);
                    }

                } else if (arg0.startsWith("-v")) {
                    if (arg0.equals("-v")) {
                        Verbose.level = 1;
                    } else  if (arg0.equals("-vv")) {
                        Verbose.level = 2;
                    } else  if (arg0.equals("-vvv")) {
                        Verbose.level = 3;
                    }
                } else {
                    usage();
                }

                argc++;

            }

        } else {
            usage();
        }

        if (Verbose.level >= 2) {
            System.err.println("Listen on port: " + udpPort);
            System.err.println("Columns: " + columns);
            System.err.println("Bandwidth in bits: " + bandwidthBits);
            System.err.println("Packets per second: " + packetsPerSecond);
        }
        
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("BPPListen [-b bandwidth] [-p port] [-c cols]");
        System.exit(1);
    }

    protected static void setBandwidth(float b) {
        bandwidthBits = (int)(b * 1024 * 1024);
        bandwidth = bandwidthBits >> 3;
        if (Verbose.level >= 2) {
            System.err.println("BPPFn: bandwidthBits = " + bandwidthBits + " bandwidth = " + bandwidth);
        }
    }

    protected static void setPacketsPerSecond(int c) {
        packetsPerSecond = c;
    }

    protected static void processTraffic() throws IOException {
        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();

        // Setup BPPFn
        bppFn = new BPPFn(bandwidthBits, packetsPerSecond);

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

    }
        
    protected static void datagramProcess(DatagramPacket packet) {
        int length = packet.getLength();
        
        System.out.printf("IN:   %8d%6d%10d\n", count, length, totalIn);

        DatagramPacket newVal = bppFn.datagramProcess(packet);

        int newLength = newVal.getLength();
        totalOut += newLength;

        if (length == newLength) {
            // the packet is not changed
            System.out.printf("OUT:  %8d%6d%10d\n", count, newLength, totalOut);
        } else {
            // the packet has chunks removed
            System.out.printf("OUT*: %8d%6d%10d\n", count, newLength, totalOut);
        }
            

            
                    
    }

}
