// ChunkInfo.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

import java.nio.ByteBuffer;

import cc.clayman.h264.NALType;

/*
 * An interface for ChunkInfo
 */
public interface ChunkInfo {

    /**
     * The size of the space
     */
    public int size();
    
    /**
     * How much remaining space
     */
    public int remaining();

    /**
     * How much used space
     */
    public int offset();

    /**
     * Are any of the ChunkContents full
     */
    public boolean anyFull();

    /**
     * Add some data to the payload
     * @return the ChunkContent created for the payload
     */
    public ChunkContent addPayload(ByteBuffer buf) throws UnsupportedOperationException;

    
    /**
     * Get the type of NALs in this chunk.
     */
    public NALType getNALType();

    /**
     * Get the type of NALs in this chunk.
     */
    public ChunkInfo setNALType(NALType type);

    /**
     * Get the sequence number in this Chunk
     */
    public int getSequenceNumber();

    /**
     * Set the sequence number in this Chunk
     */
    public ChunkInfo setSequenceNumber(int seqNo);
    
    /**
     * Get the start NAL number in this Chunk
     */
    public int getNALNumber();

    /**
     * Set the start NAL number in this Chunk
     */
    public ChunkInfo setNALNumber(int nalNo);

    /**
     * Get the no of NALs in this Chunk
     */
    public int getNALCount();

    /**
     * Set the no of NALs in this Chunk
     */
    public ChunkInfo setNALCount(int nalCount);

    /**
     * The no of ChunkContent elements
     */
    public int chunkCount();

    /**
     * Get the Chunk Content
     */
    public ChunkContent[] getChunkContent();
    
    /**
     * Get the ith Chunk Content
     */
    public ChunkContent getChunkContent(int i);
    
}
