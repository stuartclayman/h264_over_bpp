// MultiNALProcessor.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.processor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.stream.Stream;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.util.Verbose;



/*
 * A processor of NALs, that creates a single ChunkInfo on each call.
 */
public class MultiNALProcessor implements NALProcessor, Iterator<ChunkInfo> {
    // The H264InputStream we are reading from
    H264InputStream inStream;
    
    // The size of the contents in the packets
    int contentSize = 1472;     // Ethernet payload (1500) - 20 - 8

    // The current NAL we are looking at
    NAL currentNAL = null;

    // The NAL number
    int nalNumber = 0;

    // The no of NALs in a chunk
    int nalCount = 0;

    // Is previous NAL NONVCL
    boolean prevIsNonVCL  = true;
    // Number of VCL NALs seen so far
    int vclCount = 0;
    
    
    // How many read in this time
    int readIn = 0;

    // The offset into the NAL payload
    int nalOffset = 0;
    // The NAL buffer
    ByteBuffer nalBuffer = null;

    // The current ChunkInfo
    SVCChunkInfo chunk = null;

    // A potential callback
    ChunkInfoMethod onChunk = null;
    
    ChunkSizeCalculator chunkSizeCalculator ;

    // The H264InputStream has hit EOF, so no more to do
    boolean finished = false;

    // No of VCLs to expect
    int noOfVCLs = 1;

    // The NALs of the VCLs to process
    NAL[] vcls = null;
    ByteBuffer[] nalBuffers = null;

    // Fragment number for a VCL
    int[] fragments = null;
    boolean[] lastFrag = null;


    // TemporalLayerModel
    TemporalLayerModel temporalLayerModel = new TemporalLayerModelGOB16();
    // SignificanceModel
    SignificanceModel significanceModel = new SignificanceModel3LayersMod1();
    // Layers
    static final Layer[] layerModel = { Layer.L0, Layer.L1, Layer.L2, Layer.L3,  Layer.L4 };

    /**
     * Construct a MultiNALProcessor
     * and map a number of VCLs to chunks.
     * uses the default payload size of 1472;
     */
    public MultiNALProcessor(H264InputStream inputStream, int noOfVCLs) {
        inStream = inputStream;
        this.noOfVCLs = noOfVCLs;
        this.vcls = new NAL[noOfVCLs];
        this.nalBuffers = new ByteBuffer[noOfVCLs];
        fragments = new int[noOfVCLs];
        lastFrag = new boolean[noOfVCLs];
        clearFragmentData();
        chunkSizeCalculator = new EvenSplit();   // DynamicSplit(); // 
    }
    
    /**
     * Construct a MultiNALProcessor
     * and map a number of VCLs to chunks.
     * @param packetSize the size of the packets that will sent to packets.
     */
    public MultiNALProcessor(H264InputStream inputStream, int packetSize, int noOfVCLs) {
        inStream = inputStream;
        this.contentSize = packetSize;
        this.noOfVCLs = noOfVCLs;
        this.vcls = new NAL[noOfVCLs];
        this.nalBuffers = new ByteBuffer[noOfVCLs];
        fragments = new int[noOfVCLs];
        lastFrag = new boolean[noOfVCLs];
        clearFragmentData();
        chunkSizeCalculator = new EvenSplit();   // DynamicSplit(); // 
    }

    /**
     * Get the payload size for all the content elements.
     */
    public int getPayloadSize() {
        return contentSize;
    }
    
    /**
     * Get the current calculator for the chunk sizes
     */
    public int getChunkSizeCalculator() {
        return contentSize;
    }
    
    /**
     * Get the current calculator for the chunk sizes
     */
    public NALProcessor setChunkSizeCalculator(ChunkSizeCalculator calc) {
        chunkSizeCalculator = calc;
        return this;
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
        if (Verbose.level >= 3) {
            System.err.println("  hasNext()");
        }
        
        // if no currentNAL, probably after ejectNAL()
        if (currentNAL == null) {
            // get another one from the inputStream
            // this might fetch a number of VCLs in a layered video
            fetchNextNAL();
        }
        
        return !finished;
    }

