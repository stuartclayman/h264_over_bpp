// MultiNALRebuilder.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.processor;

import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import cc.clayman.h264.NAL;
import cc.clayman.h264.NALType;
import cc.clayman.h264.H264InputStream;
import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.SVCChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.chunk.ChunkStreamer;
import cc.clayman.chunk.ChunkInfoMethod;
import cc.clayman.util.Verbose;

/*
 * A class for rebuilding of NALs.
 * These take a collection of chunks and rebuild a stream on NALs.
 */
public class MultiNALRebuilder implements NALRebuilder {
    // The NAL no we expect to see next
    int expectedNALNo = 1;
    
    // No of VCLs to expect
    int noOfVCLs = 1;

    // A list of ChunkContent from each of the chunks
    // which is built up as we visit each ChunkInfo
    List<ChunkContent>[] nalChunkLists = null;

    // The ChunkStreamer
    ChunkStreamer chunkStreamer = null;

    // Some NALs ready to be consumed by the caller
    List<NALResult> nalList = null;

    // A potential callback
    ChunkInfoMethod onChunk = null;
    
    /**
     * MultiNALRebuilder
     * Takes a ChunkStreamer, which returns some ChunkInfo objects,
     * and a number of VCLs per layer, and recreates a stream of NALs.
     */
    public MultiNALRebuilder(ChunkStreamer chunkStreamer, int noOfVCLs) {
        this.noOfVCLs = noOfVCLs;
        this.nalChunkLists = new List[noOfVCLs];
        this.chunkStreamer = chunkStreamer;
        this.nalList = new ArrayList<NALResult>();

        // First we setup the nalChunkLists for the VCLs
        // ready for when we visit VCLs
        setupNALChunkLists();
    }

    /**
     * Start the rebuilder
     */
    public boolean start() {
        chunkStreamer.start();
        return true;
    }
    
    /**
     * Stop the rebuilder
     */
    public boolean stop() {
        chunkStreamer.stop();
        return true;
    }
    
    

