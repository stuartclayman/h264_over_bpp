// NALProcessor.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.processor;

import cc.clayman.chunk.ChunkInfo;

/*
 * An interface for processors of NALs.
 */
public interface NALProcessor {
    /**
     * Returns true if the iteration has more elements.
     */
    public boolean hasNext();


    /**
     * Returns the next element in the iteration.
     */
    public ChunkInfo next();

    /**
     * Start the processor
     */
    public boolean start();
    
    /**
     * Stop the processor
     */
    public boolean stop();
    
    

}