    /**
     * Returns the next element in the iteration.
     */
    public SVCChunkInfo next() {
        if (Verbose.level >= 3) {
            System.err.println("  next()");
        }
        
        // We can loop over NALs until we fill the ChunkInfo
        // or see a different type of NAL
        SVCChunkInfo chunkInfo = innerNext();

        if (chunkInfo == null) {
            return null;
        } else {
            // call onChunk if set from outside
            if (onChunk != null) {
                onChunk.call(chunkInfo);
            }

            return chunkInfo;
        }

    }


    
    protected SVCChunkInfo innerNext() {
        while (true) {
            // get a NAL
            if (currentNAL == null) {
                // get another one from the inputStream
            // this might fetch a number of VCLs in a layered video
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

                if (Verbose.level >= 2) {
                    System.err.println("  Allocate " + currentNAL.getTypeClass() + " nalNumber = " + nalNumber + " nalCount = " + nalCount + " readIn = " + readIn);
                }
                

                if (currentNAL.getTypeClass() == NALType.NONVCL) {
                    // NALType = NONVCL
                    chunk = allocateOneChunkInfo(contentSize);

                    chunk.setNALType(currentNAL.getTypeClass());
                    chunk.setNALNumber(nalNumber - readIn + 1);
                } else {
                    // NALType = VCL
                    // Allocate a new ChunkInfo for next ime
                    chunk = allocateChunkInfo(contentSize);

                    chunk.setNALType(currentNAL.getTypeClass());
                    chunk.setNALNumber(nalNumber - readIn + 1);
                }
                
            } else if (chunk.getNALType() != currentNAL.getTypeClass()) {
                // The Type changed from non-VCL to VCL, or VCL to non-VCL

                if (Verbose.level >= 2) {
                    System.err.println("  Switch " + chunk.getNALType() + "  TO  " + currentNAL.getTypeClass() + " nalNumber = " + nalNumber + " nalCount = " + nalCount + " readIn = " + readIn);
                }

                // If there's something in the chunk, return the ChunkInfo
                if (chunk.remaining() < chunk.size()) {
                    // Cleanup and return the Chunk
                    chunk.setNALCount(nalCount);
                    SVCChunkInfo retVal = chunk;

                    // Allocate a new ChunkInfo for next ime
                    chunk = allocateChunkInfo(contentSize);

                    chunk.setNALNumber(nalNumber - readIn + 1);

                    nalCount = 0;

                    return retVal;
                }

            }  

            // non-VCL processing
            if (chunk.getNALType() == NALType.NONVCL) {

                // CHeck if the NAL will fit in the Chunk
                int nalSpace = currentNAL.getSize() + currentNAL.getMarkerSize();

                if (Verbose.level >= 2) {
                    System.err.println("  NONVCL Space " + nalSpace + " <> " + chunk.remaining());
                }

                if (nalSpace > chunk.remaining()) {
                    // Not enough room
                    // So cleanup and return the Chunk
                    chunk.setNALCount(nalCount);
                    SVCChunkInfo retVal = chunk;

                    chunk = null;
                    nalCount = 0;
                    
                    ejectNAL();

                    return retVal;
                } else {
                    // add some of the payload to the chunk
                    ChunkContent content = chunk.addPayload(currentNAL.buffer());
                    content.setSignificanceValue(1);

                    nalCount++;

                    ejectNAL();

                    continue;
                }
            } else if (chunk.getNALType() == NALType.VCL) {

                // VCL processing
                
                // copy some bytes into chunk
                int payloadSpace;
                ////boolean anyFull;

                boolean []drained = new boolean[noOfVCLs];
                // check drained status
                boolean anyDrained = false;
                int drainedCount = 0;
                
                // skip through noOfVCLs
                for (int i=0; i<noOfVCLs; i++) {
                    ChunkContent content = chunk.addPayload(nalBuffers[i]);

                    fragments[i]++;
                    content.setFragmentationNumber(fragments[i]);

                    // Use TemporalLayerModel to get frame info
                    TemporalLayerModel.Tuple<Frame, Temporal, Integer> frameInfo = temporalLayerModel.getLayerInfo(vclCount);
                    
                    // collect significance from TemporalLayerModel and Layer No
                    int significance = significanceModel.getSignificanceValue(frameInfo.frame, frameInfo.temporal, Layer.get(i)); // layerModel[i]);
                    // put significance into ChunkContent
                    content.setSignificanceValue(significance);

                    if (nalBuffers[i].position() == nalBuffers[i].limit()) {
                        // this buffer has been fully drained.
                        drained[i] = true;

                        anyDrained = true;

                        drainedCount++;

                        // lastFrag
                        lastFrag[i] = true;
                        content.setLastFragment(lastFrag[i]);
                    }

                    if (Verbose.level >= 1) {
                        System.err.println("CHUNK: nalNo: " + (chunk.getNALNumber()+i) + " BPP: content[" + i + "] = " + content.offset() + " fragment: " + content.getFragmentationNumber() + " isLastFragment: " + content.isLastFragment());
                    }

                }

                // Cleanup and return the Chunk
                chunk.setNALCount(nalCount);
                SVCChunkInfo retVal = chunk;

                chunk = null;
                nalCount = 0;


                // more NALs to drain data from
                if (drainedCount < noOfVCLs) {
                } else {
                    // it's the last one
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

                if (Verbose.level >= 2) {
                    System.err.println("EOF");
                }

                return false;
            } else {
                currentNAL = inStream.getNAL();
                nalNumber++;

                if (Verbose.level >= 3) {
                    System.err.println("  Fetch " + nalNumber + " NAL " + currentNAL);
                }
                
                // Here we check the NALType to determine if we should readahead
                if (currentNAL.getTypeClass() == NALType.VCL) {
                    // NALType = VCL

                    if (prevIsNonVCL) {
                        vclCount++;
                        prevIsNonVCL = false;
                    }

                    vcls[0] = currentNAL;
                    nalBuffers[0] = currentNAL.buffer();

                    // now try and get the others
                    for (int i = 1; i<noOfVCLs; i++) {
                        currentNAL = inStream.getNAL();
                        nalNumber++;

                        if (Verbose.level >= 3) {
                            System.err.println("  Fetch " + nalNumber + " NAL " + currentNAL);
                        }
                
                        if (currentNAL.getTypeClass() != NALType.VCL) {
                            throw new Error("Unexpected NAL at " + nalNumber);
                        }

                        vcls[i] = currentNAL;
                        nalBuffers[i] = currentNAL.buffer();

                    }

                    readIn = noOfVCLs;

                } else {                
                    // NALType = NONVCL

                    prevIsNonVCL = true;

                    nalBuffer = currentNAL.buffer();

                    readIn = 1;
                    
                    // Clear VCL data store
                    ejectVCLNALs();
                    clearFragmentData();
                }
                
                return true;
            }
        } catch (IOException ioe) {
            throw new Error("NAL read error " + nalNumber + " Msg: " + ioe.getMessage());
        }
    }


    /**
     * Eject the current NAL.
     * Causes the next NAL to be read, eventually.
     */
    protected void ejectNAL() {
        // eject currentNAL
        currentNAL = null;

        if (Verbose.level >= 3) {
            System.err.println("    ejectNAL");
        }
    }

    /**
     * Eject the VCL NALs.
     * Causes the next NAL to be read, eventually.
     */
    protected void ejectVCLNALs() {
        // eject VCL NALs
        for (int i = 0; i<noOfVCLs; i++) {
            vcls[i] = null;
            nalBuffers[i] = null;
        }
    }

    /**
     * Clear fragment info
     */
    protected void clearFragmentData() {
        for (int i = 0; i<noOfVCLs; i++) {
            fragments[i] = 0;
            lastFrag[i] = false;
        }
    }


    /**
     * Allocate a new Chunk Info
     */
    protected SVCChunkInfo allocateOneChunkInfo(int size) {
        SVCChunkInfo chunk = new SVCChunks(1, size);
        chunk.setNALType(currentNAL.getTypeClass());

        return chunk;
    }
    
    /**
     * Allocate a new Chunk Info
     */
    protected SVCChunkInfo allocateChunkInfo(int size) {
        // The simple way to do the allocation
        // The SVCChunks will split the space evenly
        //ChunkInfo chunk = new SVCChunks(noOfVCLs, size);

        // A better way is to see if we can adjust the space dynamically
        // If any NAL buffers are used up, we can split the remaining space in accordance

        Stream<Integer> ss = Stream.of(nalBuffers).map(bb -> bytesToCollect(bb));
        int[] needed = ss.mapToInt(Integer::intValue).toArray();  //toArray(Integer[]::new);

        int [] chunkSizes = chunkSizeCalculator.calculate(size, needed);
        
        /*

        ss.forEach(e -> System.err.print(" val " + e));
        System.err.println();
                   
        */

        SVCChunkInfo chunk = new SVCChunks(chunkSizes);
        
        chunk.setNALType(currentNAL.getTypeClass());

        return chunk;
    }

    /**
     * How much space left in a NAL buffer
     */
    private int bytesToCollect(ByteBuffer bb) {
        return bb.limit() - bb.position();
    }
    
    /**
     * Setup callback function.
     * Can be used for printouts, etc.
     */
    public void onChunk(ChunkInfoMethod method) {
        onChunk = method;
    }

}
