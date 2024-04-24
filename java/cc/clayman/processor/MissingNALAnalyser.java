// MissingNALAnalyser.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: April 2024

package cc.clayman.processor;

import cc.clayman.h264.NAL;
import cc.clayman.h264.Frame;
import cc.clayman.h264.TemporalLayerModel;
import cc.clayman.h264.TemporalLayerModelGOB16;
import cc.clayman.processor.NALResult;
import cc.clayman.util.Verbose;
import java.util.Iterator;


/*
 * This implements the analysing and handling of missing NALs.
 *
 * See the paper:
 * The Effects of Packet Wash on SVC Video in Limited Bandwidth Environments
 * Stuart Clayman and Muge Sayit
 * IEEE 23rd International Conference on High Performance Switching and Routing (HPSR), June 2022.
 *
 * NALs are analysed by considering the layer dependencies. We do not
 * need to put out Layer 1 or Layer 2 NALs if the preceding frames
 * have not been reconstructed,
 */
public class MissingNALAnalyser implements Iterator<NAL> {
    // A NALRebuilder
    NALRebuilder rebuilder;

    // No of VCLs to expect
    int noOfVCLs = 1;

    int vclCount = 0;
    int qualityLayer = 0;
    boolean prevIsNonVCL  = true;
    TemporalLayerModel model = null;

    boolean [] droppedLayer = null;

    int count = 0;
    int total = 0;
    
    /**
     * MissingNALAnalyser
     * Takes a NALResult generator and returns NALs
     */
    public MissingNALAnalyser(NALRebuilder rebuilder, int noOfVCLs) {
        this.rebuilder = rebuilder;
        this.noOfVCLs = noOfVCLs;

        model = new TemporalLayerModelGOB16();
        droppedLayer = new boolean[noOfVCLs];
        
        resetDroppedLayer(droppedLayer);
    }

    /**
     * Start the handler
     */
    public boolean start() {
        rebuilder.start();
        return true;
    }
    
    /**
     * Stop the handler
     */
    public boolean stop() {
        rebuilder.stop();
        return true;
    }
    
    

    /**
     * Returns true if the iteration has more elements.
     */
    public boolean hasNext() {
        if (Verbose.level >= 3) {
            System.err.println("  hasNext()");
        }

        if (! rebuilder.hasNext()) {
            // chunkStreamer is finshed
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns the next NAL in the iteration.
     * so the caller can determine
     * what the rebuilder has discovered from the stream of Chunks.
     */
    public NAL next() {
        if (Verbose.level >= 3) {
            System.err.println("  next()");
        }

        NAL nal = null;
        
        while (rebuilder.hasNext()) {
            NALResult nalResult = rebuilder.next();

            if (nalResult != null) {

                if (Verbose.level >= 1) {
                    System.err.println(qualityLayer + " " + nalResult);
                }


                

                if (nalResult.state == NALResult.State.NAL) {

                    nal = nalResult.getNAL().get();

                    if (!nal.isVideo()) {
                        qualityLayer = 0;
                    }

                    if (Verbose.level >= 1) {
                        System.err.println("LISTEN: NAL " + nalResult.nalType + " " + nalResult.number + " / " + qualityLayer + " Time: " + System.nanoTime());
                    }

                } else if (nalResult.state == NALResult.State.WASHED) {

                    droppedLayer[qualityLayer] = true;

                    if (Verbose.level >= 1) {
                        System.err.println("LISTEN: WASHED " + nalResult.nalType + " " + nalResult.number + " / " + qualityLayer + " Time: " + System.nanoTime());

                        System.err.printf("QUALITY: WASHED VCLNo: %d Qualitylayer: %d Time: %d\n", vclCount, qualityLayer, System.nanoTime());
                        
                    }



                    // increase qualityLayer for next time
                    qualityLayer = (qualityLayer + 1) % noOfVCLs;

                    continue;
                                    
                } else if (nalResult.state == NALResult.State.DROPPED) {
                    // there was a reason to not rebuild the NAL
                    // probably data was stripped in the network
                    droppedLayer[qualityLayer] = true;

                    // increase qualityLayer for next time
                    qualityLayer = (qualityLayer + 1) % noOfVCLs;


                    if (Verbose.level >= 1) {
                        System.err.println("LISTEN: DROPPED " +  nalResult.nalType + " " + nalResult.number + " / " + qualityLayer + " Time: " + System.nanoTime());
                    }

                    
                    continue;

                }

                                
                if (nal != null) {
                    count++;
                    total += nal.getSize();

                    // A good test of the system
                    // Can we print using the printChunk()
                    // from a class on the server side
                    // printNAL(nal, count, total);

                    // Is it a VCL 
                    if (nal.isVideo()) {
                        // Keep track of no of VCLs
                        if (prevIsNonVCL) {
                            vclCount++;
                            prevIsNonVCL = false;                            
                        }


                        // Look at TemporalLayerModelGOB16
                        TemporalLayerModel.Tuple currentNALModel = model.getLayerInfo(vclCount);

                        if (currentNALModel.frame == Frame.I && qualityLayer == 0) {
                            // It's an I frame, so reset droppedLayers
                            resetDroppedLayer(droppedLayer);
                        }
                           
                        // What should we do with the NAL
                        if (droppedLayer[qualityLayer] != true)  {
                            // The NAL at this qualityLayer is not washed away
                            
                            if (Verbose.level >= 1) {
                                System.err.printf("QUALITY: COLLECT VCLNo: %d FrameNo: %s Frame: %s Temporal: %s  Qualitylayer: %d Time: %d\n", vclCount, vclCount + currentNALModel.adjustment, currentNALModel.frame, currentNALModel.temporal, qualityLayer, System.nanoTime());  // WAS currentTimeMillis());
                            }

                            
                            //if (Verbose.level >= 1) {
                            //    System.err.println("RETURN qualityLayer: " + qualityLayer + " droppedLayer[" + qualityLayer + "] = " + droppedLayer[qualityLayer]);
                            //}
                            
                            // increase qualityLayer for next time
                            qualityLayer = (qualityLayer + 1) % noOfVCLs;


                            return nal;
                        } else {
                            // droppedLayer[qualityLayer] is true
                            // which means a previous NAL at this qualityLayer
                            // was washed away, so we cannot use it as
                            // there is a dependency
                            if (Verbose.level >= 1) {
                                System.err.println("LISTEN: NOT_WRITING " + nalResult.number + " / " + qualityLayer + " Time: " + System.nanoTime());


                                System.err.printf("QUALITY: NOT_WRITING VCLNo: %d FrameNo: %s Frame: %s Temporal: %s  Qualitylayer: %d Time: %d\n", vclCount, vclCount + currentNALModel.adjustment, currentNALModel.frame, currentNALModel.temporal, qualityLayer, System.nanoTime());  // WAS currentTimeMillis());
                            }

                            // increase qualityLayer for next time
                            qualityLayer = (qualityLayer + 1) % noOfVCLs;

                        }

                        
                    } else {
                        // NAL is NOT video

                        prevIsNonVCL = true;
                        // reset qualityLayer
                        qualityLayer = 0;

                        //if (Verbose.level >= 1) {
                        //    System.err.println("RETURN qualityLayer: " + qualityLayer + " NOT VIDEO");
                        //}
                            
                        return nal;
                    }

                    
                        
                }
            }
        }

        return null;
    }


    private void resetDroppedLayer(boolean [] droppedLayer) {
        for (int i=0; i < noOfVCLs; i++) {
            droppedLayer[i] = false;
        }
    }

    
}
