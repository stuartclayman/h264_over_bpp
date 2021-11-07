package test;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import cc.clayman.h264.*;
import cc.clayman.util.Dict;


public class Test1 {
    public static void main(String[] args) {
        if (args.length == 1) {
            String filename = args[0];
            
            try {
                processFile(filename);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            System.err.println("Test1 filename");
            System.exit(1);
        }
    }

    protected static void processFile(String filename) throws IOException {
        // Open a H264InputStream
        H264InputStream str = new H264InputStream(new FileInputStream(filename));

        int count = 0;
        int total = 0;
        NAL nal;
        
        while ((nal = str.getNAL()) != null) {
            count++;

            total += nal.getSize();

            Map vals = Dict.of("total", total, "count", count);
            //System.err.println("vals = " + vals);

            printNAL(nal, count, total);

        }

        str.close();
    }
    
    protected static void printNAL(NAL nal, int count, int total) {
        int size = nal.getSize();

        System.out.printf("%-8d", count);               // N
        System.out.printf(" %-6d", size*8);             // no of bits
        System.out.printf(" %-5d", size);               // no of bytes
        System.out.printf(" %-9d", (total - size) + 1);     // start bytes
        System.out.printf(" %-10d", total);             // end bytes

        System.out.printf(" %-1d", nal.getNRI());       // NRI
        System.out.printf(" %-2d",nal.getType());       // Type
        System.out.printf(" %-7s",nal.getTypeClass());  // VCL or non-VCL
        System.out.printf(" %s", nal.getTypeString());  // Type description
        System.out.println();
                    
    }
    
}
