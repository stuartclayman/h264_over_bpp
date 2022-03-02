package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALProcessor;
import cc.clayman.net.*;
import cc.clayman.terminal.ChunkDisplay;
import cc.clayman.util.Verbose;

// Using the UDPSender
// with a BPP packetizer
public class BPPSend {

    // Default is STDIN
    static String filename = "-";

    // send host
    static String host = "localhost";
    // send port
    static int udpPort = 6799;

    static UDPSender sender = null;
    static int sleep = 7;                 // default sleep (in milliseconds) between chunks
    static boolean adaptiveSleep = false; // show we do adaptive sleep
    static int packetsPerSecond = 0;      // no of packets per second
    static int columns = 80;              // default no of cols on terminal
    static int packetSize = 1500;         // packet size
    static int nalsPerFrame = 3;          // no of NALs per frame

    static ChunkPacketizer packetizer = null;
    static ChunkSizeCalculator calculator = null;
    

    public static void main(String[] args) {
        if (args.length == 0) {
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
                    sleep = Integer.parseInt(val);
                    argc++;
             
                } else if (arg0.equals("-r")) {
                    // Packet sending rate - packets per second
                    argc++;

                    String val = args[argc];
                    packetsPerSecond = Integer.parseInt(val);
                    sleep = 1000 / packetsPerSecond;
                    argc++;
             
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
            System.err.println("Columns: " + columns);
            System.err.println("Adaptive Sleep: " + (adaptiveSleep ? "ON" : "OFF"));
        }
        
        try {
            processFile(filename);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("BPPSend [-f [-|filename]] [-h host]  [-p port] [-s sleep|-r rate|-a] [-z packetSize] [-N nals] [-Pe|-Pd|-Pi|-Pf]");
        System.exit(1);
    }


    protected static void processFile(String filename) throws IOException {
        int count = 0;
        int total = 0;
        ChunkInfo chunk = null;
        
        // Setup UDP Sender
        sender = new UDPSender(host, udpPort);
        sender.start();
        
        // Configure ChunkPacketizer
        // 1500 byte packets / 3 chunks
        packetizer = new BPPPacketizer(packetSize, nalsPerFrame);

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
            nalProcessor.onChunk(new ChunkInfoPrinter());
        }

        // Get Chunks from the nalProcessor
        while (nalProcessor.hasNext()) {

            chunk = nalProcessor.next();
            count++;

            total += chunk.offset();

            //printChunk(chunk, count, total);
            printChunk(chunk, count, total, nalProcessor.getPayloadSize());

            
            //System.err.printf("%-6d", count);
            //infoChunk(chunk, count, total);

            // now send it
            sender.sendPayload(packetizer.convert(count, chunk));

            // fix sleep from student code - awaiting proper algorithm
            try {
                if (adaptiveSleep) {
                    int value = (chunk.offset() / (packetSize / sleep)) + 1;
                    Thread.sleep(value);

                    if (Verbose.level >= 3) {
                        System.err.print("sleep: " + value);
                    }
                } else {
                    if (Verbose.level >= 3) {
                        System.err.print("sleep: " + sleep);
                    }

                    Thread.sleep(sleep);
                }
                
            } catch (InterruptedException ie) {
            }
        }

        // close the stream
        str.close();

        // stop sender
        sender.stop();

    }
    
    protected static void infoChunk(ChunkInfo chunk, int count, int total) {
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

 }
