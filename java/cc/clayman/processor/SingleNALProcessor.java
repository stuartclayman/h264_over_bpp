// SingleNALProcessor.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.processor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

import cc.clayman.h264.H264InputStream;
import cc.clayman.h264.NAL;
import cc.clayman.h264.NALType;
import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.SVCChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.chunk.SingleChunkInfo;
import cc.clayman.chunk.SVCChunks;
import cc.clayman.net.IP;


/*
 * A processor of NALs, that creates a single Chunk on each call.
 */
public class SingleNALProcessor implements NALProcessor, Iterator {
    // The H264InputStream we are reading from
    H264InputStream inStream;
    
    // The size of the contents in the packets
    int contentSize = 1472;     // Ethernet payload (1500) - 20 - 8

    // The current NAL we are looking at
    NAL currentNAL = null;

    // The NAL count
    int nalCount = 1;

    // Fragment number for a VCL
    int fragment = 0;
    // The offset into the NAL payload
    int nalOffset = 0;
    // The NAL buffer
    ByteBuffer nalBuffer = null;

    // The current ChunkInfo
    SVCChunkInfo chunk = null;
    
    // The H264InputStream has hit EOF, so no more to do
    boolean finished = false;

    /**
     * Construct a SingleNALProcessor
     * uses the default payload size of 1472;
     */
    public SingleNALProcessor(H264InputStream inputStream) {
        inStream = inputStream;
    }
    
    /**
     * Construct a SingleNALProcessor
     * @param packetSize the size of the packets that will sent to packets.
     */
    public SingleNALProcessor(H264InputStream inputStream, int packetSize) {
        inStream = inputStream;
        this.contentSize = packetSize - IP.IP_HEADER - IP.UDP_HEADER;
    }

    /**
     * Start the processor
     */
    public boolean start() {
        return true;
    }
    
    /**
     * Stop the processor
     */
    public boolean stop() {
        try {
            inStream.close();
        } catch (IOException ioe) {
        }
        
        return true;
    }

    /*
     * Iterator code
     *
     * Should support:
     * while(hasNext()) {
     *      chunk = next();
     * }
     *
     * and:
     * next(); next(); next(); ...
     *    
     */
    
    /**
     * Returns true if the iteration has more elements.
     */
    public boolean hasNext() {
        // if no currentNAL
        if (currentNAL == null) {
            // get another one from the inputStream
            fetchNextNAL();
        }
        
        return !finished;
    }

    /**
     * Returns the next element in the iteration.
     */
    public SVCChunkInfo next() {
        // We can loop over NALs until we fill the ChunkInfo
        // or see a different type of NAL

        while (true) {
            // get a NAL
            if (currentNAL == null) {
                // get another one from the inputStream
                fetchNextNAL();
            } else {
                // Carry on processing  the current NAL
                // Probably a VCL NAL that is being fragmented
            }

            // process the NAL

            // At this point we have a NAL and a ChunkInfo

            // There are 2 main strategies:
            // 1. Fit multiple non-VCL NALs into a chunk
            // 2. Fragment one VCL across multiple chunks

            // First we see if there is a ChunkInfo object
            // and then if the NAL type and the ChunkInfo type are different

            if (chunk == null) {
                // Allocate a new ChunkInfo for next ime
                chunk = allocateChunkInfo(contentSize);

                chunk.setNALType(currentNAL.getTypeClass());
                
            } else if (chunk.getNALType() != currentNAL.getTypeClass()) {
                // The Type changed from non-VCL to VCL, or VCL to non-VCL

                System.err.println("  Switch " + chunk.getNALType() + "  TO  " + currentNAL.getTypeClass());

                // If there's something in the chunk, return the ChunkInfo
                if (chunk.remaining() < chunk.size()) {
                    // Cleanup and return the Chunk
                    SVCChunkInfo retVal = chunk;

                    // Allocate a new ChunkInfo for next ime
                    chunk = allocateChunkInfo(contentSize);

                    return retVal;
                }

            }  

            // non-VCL processing
            if (chunk.getNALType() == NALType.NONVCL) {

                // CHeck if the NAL will fit in the Chunk
                int nalSpace = currentNAL.getSize() + currentNAL.getMarkerSize();

                System.err.println("  Space " + nalSpace + " <> " + chunk.remaining());

                if (nalSpace > chunk.remaining()) {
                    // Not enough room
                    // So cleanup and return the Chunk
                    SVCChunkInfo retVal = chunk;

                    chunk = null;
                    
                    ejectNAL();
                
                    return retVal;
                } else {
                    // add some of the payload to the chunk
                    chunk.addPayload(currentNAL.buffer());

                    ejectNAL();

                    continue;
                }
            } else 
            
            // VCL processing
            if (chunk.getNALType() == NALType.VCL) {
                
                // copy some bytes into chunk
                ChunkContent chunkContent = chunk.addPayload(nalBuffer);
                boolean anyFull = chunk.anyFull();

                // Cleanup and return the Chunk
                SVCChunkInfo retVal = chunk;

                chunk = null;
                    
                if (anyFull) {
                    // it's fragmented
                    fragment++;
                } else {
                    // it's the last one
                    fragment++;

                    ejectNAL();
                }
                
                // Return
                return retVal;
                
            }
        }
    }

    /**
     * Fetch the next NAL from the H264InputStream
     * @return true if it got one, false otherwise
     */
    protected boolean fetchNextNAL() {
        try { 
            if (inStream.isEOF()) {
                // nothing more to do
                finished = true;
                inStream.close();
                return false;
            } else {
                currentNAL = inStream.getNAL();
                nalCount++;

                nalBuffer = currentNAL.buffer();
                
                System.err.println("  Fetch " + nalCount + " NAL " + currentNAL);
                
                return true;
            }
        } catch (IOException ioe) {
            return false;
        }
    }

    /**
     * Eject the current NAL.
     * Causes the next NAL to be read, eventually.
     */
    protected void ejectNAL() {
        // eject currentNAL
        currentNAL = null;
    }

    /**
     * Allocate a new Chunk Info
     */
    protected SVCChunkInfo allocateChunkInfo(int size) {
        SVCChunkInfo chunk = new SVCChunks(1, size); // new SingleChunkInfo(size); // new BasicChunkInfo(size); //
        chunk.setNALType(currentNAL.getTypeClass());

        return chunk;
    }
}
