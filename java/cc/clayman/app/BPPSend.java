package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.util.regex.*;

import cc.clayman.bpp.BPP;
import cc.clayman.bpp.BPPFunction;
import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALProcessor;
import cc.clayman.net.*;
import cc.clayman.terminal.ChunkDisplay;
import cc.clayman.terminal.SVCChunkDisplay;
import cc.clayman.util.Verbose;

/**
 * BPPSend sends SVC video in BPP packets.
 * Using the UDPSender with a BPPSVCpacketizer.
 */
public class BPPSend {

    // Default is STDIN
    static String filename = "-";

    // send host
    static String host = "localhost";
    // send port
    static int udpPort = 6799;

    static UDPSender sender = null;
    static float sleep = 7.0f;            // default sleep (in milliseconds) between chunks
    static boolean adaptiveSleep = true;  // show we do adaptive sleep
    static int packetsPerSecond = 0;      // no of packets per second
    static int columns = 80;              // default no of cols on terminal
    static int packetSize = 1500;         // packet size
    static int nalsPerFrame = 3;          // no of NALs per frame
    static int videoKbps = 1094;          // the bandwidth of the video file
    static int threshold = 5;             // default threshold
    static int fnSpec = BPP.Function.NONE;    // No special functions called in network node

    static ChunkPacketizer packetizer = null;
    static ChunkSizeCalculator calculator = null;
    

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

                } else if (arg0.equals("-a")) {
                    // Use adaptive sleep approach
                    argc++;
                    adaptiveSleep = true;

                } else if (arg0.equals("-s")) {
                    // Sleep (in milliseconds) between chunks
                    argc++;

                    String val = args[argc];
                    sleep = Float.parseFloat(val);
                    packetsPerSecond = (int)(1000f / sleep);
                    adaptiveSleep = false;
             
                } else if (arg0.equals("-r")) {
                    // Packet sending rate - packets per second
                    argc++;

                    String val = args[argc];
                    packetsPerSecond = Integer.parseInt(val);
                    sleep = 1000f / packetsPerSecond;
                    adaptiveSleep = false;
             
                } else if (arg0.equals("-c")) {            
                    // columns
                    argc++;

                    String val = args[argc];
                    columns = Integer.parseInt(val);

                    
                } else if (arg0.equals("-N")) {            
                    // nals per frame
                    argc++;

                    String val = args[argc];
                    nalsPerFrame = Integer.parseInt(val);

                } else if (arg0.equals("-B")) {            
                    // bandwidth of video in kbps
                    argc++;

                    String val = args[argc];
                    videoKbps = Integer.parseInt(val);

                } else if (arg0.equals("-T")) {            
                    // threshold for significance values
                    argc++;

                    String val = args[argc];
                    threshold = Integer.parseInt(val);

                } else if (arg0.startsWith("-P")) {

                    if (arg0.equals("-Pe")) {
                        // Use Even chunk packing strategy
                        calculator = new EvenSplit();

                    } else if (arg0.equals("-Pd")) {
                        // Use Dynamic split chunk packing strategy
                        calculator = new DynamicSplit();

                    } else if (arg0.equals("-Pi")) {
                        // Use In order chunk  strategy
                        calculator = new InOrder();

                    } else if (arg0.equals("-Pf")) {
                        // Use In order chunk and full packing strategy
                        calculator = new InOrderPacked();

                    } else {
                        // Unknown packing option
                    }


                } else if (arg0.startsWith("-F")) {
                    if (arg0.startsWith("-Fr")) {
                        // RelaxThreshold functions
                        
                        // check if arg looks like -Fr:value
                            
                        // we need to split the arg and grab the period
                        String regexp = "-Fr:(\\d)";
                        Pattern pattern = Pattern.compile(regexp);
                        Matcher matcher = pattern.matcher(arg0);
                        
                        if (matcher.matches()) {
                            String group1 = matcher.group(1);

                            int arg = Integer.parseInt(group1);

                            fnSpec = new BPPFunction.RelaxThreshold(arg).representation();

                        } else {
                            System.err.println("Function spec: illegal arg. Expected -Fr:arg e.g. -Fr:1");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Function spec: illegal arg. Expected -Fr:1");
                        System.exit(1);

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
            System.err.println("Send host: " + host);
            System.err.println("Send on port: " + udpPort);
            System.err.println("NALs per frame: " + nalsPerFrame);
            System.err.println("Packet size: " + packetSize);
            System.err.println("Sleep: " + sleep);
            System.err.println("Adaptive Sleep: " + (adaptiveSleep ? "ON" : "OFF"));
            System.err.println("Threshold: " + threshold);
            System.err.println("Video kbps: " + videoKbps);
            System.err.println("Columns: " + columns);
        }
        
        try {
            processFile(filename);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("BPPSend [-f [-|filename]] [-h host]  [-p port] [-s sleep|-r rate|-a] [-z packetSize] [-N nals] [-B bandwidth] [-T threshold] [-Pe|-Pd|-Pi|-Pf]");
        System.exit(1);
    }


    protected static void processFile(String filename) throws IOException {
        int count = 0;  // packet count
        int total = 0;  // total sent
        int countThisSec = 0;  // packet count this second
        int sentThisSec = 0;   // amount sent this second
        int expected = videoKbps * 1024 / 8;   // expected amount to send per second
        int seconds = 0;       // no of seconds
        long secondStart = 0;   // when did the second start

        SVCChunkInfo chunk = null;
        
        // Setup UDP Sender
        sender = new UDPSender(host, udpPort);
        sender.start();
        
        // Configure ChunkPacketizer
        // 1500 byte packets / 3 chunks
        packetizer = new BPPSVCPacketizer(packetSize, nalsPerFrame);

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

        
        // MultiNALProcessor - payload size from packetizer, 3 chunks
        MultiNALProcessor nalProcessor = new MultiNALProcessor(str, packetizer.getPayloadSize(), nalsPerFrame);

        // did user specify a ChunkSizeCalculator
        if (calculator != null) {
            nalProcessor.setChunkSizeCalculator(calculator);
        }

        // Setup nalProcessor printer
        if (Verbose.level >= 1) {
            nalProcessor.onChunk(new SVCChunkInfoPrinter());
        }

        // set secondStart
        secondStart = System.currentTimeMillis();

        // use default sleep
        float lastSleep = sleep;

        // Get Chunks from the nalProcessor
        while (nalProcessor.hasNext()) {

            chunk = nalProcessor.next();
            count++;

            total += chunk.offset();

            //printChunk(chunk, count, total);
            printChunk(chunk, count, total, nalProcessor.getPayloadSize());

            
            //System.err.printf("%-6d", count);
            //infoChunk(chunk, count, total);

            // Condition: depends on the command

            // Threshold: 0 - 15
            // This is used by the network node to drop chunks
            
            // now send it
            sender.sendPayload(packetizer.convert(count, BPP.Command.WASH, BPP.Condition.LIMITEDFN, threshold, fnSpec, chunk));

            // sleep a bit
            try {
                if (adaptiveSleep) {

                    long now = System.currentTimeMillis();
                    long timeOffset = now - secondStart;
                    float secondPart = (float)timeOffset / 1000;

                    if (timeOffset >= 1000) {
                        // we crossed a second boundary
                        if (Verbose.level >= 2) {
                            System.err.printf("SENT_THIS_SEC: second: %2.3f expected: %7d  packetsThisSec: %3d  sentThisSec: %7d diff: %7d\n", seconds + secondPart, expected, countThisSec, sentThisSec, (expected-sentThisSec));
                        }

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
                    //int idealPacketsPerSecond  = expected / packetSize;
                    //int idealSleep = 1000 / idealPacketsPerSecond;
                    int idealSentThisSec = (int) (expected * secondPart);
                    int behind = idealSentThisSec - sentThisSec;
                    float value =  0; 

                    if (behind > 0) {
                        // behind
                        if (behind > packetSize) {
                            // a bit too much behind
                            value = (lastSleep == 0 ? 0 : lastSleep-1);
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

                } else {
                    if (Verbose.level >= 3) {
                        System.err.print("SLEEP: " + sleep);
                    }

                    // Get second part and nanosecond part
                    int secondPart = (int)sleep;
                    int nanosecondPart = (int)((sleep - secondPart) * 1000000);
                    Thread.sleep(secondPart, nanosecondPart);
                }
                
            } catch (InterruptedException ie) {
            }
        }

        // close the stream
        str.close();

        // stop sender
        sender.stop();

    }
    
    protected static void infoChunk(SVCChunkInfo chunk, int count, int total) {
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
