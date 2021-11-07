// MultiChunkInfo.java
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
public class MultiChunkInfo implements ChunkInfo {
    int startNAL;               // The NAL number in the stream.  Starts at 1.
    int nalCount;               // How many NALs in this Chunk
    int sequenceNo;             // The sequence number
    NALType type;               // Is it VCL or non-VCL

    ChunkContent[] content;       // The content of a Chunk

    // Used by forward() to loop through
    int nextContent = 0;

    /**
     * Allocate a number of chunks.
     *
     * @param A size which is divided into count chunks
     */
    public MultiChunkInfo(int count, int payloadSize) {
        // By default we calculate an even split
        // allocate an array
        int[] contentSizes = new int[count];

        int left = payloadSize;
        int part = payloadSize / count;
        

        // now calculate the split
        for (int i=0; i < count-1; i++) {
            contentSizes[i] = part;
            left -= part;
        }

        contentSizes[count-1] = left;

        
        // allocate the content array
        content = new ChunkContent[contentSizes.length];

        // now allocate the ChunkContent elements
        for (int i=0; i < contentSizes.length; i++) {
            content[i] = new ChunkContent(contentSizes[i]);
        }

        //System.err.print("  MultiChunkInfo: ");
        //for (int i=0; i < count; i++) {
        //    System.err.print(" " + i + ": " + content[i].remaining());
        //}
        //System.err.print("\n");
        
    }
    
    /**
     * Allocate a number of chunks.
     *
     * @param An array of chunk sizes.  e.g [491, 491, 490]
     */
    public MultiChunkInfo(int []contentSizes) {
        // allocate the array
        content = new ChunkContent[contentSizes.length];

        // now allocate the ChunkContent elements
        for (int i=0; i < contentSizes.length; i++) {
            content[i] = new ChunkContent(contentSizes[i]);
        }
    }

    /**
     * The no of ChunkContent elements
     */
    public int chunkCount() {
        return content.length;
    }

    /**
     * Get the Chunk Content
     */
    public ChunkContent[] getChunkContent() {
        return content;
    }
    
    /**
     * Get the ith Chunk Content
     */
    public ChunkContent getChunkContent(int i) {
        return content[i];
    }


    /**
     * The size of the space
     */
    public int size() {
        int size = 0;
        for (ChunkContent chunk : content) {
            size += chunk.size();
        }
        return size;
    }

    /**
     * How much remaining space
     */
    public int remaining() {
        int remaining = 0;
        for (ChunkContent chunk : content) {
            remaining += chunk.remaining();
        }
        return remaining;
    }

    /**
     * How much used space
     */
    public int offset() {
        int offset = 0;
        for (ChunkContent chunk : content) {
            offset += chunk.offset();
        }
        return offset;
    }

    /**
     * Are any of the ChunkContents full
     */
    public boolean anyFull() {
        boolean full = false;
        
        for (ChunkContent chunk : content) {
            int remaining = chunk.remaining();
            int size = chunk.size();

            if (size > 0 && remaining == 0) {
                // one is full
                full = true;

                //System.err.println("  Chunk size " + size + " remaining " + remaining + " is full");
                //no need to carry on
                break;
            }
        }
        return full;
    }

    
    /**
     * Add some data to the payload
     */
    public ChunkContent addPayload(ByteBuffer buf) throws UnsupportedOperationException {
        // Add to next chunk
        //System.err.println("    content[" + nextContent + "] remaining = " + content[nextContent].remaining());

        ChunkContent thisContent = content[nextContent];

        int remainingInChunk = thisContent.addPayload(buf);

        //System.err.println("    content[" + nextContent + "] remaining = " + remainingInChunk);
        
        forward();
        return thisContent;
    }

    
    /**
     * Add some data to the payload
     * offset is offset into payload byte[]
     */
    public ChunkContent addPayload(ByteBuffer buf, int offset, int length) throws UnsupportedOperationException {
        ChunkContent thisContent = content[nextContent];

        // Add to next chunk
        int remainingInChunk = thisContent.addPayload(buf, offset, length);
        forward();
        return thisContent;
    }

    /**
     * Move the content reference forward
     */
    protected void forward() {
        nextContent = (nextContent + 1) % (content.length);
    }

    /**
     * Add some data to the payload chunk: n
     */
    public int addPayload(ByteBuffer buf, int n) throws UnsupportedOperationException {
        return content[n].addPayload(buf);
    }

    
    /**
     * Add some data to the payload chunk: n
     * offset is offset into payload byte[]
     */
    public int addPayload(ByteBuffer buf, int offset, int length, int n) throws UnsupportedOperationException {
        return content[n].addPayload(buf, offset, length);
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
