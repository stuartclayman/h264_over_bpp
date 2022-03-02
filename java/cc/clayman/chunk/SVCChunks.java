// SVCChunks.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Feb 2022

package cc.clayman.chunk;

import java.nio.ByteBuffer;
import cc.clayman.h264.NALType;

/*
 * An class for creating a Multiple Chunks of SVC video
 * It may contain 1 or more whole NALs,
 * or 1 NAL that is fragmented.
 *
 * This is like MultiChunkInfo but has fields and methods for SVC video.
 */
public class SVCChunks extends MultiChunkInfo implements SVCChunkInfo {
    int startNAL;               // The NAL number in the stream.  Starts at 1.
    int nalCount;               // How many NALs in this Chunk
    NALType type;               // Is it VCL or non-VCL

    /**
     * Allocate a number of chunks.
     *
     * @param A size which is divided into count chunks
     */
    public SVCChunks(int count, int payloadSize) {
        super(count, payloadSize);
    }
    
    /**
     * Allocate a number of chunks.
     *
     * @param An array of chunk sizes.  e.g [491, 491, 490]
     */
    public SVCChunks(int []contentSizes) {
        super(contentSizes);
    }
    
    /**
     * Get the type of NALs in this chunk.
     */
    public NALType getNALType() {
        return type;
    }
    
    /**
     * Get the type of NALs in this chunk.
     */
    public ChunkInfo setNALType(NALType type) {
        this.type = type;
        return this;
    }
    
    /**
     * Get the start NAL number in this Chunk
     */
    public int getNALNumber() {
        return startNAL;
    }

    /**
     * Set the start NAL number in this Chunk
     */
    public ChunkInfo setNALNumber(int nalNo) {
        this.startNAL = nalNo;
        return this;
    }

    /**
     * Get the no of NALs in this Chunk
     */
    public int getNALCount() {
        return nalCount;
    }

    /**
     * Set the no of NALs in this Chunk
     */
    public ChunkInfo setNALCount(int nalCount) {
        this.nalCount = nalCount;
        return this;
    }

}
