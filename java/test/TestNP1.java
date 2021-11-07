package test;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.SingleNALProcessor;

// A first test of the SingleNALProcessor
public class TestNP1 {
    public static void main(String[] args) {
        if (args.length == 1) {
            String filename = args[0];
            
            try {
                processFile(filename);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            System.err.println("TestNP1 filename");
            System.exit(1);
        }
    }

    protected static void processFile(String filename) throws IOException {
        // Open a H264InputStream
        H264InputStream str = new H264InputStream(new FileInputStream(filename));
        SingleNALProcessor nalProcessor = new SingleNALProcessor(str);

        int count = 0;
        int total = 0;
        ChunkInfo chunk = null;
        
        while (nalProcessor.hasNext()) {

            chunk = nalProcessor.next();
            count++;

            total += chunk.offset();

            printChunk(chunk, count, total);
        }

        str.close();
    }
    
    protected static void printChunk(ChunkInfo chunk, int count, int total) {
        

        System.out.printf("%-8d", count);               // N
        System.out.printf("%-10s", chunk.getNALType());               // type
        System.out.printf(" %-5d", chunk.offset());         // no of bytes
        System.out.printf(" %-10d", total);             // total bytes

        System.out.println();
                    
    }
    
}
