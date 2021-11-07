// ChunkContent.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

import java.nio.ByteBuffer;
import cc.clayman.h264.NALType;

/*
 * An class for holding the content of a Chunk.
 */
public class ChunkContent {
    int fragmentationNo = 0;        // If fragmented, what fragment is this. 0 is none.
    boolean lastFragment = false;
    int significance;           // The significance value for this 
    boolean isDropped = false;

    int chunkSize;              // How big is the chunk payload
    int remaining;              // How much payload space is remaining

    byte[] payload = null;

    public ChunkContent(int chunkSize) {
        this.chunkSize = chunkSize;
        this.remaining = chunkSize;
        this.payload = new byte[chunkSize];
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
     * Get the fragmentation number for this ChunkContent
     */
    public int getFragmentationNumber() {
        return fragmentationNo;
    }

    /**
     * Set the start fragmentation number in this ChunkContent
     */
    public ChunkContent setFragmentationNumber(int fragNo) {
        this.fragmentationNo = fragNo;
        return this;
    }

    /**
     * Is this the last fragmentation in this ChunkContent
     */
    public boolean isLastFragment() {
        return lastFragment;
    }

    /**
     * Set fragmentations in this ChunkContent
     */
    public ChunkContent setLastFragment(boolean last) {
        this.lastFragment = last;
        return this;
    }

    /**
     * Is this dropped from the ChunkContent
     */
    public boolean isDropped() {
        return isDropped;
    }

    /**
     * Set isDropped in this ChunkContent
     */
    public ChunkContent setIsDropped(boolean dropped) {
        this.isDropped = dropped;
        return this;
    }

    /**
     * Get the significance value for this ChunkContent
     */
    public int getSignificanceValue() {
        return significance;
    }

    /**
     * Set the significance value in this ChunkContent
     */
    public ChunkContent setSignificanceValue(int significance) {
        this.significance = significance;
        return this;
    }

    /**
     * Get the payload bytes
     */
    public byte[] getPayload() {
        return payload;
    }
    
    /**
     * Add some data to the payload
     * @return remaining space
     */
    public int addPayload(ByteBuffer buf) throws UnsupportedOperationException {
        //buf.rewind();
        int contentSize = buf.limit() - buf.position();
        int offset = offset();
        
        //System.err.println("    addPayload: buf.position = " + buf.position() + " buf.limit = " + buf.limit());

        //System.err.println("    addPayload: contentSize = " + contentSize + " remaining = " + remaining);

        if (contentSize == 0) {
            //System.err.println("    addPayload [Z]: contentSize = " + contentSize + " remaining = " + remaining);

            return 0;
        } else if (contentSize > remaining) {
            // content is more than available remaining space
            //throw new UnsupportedOperationException("Not Enough Space Remaining for payload of size " + contentSize);

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
}
