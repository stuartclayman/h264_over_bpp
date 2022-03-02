// SVCChunkInfo.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Feb 2022

package cc.clayman.chunk;

import java.nio.ByteBuffer;

import cc.clayman.h264.NALType;

/*
 * An interface for Info about SVC Chunks.
 */
public interface SVCChunkInfo extends ChunkInfo {

    /**
     * Get the type of NALs in this chunk.
     */
    public NALType getNALType();

    /**
     * Get the type of NALs in this chunk.
     */
    public ChunkInfo setNALType(NALType type);
    
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
}
