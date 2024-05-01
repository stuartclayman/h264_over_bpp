package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.*;
import cc.clayman.net.*;
import cc.clayman.terminal.ChunkDisplay;
import cc.clayman.terminal.SVCChunkDisplay;
import cc.clayman.util.Verbose;

// A UDP sender
// With a Simple packetizer
public class UDPSend {

    // Default is STDIN
    static String filename = "-";

    // send host
    static String host = "localhost";
    // send port
    static int udpPort = 6799;

    static UDPSender sender = null;
    static float sleep = 7.0f;       // default sleep (in milliseconds) between chunks
    static ChunkPacketizer packetizer = null;
    static int columns = 80;         // default no of cols on terminal
    static int packetSize = 1500;    // packet size

    static int videoKbps = 1094;          // the bandwidth of the video file

    
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
        } else if (args.length >= 1) {
            // have flags too

            int argc = 0;

            while (argc < args.length) {
                String arg0 = args[argc];

            
                if (arg0.equals("-f")) {
                    // Input filename
                    argc++;
                    filename = args[argc];

                } else if (arg0.equals("-h")) {
                        // Host
                        argc++;
                        host = args[argc];

                } else if (arg0.equals("-p")) {
                    // Port
                    argc++;

                    String val = args[argc];
                    udpPort = Integer.parseInt(val);

                } else if (arg0.equals("-z")) {            
                    // packet size
                    argc++;

                    String val = args[argc];
                    packetSize = Integer.parseInt(val);
                } else if (arg0.equals("-s")) {
                    // Sleep (in milliseconds) between chunks
                    argc++;

                    String val = args[argc];
                    sleep = Float.parseFloat(val);
                    argc++;
             
                } else if (arg0.equals("-c")) {            
                    // columns
                    argc++;

                    String val = args[argc];
                    columns = Integer.parseInt(val);

                } else if (arg0.equals("-B")) {            
                    // bandwidth of video in kbps
                    argc++;

                    String val = args[argc];
                    videoKbps = Integer.parseInt(val);

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
            
            
            if (Verbose.level >= 2) {
                System.err.println("Send on port: " + udpPort);
                System.err.println("Packet size: " + packetSize);
                System.err.println("Video kbps: " + videoKbps);
                System.err.println("Columns: " + columns);
            }
        
            try {
                processFile(filename);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            usage();
        }
    }

    static void usage() {
        System.err.println("UDPSend  [-f [-|filename]] [-s sleep] [-z packetSize] [-h host] [-p port] [-B bandwidth]");
        System.exit(1);
    }


    protected static void processFile(String filename) throws IOException {
        int countThisSec = 0;  // packet count this second
        int sentThisSec = 0;   // amount sent this second
        int expected = videoKbps * 1024 / 8;   // expected amount to send per second
        int seconds = 0;       // no of seconds
        long secondStart = 0;   // when did the second start

        // Setup UDP Sender
        sender = new UDPSender(host, udpPort);
        sender.start();
        

        int count = 0;
        int total = 0;
        SVCChunkInfo chunk = null;
        
        // Configure ChunkPacketizer
        packetizer = new SimpleSVCPacketizer(packetSize);

        // Open a H264InputStream
        H264InputStream str = null;

        if (filename.equals("-")) {
            str = new H264InputStream(System.in);

            if (Verbose.level >= 2) {
                System.err.println("Input stream: STDIN" );
            }
                           
        } else {
            str = new H264InputStream(new FileInputStream(filename));

            if (Verbose.level >= 2) {
                System.err.println("Input file: " + filename);
            }                    
        }

        
        // MultiNALProcessor - payload size of 1468
        MultiNALProcessor nalProcessor = new MultiNALProcessor(str, packetizer.getPayloadSize(), 1);

        // Setup nalProcessor printer
        if (Verbose.level >= 1) {
            nalProcessor.onChunk(new SVCChunkInfoPrinter());
        }

        // use default sleep
        float lastSleep = sleep;

        while (nalProcessor.hasNext()) {

            // The MultiNALProcessor returns SVCChunkInfo objects
            chunk = (SVCChunkInfo)nalProcessor.next();
            count++;

            total += chunk.offset();

            //printChunk(chunk, count, total);
            printChunk(chunk, count, total, nalProcessor.getPayloadSize());

            // now send it
            // condition and threshold set to 0
            sender.sendPayload(packetizer.convert(count, 0, 0, chunk));
    
            /*
             * original approach
             
            // sleep a bit
            try { 
                // Get second part and nanosecond part
                int secondPart = (int)sleep;
                int nanosecondPart = (int)((sleep - secondPart) * 1000000);
                Thread.sleep(secondPart, nanosecondPart);
            } catch (InterruptedException ie) {
            }
            */

            try {
                long now = System.currentTimeMillis();
                long timeOffset = now - secondStart;
                float secondPart = (float)timeOffset / 1000;

                if (timeOffset >= 1000) {
                    // we crossed a second boundary
                    seconds++;
                    secondStart = now;
                    countThisSec = 0;
                    sentThisSec = 0;
                    secondPart = 0;
                    lastSleep = sleep;
                }

                // how much is sent
                int thisPacket = chunk.offset();
                countThisSec++;
                sentThisSec += thisPacket;

                // work out sleep if ideally send packetSize
                int idealSentThisSec = (int) (expected * secondPart);
                int behind = idealSentThisSec - sentThisSec;
                float value =  0; 

                if (behind > 0) {
                    // behind
                    if (behind > packetSize) {
                        // a bit too much behind
                        value = (lastSleep == 1 ? 1 : lastSleep-1);
                    } else {
                        value = lastSleep;
                    }
                } else {
                    // ahead
                    if (behind < -packetSize) {
                        // a bit too much ahead
                        value = lastSleep + 1;
                    } else {
                        value = lastSleep;
                    }
                }
                            

                lastSleep = value;

                if (Verbose.level >= 2) {
                    System.err.printf("SLEEP: second: %2.3f  packetsThisSec: %3d  sentThisSec: %7d  idealSentThisSec: %7d  behindBytes: %6d  sleep: %2f\n", seconds + secondPart, countThisSec, sentThisSec, idealSentThisSec, behind, value);
                }
                                                            
                Thread.sleep((int)value);

            } catch (InterruptedException ie) {
            }

        }
        

        // close the stream
        str.close();

        // stop sender
        sender.stop();

    }
    
    protected static void printChunk(SVCChunkInfo chunk, int count, int total) {

        System.out.printf("%-8d", count);               // N
        System.out.printf("%-10s", chunk.getNALType());               // type
        System.out.printf(" %-5d", chunk.offset());         // no of bytes
        System.out.printf(" %-10d", total);             // total bytes

        System.out.println();
    }
    
    protected static void printChunk(SVCChunkInfo chunk, int count, int total, int payloadSize) {
        
        // try and find the no of columns from the Environment
        String columnsEnv = System.getenv("COLUMNS");

        if (columnsEnv != null) {
            int columnVal = Integer.parseInt(columnsEnv);

            columns = columnVal;
        }

        System.out.printf("%-8d", count);                     // N
        System.out.printf("%-10s", chunk.getNALType());       // type

        // used up 18 chars

        ChunkDisplay displayer = new SVCChunkDisplay(columns - 22, payloadSize);
        displayer.display(chunk);
        
        System.out.println(" ");
                    
    }
}
