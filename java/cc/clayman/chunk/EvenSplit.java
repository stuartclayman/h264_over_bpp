// EvenSplit.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

/*
 * A ChunkSizeCalculator that splits the chunks evenly.
 */
public class EvenSplit implements ChunkSizeCalculator {

    /**
     * Calculate the Chunk sizes
     */
    public int[] calculate(int payloadSize, int [] sizes) {
        int count = sizes.length;

        // allocate an array
        int[] result = new int[count];

        int left = payloadSize;
        int part = payloadSize / count;
        

        // now allocate the ChunkContent elements
        for (int i=0; i < count-1; i++) {
            result[i] = part;
            left -= part;
        }

        result[count-1] = left;

        return result;
    }
    
}
