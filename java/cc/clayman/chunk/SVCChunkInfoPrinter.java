// SVCChunkInfoPrinter.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Feb 2022

package cc.clayman.chunk;

/*
 * An implementation of ChunkInfoMethod that prints out
 * info to System.err
 */
public class SVCChunkInfoPrinter implements ChunkInfoMethod {
    int count = 0;
    int total = 0;
        
    /**
     * Call the method
     */
    public Object call(ChunkInfo svcChunk) {
        if (svcChunk == null) {
            return null;
        } else {
            SVCChunkInfo chunk = (SVCChunkInfo)svcChunk;

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