    /**
     * Returns true if the iteration has more elements.
     */
    public boolean hasNext() {
        if (Verbose.level >= 3) {
            System.err.println("  hasNext()");
        }

        if (! chunkStreamer.hasNext()) {
            // chunkStreamer is finshed
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns the next NAL in the iteration.
     * Actually returns a NALResult so the caller can determine
     * what the rebuilder has discovered from the stream of Chunks.
     */
    public NALResult next() {
        if (Verbose.level >= 3) {
            System.err.println("  next()");
        }

        if (! chunkStreamer.hasNext()) {
            return null;
        } else {
            // First we check if the NALList has any content
            // and if there's more to return
            if (nalList.size() > 0) {
                // there's something, no don;t do more
            } else {
                // get more chunks from the chunkStreamer
                boolean moreToDo = true;


                // loop through chunks, until there is no more to do
                while (moreToDo) {
                    // Get a chunk from the streamer
                    // The chunkStreamer returns a SVCChunkInfo
                    SVCChunkInfo chunk = (SVCChunkInfo)chunkStreamer.next();

                    if (chunk == null) {
                        // the streamer has hit EOF
                        moreToDo = false;
                        return null;
                        
                    } else {

                        // call onChunk if set from outside
                        if (onChunk != null) {
                            onChunk.call(chunk);
                        }
                    
                    
                        // Process the chunk, and see what we get back
                        RebuildState processedChunk = process(chunk);

                        // Now check what we got 
                        if (processedChunk.state == RebuildState.State.NAL_VALUES) {
                            // we got multiple NALs
                            // processedChunk holds Optional<List<NAL>> getNALs()

                            moreToDo = false;


                            // patch up nalList 
                            // dig out nalList from the Optional
                            // and add a number of NALResult to the nalList
                            List<NAL> nals = processedChunk.getNALs().get();

                            for (NAL nal: nals) {

                                nalList.add(new NALResult(NALResult.State.NAL, processedChunk.nalType, expectedNALNo, nal));
                                expectedNALNo++;
                            }


                            if (Verbose.level >= 3) {
                                System.err.println("next: nalList = " + nalList.size());
                            }

                        
                            //  Setup the nalChunkLists for the next lot
                            setupNALChunkLists();

                    
                        } else if (processedChunk.state == RebuildState.State.PROCESSING) {
                            // We are still processing input chunks
                            // So go round again
                        } else if (processedChunk.state == RebuildState.State.FRAGMENT_END) {
                            // we got the end of the fragments
                            moreToDo = false;

                            // now we should convert the List<ChunkContent>[]
                            // into a number of NALs
                            for (int c=0; c<noOfVCLs; c++) {
                                //System.err.println("rebuild: convert content list layer " + c);
                            
                                // Get the list of chunks for layer c
                                List<ChunkContent> layerList = nalChunkLists[c];

                                // convert layer  chunks to NAL

                                // Consider that a chunk might be missing
                                // so convertLayer() might not return a NAL
                                NALResult conversion = convertLayer(expectedNALNo, chunk.getNALType(), c, layerList) ;

                                //System.err.println("rebuild: conversion = " + conversion);

                                // set the result, for the expectedNALNo
                                NALResult result;
                            
                                if (conversion.state == NALResult.State.NAL) {
                                    result = new NALResult(conversion.state, conversion.nalType, expectedNALNo, conversion.nal);
                                } else {
                                    result = new NALResult(conversion.state, conversion.nalType, expectedNALNo);
                                }

                                // add this to the nalList
                                nalList.add(result);
                                
                                expectedNALNo++;
                            }

                            if (Verbose.level >= 3) {
                                System.err.println("next: nalList = " + nalList.size());
                            }
                                                

                            //  Setup the nalChunkLists for the next lot
                            setupNALChunkLists();

                        
                        } else if (processedChunk.state == RebuildState.State.MISSSING) {
                            // What shall we do on a MISSSING one
                            if (Verbose.level >= 1) {
                                System.err.println("MISSING at " + processedChunk.nalNumber + ": lost " + expectedNALNo + " -> " + (processedChunk.nalNumber - 1));
                            }

                            if (processedChunk.nalNumber < expectedNALNo) {
                                // out of order
                                if (Verbose.level >= 1) {
                                    System.err.println("MISSING out of order: " + processedChunk.nalNumber);
                                }

                            }

                            
                            // expectedNALNo = received nalNumber + 1
                            expectedNALNo = processedChunk.nalNumber ;

                            // Return Error somehow
                            
                        } else {
                            // the processedChunk state is Unknown
                        }
                    }
                }

                // by now we should have one or more NALs
            }

            // return next NALResult in the nalList
            NALResult answer = nalList.get(0);

            // drop it off the list
            nalList.remove(0);


            return answer;

        }
        
    }    

    // ChunkInfo data
    //
    // 1     chunkCount: 1 command: 0x00001 condition: 95 threshold: 12
    //   1  OFFi: nalNo: 1 nalCount: 9 fragment: 0 
    //      CSi: contentSize: 427  SIGi:  0
    //      OFi:  FFi: false  NAL: NONVCL
    // 2     chunkCount: 3 command: 0x00001 condition: 95 threshold: 12
    //   1  OFFi: nalNo: 10 nalCount: 0 fragment: 1 
    //      CSi: contentSize: 1419  SIGi:  0
    //      OFi:  FFi: false  NAL: VCL
    //   2  OFFi: nalNo: 10 nalCount: 0 fragment: 1 
    //      CSi: contentSize: 0  SIGi:  0
    //      OFi:  FFi: false  NAL: VCL
    //   3  OFFi: nalNo: 10 nalCount: 0 fragment: 1 
    //      CSi: contentSize: 0  SIGi:  0
    //      OFi:  FFi: false  NAL: VCL

    /**
     * Take a ChunkInfo and try to rebuild some NALs.
     * A ChunkInfo is passed in, and potentially some NALs are passed back.
     */
    public RebuildState process(ChunkInfo svcChunk) {
        SVCChunkInfo chunk = (SVCChunkInfo)svcChunk;
        
        NALType nalType = chunk.getNALType();
        int nalCount = chunk.getNALCount();
        int nalNumber = chunk.getNALNumber();

        // we got the expected NAL number
        
        if (nalType == NALType.NONVCL) {
            // got some NONVCL data

            if (Verbose.level >= 2) {
                System.err.println("process NONVCL");
            }
            
            // Check that the chunk we have is the one we are expecting.
            // The nalNumber of the chunk should be the same as the expectedNALNo.
            // If it is not, then something has gone missing
            if (nalNumber != expectedNALNo) {
                // something went wrong in transmission

                if (Verbose.level >= 2) {
                    System.err.println("ERROR NONVCL: nalNumber " + nalNumber + "  !=  expectedNALNo: " + expectedNALNo);
                }

                RebuildState state =  new RebuildState(RebuildState.State.MISSSING, nalType, nalNumber);

                return state;
            } else {
                // try and parse them from the payload bytes
                ChunkContent content = chunk.getChunkContent(0);
                byte[] payload = content.getPayload();
                H264InputStream inStream = new H264InputStream(new ByteArrayInputStream(payload));

                // we expect nalCount NONVCL NALs packed into the payload
                List<NAL> list = new ArrayList<NAL>();
            
                for (int n=0; n<nalCount; n++) {
                    NAL nextNAL = inStream.getNAL();
                    list.add(nextNAL);
                }

                // finished with the stream
                try { 
                    inStream.close();
                } catch (IOException ioe) {
                    ;
                }

                // Return the NALs
                RebuildState state = new RebuildState(RebuildState.State.NAL_VALUES, nalType, nalNumber, list);
        
                return state;
            }
        } else {
            // VCL

            if (Verbose.level >= 2) {
                System.err.println("process VCL");
            }
            
            // Check that the chunk we have is the one we are expecting.
            // The nalNumber of the chunk should be the same as the expectedNALNo.
            // If it is not, then something has gone missing
            if (nalNumber != expectedNALNo) {
                // something went wrong in transmission

                if (Verbose.level >= 2) {
                    System.err.println("ERROR VCL: nalNumber " + nalNumber + "  !=  expectedNALNo: " + expectedNALNo);
                }

                RebuildState state =  new RebuildState(RebuildState.State.MISSSING, nalType, nalNumber);

                return state;
            } else {
                // Keep track of no of lastFragments
                int lastFragCount = 0;
                
                // Add the the chunks to the nalChunkLists
                ChunkContent[] content = chunk.getChunkContent();
                

                for (int c=0; c<content.length; c++) {
                    // Add each ChunkContent to the relevant list

                    if (Verbose.level >= 1) {
                        System.err.println("CHUNK: nalNo: " + (nalNumber+c) + " BPP: content[" + c + "] = " +  content[c].offset() + " fragment: " + content[c].getFragmentationNumber()  + " isLastFragment: " + content[c].isLastFragment() + " isDropped: " + content[c].isDropped());
                    }

                    // add this to the nalChunkLists
                    nalChunkLists[c].add(content[c]);

                    // is it a lastFragment
                    if (content[c].isLastFragment()) {
                        lastFragCount++;
                    }
                }

                // if the no of lastFragCount == noOfVCLs
                // then we are completed
                if (lastFragCount == noOfVCLs) {
                    // still processing
                    RebuildState state =  new RebuildState(RebuildState.State.FRAGMENT_END, nalType, nalNumber);
                    return state;
                } else {
                    // still processing
                    RebuildState state =  new RebuildState(RebuildState.State.PROCESSING, nalType, nalNumber);
                    return state;
                }
        
            }
        }        
    }


    /**
     * Convert a List<ChunkContent> into a NAL
     */
    protected NALResult convertLayer(int nalNo, NALType nalType, int layer, List<ChunkContent> contentList) {
        // first we find the size of the content
        // if it is not zero we check for no of marker bytes: is it 3 or is it 4
        int markerSize = 0;

        if (contentList.size() == 0) {
            return new NALResult(NALResult.State.DROPPED, nalType, layer);            
        } else {

            //System.err.println("convertLayer: layer " + layer + " first chunk length " + content0.offset() + " for " + contentList.size() + " chunks");
            boolean missingChunk = false;
            NALResult missingRetVal = null;

            {
                // Now we check if all the fragments arrived
                int expectedFragNo = 1;

                // skip through all the chunks
                for (ChunkContent chunk : contentList) {
                    if (chunk.getFragmentationNumber() == expectedFragNo) {
                        //System.err.println("convertLayer: expectedFragNo " + expectedFragNo + " got " + chunk.getFragmentationNumber());
                        // looks good
                        expectedFragNo++;

                        if (Verbose.level >= 1) {
                            System.err.println("CONVERT: nalNo: " + nalNo + " layer: " + layer + " size: " + chunk.offset() + " fragment: " + chunk.getFragmentationNumber() + " isLastFragment: " + chunk.isLastFragment() + " isDropped: " + chunk.isDropped());
                        }


                        // if a chunk is dropped
                        // then the NAL cannot be rebuilt
                        // so it is marked as WASHED
                        if (chunk.isDropped()) {
                            if (Verbose.level >= 2) {
                                System.err.println("convertLayer: layer " + layer + " is dropped");
                            }
                            
                            missingChunk = true;
                            missingRetVal = new NALResult(NALResult.State.WASHED, nalType, layer);
                        }

                        // now check if last fragmentNumber
                        if (chunk.isLastFragment()) {
                            // good
                            break;
                        }
                    } else {
                        // something was lost in transmission
                        if (Verbose.level >= 2) {
                            System.err.println("convertLayer: layer " + layer + " LOST");
                        }

                        missingChunk = true;
                        missingRetVal = new NALResult(NALResult.State.LOST, nalType, layer);
                    }
                }

                
                // Is anytihng missing
                if (missingChunk) {
                    return missingRetVal;
                    
                } else {
                    // first we work out the size of the total space
                    int size = 0;
                    ChunkContent content0 = null;

                    for (ChunkContent chunk : contentList) {
                        // add size
                        int len = chunk.getPayload().length;
                        size += len;

                        if (content0 == null && len > 0) {
                            // find first ChunkContent which has data
                            content0 = chunk;
                        }
                    }

                    // Build a NAL
                    byte[] chunk0 = content0.getPayload();


                    if (chunk0[0] == 0 && chunk0[1] == 0 && chunk0[2] == 1 ) {
                        markerSize = 3;
                    } else if (chunk0[0] == 0 && chunk0[1] == 0 && chunk0[2] == 0 && chunk0[3] == 1) {
                        markerSize = 4;
                    } else {
                        if (Verbose.level >= 2) {
                            System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  chunk0[0], chunk0[1], chunk0[2], chunk0[3]);
                            System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  chunk0[4], chunk0[5], chunk0[6], chunk0[7]);
                        }

                        
                        throw new Error("Marker not matching size 3 or 4");
                    }

                    

                    // Now we build a single result
                    byte[] single = new byte[size];
                    int bufPos = 0;

                    // Now we fill it
                    for (ChunkContent chunk : contentList) {
                        byte[] payload = chunk.getPayload();

                        // now add the content bytes to single
                        // source_arr,  sourcePos,  dest_arr,  destPos, len
                        System.arraycopy(payload, 0, single, bufPos, payload.length);

                        bufPos += payload.length;
            
                    }

                    // We wrap the byte[] with a ByteBuffer
                    ByteBuffer buffer = ByteBuffer.wrap(single);

                    // and build the NAL
                    NAL nal = new NAL(markerSize, single.length, buffer);

                    return new NALResult(NALResult.State.NAL, nalType, layer, nal);
                }
            }
        }
    }
    
    /**
     * Setup nalChunkLists
     */
    private void setupNALChunkLists() {
        for (int i=0; i<nalChunkLists.length; i++) {
            nalChunkLists[i] = new ArrayList<ChunkContent>();
        }
    }

    /**
     * Setup callback function.
     * Can be used for printouts, etc.
     */
    public void onChunk(ChunkInfoMethod method) {
        onChunk = method;
    }

}
