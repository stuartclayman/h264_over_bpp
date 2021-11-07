// BasicChunkInfo.java
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
 */
public class BasicChunkInfo {
    int startNAL;               // The NAL number in the stream.  Starts at 1.
    int nalCount;               // How many NALs in this Chunk
    int sourceFrameNo;          // The source frame number
    int fragmentationNo;        // If fragmented, what fragment is this. 0 is none.
    boolean lastFragment;
    NALType type;               // Is it VCL or non-VCL

    int chunkSize;              // How big is the chunk payload

    int remaining;              // How much payload space is remaining

    byte[] payload = null;


    public BasicChunkInfo(int chunkSize) {
        this.chunkSize = chunkSize;
        this.remaining = chunkSize;
        this.payload = new byte[chunkSize];
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
        return null;
    }
    
    /**
     * The size of the space
     */
    public int size() {
        return chunkSize;
    }

    /**
     * How much remaining space
     */
    public int remaining() {
        return remaining;
    }

    /**
     * How much used space
     */
    public int offset() {
        return chunkSize - remaining;
    }

    /**
     * Are any of the ChunkContents full
     */
    public boolean anyFull() {
        if (remaining == 0) {
            // it's full
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Add some data to the payload
     */
    public int addPayload(ByteBuffer buf) throws UnsupportedOperationException {
        //buf.rewind();
        int contentSize = buf.limit() - buf.position();
        int offset = offset();
        
        //System.err.println("    addPayload: buf.position = " + buf.position() + " buf.limit = " + buf.limit());

        //System.err.println("    addPayload: contentSize = " + contentSize + " remaining = " + remaining);

        if (contentSize > remaining) {
            // content is more than available remaining space

            // copy in just some bytes
            buf.get(payload, offset, remaining);

            //System.err.println("    addPayload [P]: contentSize = " + contentSize + " remaining = " + remaining);

            
            remaining = 0;

            return 0;
        } else {
            //buf.get(payload);
            buf.get(payload, offset, contentSize);


            remaining -= contentSize;

            //System.err.println("    addPayload [F]: contentSize = " + contentSize + " remaining = " + remaining);

            return remaining;
        }
    }

    
    /**
     * Add some data to the payload
     * offset is offset into payload byte[]
     */
    public int addPayload(ByteBuffer buf, int offset, int length) throws UnsupportedOperationException {
        //buf.rewind();
        int contentSize = length;

        if (contentSize > remaining) {
            throw new UnsupportedOperationException("Not Enough Space Remaining for payload of size " + contentSize);
        } else {
            buf.get(payload, offset, length);

            remaining -= contentSize;

            return remaining;
        }
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
    public void setNALType(NALType type) {
        this.type = type;
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
    public void setNALNumber(int nalNo) {
        this.startNAL = nalNo;
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
    public void setNALCount(int nalCount) {
        this.nalCount = nalCount;
    }
}
