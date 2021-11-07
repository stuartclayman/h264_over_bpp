// ChunkSizeCalculator.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

/*
 * An interface for ChunkSizeCalculator.
 * Used to calculate chunk sizes.
 */
public interface ChunkSizeCalculator {

    /**
     * Calculate the Chunk sizes from the sizes of the content.
     * The size of the result is the same as the arg
     */
    public int[] calculate(int payloadSize, int[] sizes);
    
}
