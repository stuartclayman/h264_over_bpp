package test;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.terminal.ChunkDisplay;
import cc.clayman.terminal.SVCChunkDisplay;
import cc.clayman.processor.MultiNALProcessor;

// A first test of the MultiNALProcessor and ChunkDisplay to terminal
public class TestMNP2 {

    static int columns = 80;    // default no of cols on terminal
    static int sleep = 7;       // default sleep (in milliseconds) between chunks
    static int qualityLayers = 3;   // default is 3
    static ChunkSizeCalculator calculator = null;

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
             
                } else if (arg0.equals("-l")) {
                    // No of quality Layers in the video
                    argc++;

                    String val = args[argc];
                    qualityLayers = Integer.parseInt(val);
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
        System.err.println("TestMNP2 [-e|-d|-i|-p|-s sleep] filename");
        System.exit(1);
    }

    protected static void processFile(String filename) throws IOException {
        // Open a H264InputStream
        H264InputStream stream = new H264InputStream(new FileInputStream(filename));
        MultiNALProcessor nalProcessor = new MultiNALProcessor(stream, qualityLayers);

        // did user specify a ChunkSizeCalculator
        if (calculator != null) {
            nalProcessor.setChunkSizeCalculator(calculator);
        }

        int count = 0;
        int total = 0;
        SVCChunkInfo chunk = null;
        
        while (nalProcessor.hasNext()) {

            chunk = nalProcessor.next();
            count++;

            total += chunk.offset();

            printChunk(chunk, count, total, nalProcessor.getPayloadSize());

            // fix sleep from student code - awaiting proper algorithm
            try { 
                Thread.sleep(sleep);
            } catch (InterruptedException ie) {
            }
        }

        stream.close();
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
