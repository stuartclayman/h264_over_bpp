// NALRebuilder.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.processor;

import cc.clayman.chunk.ChunkInfo;

/*
 * An interface for rebuilders of NALs.
 * These take a collection of chunks and rebuild a stream on NALs.
 */
public interface NALRebuilder {
    /**
     * Take a ChunkInfo and try to rebuild some NALs.
     * A ChunkInfo is passed in, and potentially some NALs are passed back.
     */
    public RebuildState process(ChunkInfo chunk);


    /**
     * Start the rebuilder
     */
    public boolean start();
    
    /**
     * Stop the rebuilder
     */
    public boolean stop();
    
}
