// ChunkDepacketizer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import java.net.DatagramPacket;


import cc.clayman.chunk.ChunkInfo;


/**
 * Take a DatagramPacket  and converts them into a ChunkInfo object.
 */
public interface ChunkDepacketizer {
    
    /**
     * Convert a DatagramPacket into a ChunkInfo 
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public ChunkInfo convert(DatagramPacket packer) throws UnsupportedOperationException;
}
