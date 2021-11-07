package cc.clayman.app;

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

// Collect packets using UDPChunkStreamer

public class BPPListen {

    static UDPReceiver receiver = null;
    static UDPChunkStreamer streamer = null;

    static int count = 0;
    static int total = 0;


    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    static int udpPort = 6798;

    static int columns = 80;    // default no of cols on terminal

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
        System.err.println("BPPListen [port]");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        // and the ChunkStreamer
        streamer = new UDPChunkStreamer(receiver);
        streamer.start();


        // Timer stuff
        startTime = System.currentTimeMillis();
        lastTime = System.currentTimeMillis();

        // Wait for 5 secs before ending
        TimerTask timerTask = new TimedCount(streamer, 5);

        Timer timer = null;

        // if there is no timer, start one
        if (timer == null) {
            timer = new Timer();
            timer.schedule(timerTask, 1000, 1000);
        }
        

        while (streamer.hasNext()) {
            lastTime = System.currentTimeMillis();
            
            ChunkInfo chunk = streamer.next();

            if (chunk != null) {
                count++;
                total += chunk.offset();

                // A good test of the system
                // Can we print using the printChunk()
                // from a class on the server side
                printChunk(chunk, count, total, 1500);
            }

        }

        timer.cancel();

        // stop receiver
        streamer.stop();

    }
        
    protected static void printChunk(ChunkInfo chunk, int count, int total, int payloadSize) {
        
        // try and find the no of columns from the Environment
        String columnsEnv = System.getenv("COLUMNS");

        if (columnsEnv != null) {
            int columnVal = Integer.parseInt(columnsEnv);

            columns = columnVal;
        }

        System.out.printf("%-8d", count);                     // N
        System.out.printf("%-10s", chunk.getNALType());       // type

        // used up 18 chars

        ChunkDisplay displayer = new ChunkDisplay(columns - 22, payloadSize);
        displayer.display(chunk);
        
        System.out.println(" ");
                    
    }
    

    // set up timer to count throughput
    private static class TimedCount extends TimerTask {
        boolean running = true;
        ChunkStreamer streamer;
        // time to wait - in seconds - for no traffic
        int timeOut;
        
        public TimedCount(ChunkStreamer s, int timeOut) {
            streamer = s;
            this.timeOut = timeOut;
        }

        @Override
        public void run() {
            if (running) {

                long thisTime = System.currentTimeMillis();


                if (count != 0 && ((thisTime - lastTime) / 1000) >= timeOut) {
                    // no recv after 5 secs
                    System.err.println("stopping");
                    System.out.flush();
                    streamer.stop();
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

    }


}
