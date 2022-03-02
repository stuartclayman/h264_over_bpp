package test;

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
import cc.clayman.net.*;

// A second test of the UDPReceiver

public class TestNetRB1 {

    static UDPReceiver receiver = null;

    static int count = 0;
    static int total = 0;


    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    static int udpPort = 6798;


    public static void main(String[] args) {
        if (args.length == 0) {
        } else if (args.length == 1) {
            String val = args[0];
            udpPort = Integer.parseInt(val);
            System.err.println("Listen on port: " + udpPort);
            
        } else {
            usage();
        }

        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("TestNetRB1 [port]");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        DatagramPacket packet;

        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();


        // Timer stuff
        startTime = System.currentTimeMillis();
        lastTime = System.currentTimeMillis();
        Timer timer = null;

        // set up timer to count throughput
        TimerTask timerTask = new TimedCount(receiver);

        // if there is no timer, start one
        if (timer == null) {
            timer = new Timer();
            timer.schedule(timerTask, 1000, 1000);
        }
        
        ChunkDepacketizer depacketizer = new BPPDepacketizer();

        while ((packet = receiver.getPacket()) != null) {
            lastTime = System.currentTimeMillis();
            
            count++;

            SVCChunkInfo chunk = (SVCChunkInfo)depacketizer.convert(packet);

            total += chunk.offset();

            //printPacket(packet, count, total);

            // A good test of the system
            // Can we print using the printChunk()
            // from a class on the server side
            TestMNP2.printChunk(chunk, count, total, packet.getData().length);
            //TestMNP1.printChunk(chunk, count, total);

        }

        timer.cancel();

        // stop receiver
        receiver.stop();

    }
    
    protected static void printPacket(DatagramPacket packet, int count, int total) {
        byte[] packetBytes = packet.getData();

        int packetLen = packet.getLength();            // no of bytes

        int payloadLen = packet.getLength() - 4;            // no of payload bytes

        for (int b=4; b<packetLen; b++) {
            System.out.write(packetBytes[b]);
        }
    }


    // set up timer to count throughput
    private static class TimedCount extends TimerTask {
        boolean running = true;
        UDPReceiver receiver;
        
        public TimedCount(UDPReceiver r) {
            receiver = r;
        }


        @Override
        public void run() {
            if (running) {

                long thisTime = System.currentTimeMillis();


                if (count != 0 && ((thisTime - lastTime) / 1000) >= 5) {
                    // no recv after 5 secs
                    System.err.println("stopping");
                    System.out.flush();
                    receiver.stop();
                    cancel();
                }
                        
                long elaspsedSecs = (thisTime - startTime)/1000;
                long elaspsedMS = (thisTime - startTime)%1000;

                //System.err.println("Time: " + elaspsedSecs + "." + elaspsedMS);

            }
        }

        @Override
        public boolean cancel() {
            if (running) {
                running = false;
            }

            return running;
        }

        @Override
        public long scheduledExecutionTime() {
            return 0;
        }

    };

    
}
