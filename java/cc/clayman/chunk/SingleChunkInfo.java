// SingleChunkInfo.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

import java.nio.ByteBuffer;
import cc.clayman.h264.NALType;

/*
 * An class for creating a single Chunk.
 * It may contain 1 or more whole NALs,
 * or 1 NAL that is fragmented.
 *
 * This is like SingleChunkInfo but is actually a wrapper around ChunkContent.
 */
public class SingleChunkInfo implements ChunkInfo {
    int startNAL;               // The NAL number in the stream.  Starts at 1.
    int nalCount;               // How many NALs in this Chunk
    int sequenceNo;             // The sequence number
    NALType type;               // Is it VCL or non-VCL

    ChunkContent content;       // The content of a Chunk

    public SingleChunkInfo(int chunkSize) {
        content = new ChunkContent(chunkSize);
    }


    /**
     * The no of ChunkContent elements
     */
    public int chunkCount() {
        return 1;
    }
    
    /**
     * Get the Chunk Content
     */
    public ChunkContent[] getChunkContent() {
        ChunkContent[] c = new ChunkContent[1];
        c[0] = content;
        return c;
    }
    
    /**
     * Get the ith Chunk Content
     */
    public ChunkContent getChunkContent(int i) {
        if (i == 0) {
            return content;
        } else {
            throw new ArrayIndexOutOfBoundsException("Invalid index " + i);
        }
    }


    /**
     * The size of the space
     */
    public int size() {
        return content.size();
    }

    /**
     * How much remaining space
     */
    public int remaining() {
        return content.remaining();
    }

    /**
     * How much used space
     */
    public int offset() {
        return content.offset();
    }

    /**
     * Are any of the ChunkContents full
     */
    public boolean anyFull() {
        if (content.remaining() == 0) {
            // it's full
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Add some data to the payload
     */
    public ChunkContent addPayload(ByteBuffer buf) throws UnsupportedOperationException {
        content.addPayload(buf);

        return content;
    }

    
    /**
     * Add some data to the payload
     * offset is offset into payload byte[]
     */
    public ChunkContent addPayload(ByteBuffer buf, int offset, int length) throws UnsupportedOperationException {
        content.addPayload(buf, offset, length);

        return content;
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

    /**
     * Get the sequence number in this Chunk
     */
    public int getSequenceNumber() {
        return sequenceNo;
    }

    /**
     * Set the sequence number in this Chunk
     */
    public ChunkInfo setSequenceNumber(int seqNo) {
        this.sequenceNo = seqNo;
        return this;
    }
    
}
