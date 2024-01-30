// MultiChunkInfo.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

import java.nio.ByteBuffer;

/*
 * An class for creating a Multiple Chunks.
 *
 * This is a ChunkInfo that is actually a wrapper around multiple ChunkContents.
 */
public class MultiChunkInfo implements ChunkInfo {
    int sequenceNo;             // The sequence number

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

        //System.err.print("  MultiChunkInfo: ");
        //for (int i=0; i < contentSizes.length; i++) {
        //    System.err.print(" [" + i + "]: " + content[i].size());
        //}
        //System.err.print("\n");
        
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
        //System.err.println("B:    content[" + nextContent + "] remaining = " + content[nextContent].remaining());

        ChunkContent thisContent = content[nextContent];

        int remainingInChunk = thisContent.addPayload(buf);

        //System.err.println("A:    content[" + nextContent + "] remaining = " + remainingInChunk);
        
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
