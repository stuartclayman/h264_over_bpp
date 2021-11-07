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

// Using the UDPSender
// with a BPP packetizer
public class BPPSend {

    static UDPSender sender = null;
    static int sleep = 7;       // default sleep (in milliseconds) between chunks
    static int packetsPerSecond = 0;
    static ChunkPacketizer packetizer = null;
    static ChunkSizeCalculator calculator = null;
    static int columns = 80;    // default no of cols on terminal

    public static void main(String[] args) {
        if (args.length == 1) {
            String filename = args[0];
            
            try {
                processFile(filename);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else if (args.length >= 2) {
            // have flags too

            int argc = 0;

            while (argc < args.length-1) {
                String arg0 = args[argc];

            
                if (arg0.equals("-e")) {
                    // Use Even chunk packing strategy
                    argc++;
                    calculator = new EvenSplit();

                } else if (arg0.equals("-d")) {
                    // Use Dynamic split chunk packing strategy
                    argc++;
                    calculator = new DynamicSplit();

                } else if (arg0.equals("-i")) {
                    // Use In order chunk  strategy
                    argc++;
                    calculator = new InOrder();

                } else if (arg0.equals("-p")) {
                    // Use In order chunk and full packing strategy
                    argc++;
                    calculator = new InOrderPacked();

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
             
                } else {
                    usage();
                }
            }
            
            String filename = args[argc];
            
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
        System.err.println("BPPSend [-e|-d|-i|-p|-s sleep|-r rate] filename");
        System.exit(1);
    }


    protected static void processFile(String filename) throws IOException {
        // Setup UDP Sender
        sender = new UDPSender("localhost", 6799);
        sender.start();
        

        int count = 0;
        int total = 0;
        ChunkInfo chunk = null;
        
        // Configure ChunkPacketizer
        // 1500 byte packets / 3 chunks
        packetizer = new BPPPacketizer(1500, 3);

        // Open a H264InputStream
        H264InputStream str = new H264InputStream(new FileInputStream(filename));
        // MultiNALProcessor - payload size from packetizer, 3 chunks
        MultiNALProcessor nalProcessor = new MultiNALProcessor(str, packetizer.getPayloadSize(), 3);

        // did user specify a ChunkSizeCalculator
        if (calculator != null) {
            nalProcessor.setChunkSizeCalculator(calculator);
        }

        while (nalProcessor.hasNext()) {

            chunk = nalProcessor.next();
            count++;

            total += chunk.offset();

            //printChunk(chunk, count, total);
            printChunk(chunk, count, total, nalProcessor.getPayloadSize());

            
            System.err.printf("%-6d", count);

            // now send it
            sender.sendPayload(packetizer.convert(count, chunk));

            // fix sleep from student code - awaiting proper algorithm
            try { 
                Thread.sleep(sleep);
            } catch (InterruptedException ie) {
            }
        }

        // close the stream
        str.close();

        // stop sender
        sender.stop();

    }
    
    protected static void printChunk(ChunkInfo chunk, int count, int total) {

        System.out.printf("%-8d", count);               // N
        System.out.printf("%-10s", chunk.getNALType());               // type
        System.out.printf(" %-5d", chunk.offset());         // no of bytes
        System.out.printf(" %-10d", total);             // total bytes

        System.out.println();
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
