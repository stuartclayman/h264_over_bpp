// ChunkInfoPrinter.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: December 2021

package cc.clayman.chunk;

/*
 * An implementation of ChunkInfoMethod that prints out
 * info to System.err
 */
public class ChunkInfoPrinter implements ChunkInfoMethod {
    int count = 0;
    int total = 0;
        
    /**
     * Call the method
     */
    public Object call(ChunkInfo chunk) {
        if (chunk == null) {
            return null;
        } else {

            count++;
            total += chunk.offset();

            System.err.printf("INFO: ");               // 

            System.err.printf(" %-14d", System.currentTimeMillis());    // time ms

            System.err.printf(" %-17d", System.nanoTime());             // time ns

            System.err.printf("%-6d", count);                           // N
            System.err.printf(" %-10d", total);                         // total bytes

            System.err.printf("%-8s", chunk.getNALType());              // type

            System.err.printf("%-6d", chunk.offset());                  // content size

            // Visit the Content
            ChunkContent[] content = chunk.getChunkContent();

            System.err.printf("%-4d", content.length);                  // content length

            for (int c=0; c<content.length; c++) {
                
                int contentSize = content[c].offset();
                System.err.printf(" %-5d", contentSize);                // no of bytes chunk
            }


            System.err.println();
            return null;
        }
    }
}
