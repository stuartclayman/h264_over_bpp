// ChunkPacketizer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import cc.clayman.chunk.ChunkInfo;


/**
 * Take ChunkInfo objects and converts them into a byte[]
 * A ChunkPacketizer will return the size of the payload it can accommodate.
 */
public interface ChunkPacketizer {
    /**
     * Get the payload size.
     * This is the maximum payload a packet can contain, excluding the headers.
     */
    public int getPayloadSize();
    
    /**
     * Convert a ChunkInfo into byte[]
     * Takes a sequence number, a condition value, a threshold value, and a ChunkInfo
     * @throws UnsupportedOperationException if the Chunk is too big to fit in a packet
     */
    public byte[] convert(int sequence, int condition, int threshold, ChunkInfo chunk) throws UnsupportedOperationException;
}
