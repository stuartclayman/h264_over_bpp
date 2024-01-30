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

public class TestNetR2 {

    static UDPReceiver receiver = null;

    static int count = 0;
    static int total = 0;


    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    public static void main(String[] args) {
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("TestNetR2");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        DatagramPacket packet;

        // Setup UDP Receiver
        receiver = new UDPReceiver(6798);
        receiver.start();


        // Timer stuff
        startTime = System.currentTimeMillis();
        lastTime = System.currentTimeMillis();
        Timer timer = null;

        // set up timer to count throughput
        TimerTask timerTask = new TimerTask() {
                boolean running = true;

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

                        System.err.println("Time: " + elaspsedSecs + "." + elaspsedMS);

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

        // if there is no timer, start one
        if (timer == null) {
            timer = new Timer();
            timer.schedule(timerTask, 1000, 1000);
        }

        ChunkDepacketizer depacketizer = new SimpleSVCDepacketizer();

        while ((packet = receiver.getPacket()) != null) {
            lastTime = System.currentTimeMillis();
            
            SVCChunkInfo chunk = (SVCChunkInfo)depacketizer.convert(packet);

            count++;

            total += chunk.offset();

            //printPacket(packet, count, total);

            // A good test of the system
            // Can we print using the printChunk()
            // from a class on the server side
            TestMNP2.printChunk(chunk, count, total, packet.getData().length);
            //TestMNP1.printChunk(chunk, count, total);

        }

        System.err.println("end of loop");

        timer.cancel();

        // stop receiver
        receiver.stop();

    }
    
    protected static void printPacket(DatagramPacket packet, int count, int total) {
        byte[] packetBytes = packet.getData();
        
        // Now extract the header
        // 24 bits for nalNo
        byte b0 = packetBytes[0];
        byte b1 = packetBytes[1];
        byte b2 = packetBytes[2];

        // 7 bits for count + 1 bit for type
        byte b3 = packetBytes[3];

        //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[0], packetBytes[1], packetBytes[2], packetBytes[3]);


        int nalNo = 0 | ((b0 << 16)  & 0x00FF0000) | ((b1 << 8) & 0x0000FF00) | ((b2 << 0) & 0x000000FF);
        

        int nalCount = (b3 & 0xFE) >> 1;
        int type = b3 & 0x01;

        NALType nalType;
        
        if (type == 0 || type == 1)  {
            nalType = (type == 0 ? NALType.VCL : NALType.NONVCL);
        } else {
            throw new Error("Invalid NALType number " + type);
        }
        


        int packetLen = packet.getLength();            // no of bytes

        int payloadLen = packet.getLength() - 4;            // no of payload bytes

        for (int b=4; b<packetLen; b++) {
            System.out.write(packetBytes[b]);
        }
    }
    
}
