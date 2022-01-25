// ChunkInfoMethod.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: December 2021

package cc.clayman.chunk;

/*
 * An interface for ChunkInfoMethod
 * Used as a callback from NALRebuilder and nalProcessor,
 * and called at certain points.
 */
public interface ChunkInfoMethod {

    /**
     * Call the method
     */
    public Object call(ChunkInfo chunk);
    
}
