// ChunkStreamer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.chunk;

import java.util.Iterator;


/*
 * An interface for ChunkStreamers
 */
public interface ChunkStreamer extends Iterator<ChunkInfo> {

    /**
     * Returns true if there are more ChunkInfo
     */
    public boolean hasNext();

    /**
     * Returns the next ChunkInfo 
     */
    public ChunkInfo next();


    /**
     * Start the streamer
     */
    public boolean start();
    
    /**
     * Stop the streamer
     */
    public boolean stop();
    
}
