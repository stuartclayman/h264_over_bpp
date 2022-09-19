package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALRebuilder;
import cc.clayman.processor.UDPChunkStreamer;
import cc.clayman.processor.NALResult;
import cc.clayman.net.*;
import cc.clayman.util.Verbose;


// Use the MultiNALRebuilder to create an H264 stream

public class H264Listen {

    static UDPReceiver receiver = null;
    static UDPChunkStreamer streamer = null;
    static MultiNALRebuilder rebuilder = null;

    static int count = 0;
    static int total = 0;

    // No of VCLs / layers per frame
    static int NO_OF_VCLS = 3;

    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    // listen port
    static int udpPort = 6799;

    // output filename - default STDOUT
    static String filename = "-";
    static NALOutputStream outputStream = null;

    // timer for end of run when no traffic
    static int noTrafficEndTimerDuration = 5;
    

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
        } else if (args.length >= 1) {
            // have flags too

            int argc = 0;

            while (argc < args.length) {  // allow for port at end
                String arg0 = args[argc];

                if (arg0.equals("-f")) {
                    // Output filename
                    argc++;
                    filename = args[argc];

                } else if (arg0.equals("-p")) {
                    // Port
                    argc++;

                    String val = args[argc];
                    udpPort = Integer.parseInt(val);

                } else if (arg0.equals("-l")) {
                    // VLCs / layers
                    argc++;

                    String val = args[argc];
                    NO_OF_VCLS = Integer.parseInt(val);

                } else if (arg0.equals("-D")) {
                    // no traffic end timer duration
                    argc++;

                    String val = args[argc];
                    noTrafficEndTimerDuration = Integer.parseInt(val);

                } else if (arg0.startsWith("-v")) {
                    if (arg0.equals("-v")) {
                        Verbose.level = 1;
                    } else  if (arg0.equals("-vv")) {
                        Verbose.level = 2;
                    } else  if (arg0.equals("-vvv")) {
                        Verbose.level = 3;
                    }
            
                } else {
                    usage();
                }

                argc++;
            }

        } else {
            usage();
        }

        if (Verbose.level >= 2) {
            System.err.println("Listen on port: " + udpPort);
            System.err.println("Layers: " + NO_OF_VCLS);
        }
            
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("H264Listen [-f [-|filename]] [-p port]");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        // and the ChunkStreamer
        streamer = new UDPChunkStreamer(receiver);
        // and the MultiNALRebuilder
        rebuilder = new MultiNALRebuilder(streamer, NO_OF_VCLS);
        rebuilder.start();

        // Setup rebuilder printer
        if (Verbose.level >= 1) {
            rebuilder.onChunk(new SVCChunkInfoPrinter());
        }


        // open file - maybe
        if (filename != null) {
            try {
                if (filename.equals("-")) {
                    outputStream = new NALOutputStream(System.out);

                    if (Verbose.level >= 2) {
                        System.err.println("Output stream: STDOUT" );
                    }
                           
                } else {
                    outputStream = new NALOutputStream(new FileOutputStream(filename));

                    if (Verbose.level >= 2) {
                        System.err.println("Output file: " + filename);
                    }                    
                }
                    
            } catch (IOException ioe) {
            }
        }

        // Timer stuff
        startTime = System.currentTimeMillis();
        lastTime = System.currentTimeMillis();

        // Wait for 5 secs before ending
        TimerTask timerTask = new TimedCount(streamer, noTrafficEndTimerDuration);

        Timer timer = null;

        // if there is no timer, start one
        if (timer == null) {
            timer = new Timer();
            timer.schedule(timerTask, 1000, 1000);
        }

        // A NAL
        NAL nal = null;
        int vclCount = 0;
        int qualityLayer = 0;
        boolean prevIsNonVCL  = true;
        TemporalLayerModel model = new TemporalLayerModelGOB16();

        boolean [] droppedLayer = new boolean[NO_OF_VCLS];

        resetDroppedLayer(droppedLayer);
        
        while (rebuilder.hasNext()) {
            lastTime = System.currentTimeMillis();
            
            NALResult nalResult = rebuilder.next();

            if (nalResult != null) {

                if (Verbose.level >= 1) {
                    System.err.println(nalResult);
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
                    qualityLayer++;

                    continue;
                                    
                } else if (nalResult.state == NALResult.State.DROPPED) {
                    // there was a reason to not rebuild the NAL
                    // probably data was stripped in the network
                    droppedLayer[qualityLayer] = true;

                    // increase qualityLayer for next time
                    qualityLayer++;

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
                    printNAL(nal, count, total);

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

                            // Write NAL to the outputStream
                            writeNAL(outputStream, nal);
                        } else {
                            // droppedLayer[qualityLayer] is true
                            // which means a previous NAL at this qualityLayer
                            // was washed away, so we cannot use it as
                            // there is a dependency
                            if (Verbose.level >= 1) {
                                System.err.println("LISTEN: NOT_WRITING " + nalResult.number + " / " + qualityLayer + " Time: " + System.nanoTime());


                                System.err.printf("QUALITY: NOT_WRITING VCLNo: %d FrameNo: %s Frame: %s Temporal: %s  Qualitylayer: %d Time: %d\n", vclCount, vclCount + currentNALModel.adjustment, currentNALModel.frame, currentNALModel.temporal, qualityLayer, System.nanoTime());  // WAS currentTimeMillis());
                            }

                        }

                        
                        // increase qualityLayer for next time
                        qualityLayer++;

                    } else {
                        prevIsNonVCL = true;
                        qualityLayer = 0;

                        writeNAL(outputStream, nal);
                        
                    }

                    
                        
                }
            }
        }

        timer.cancel();

        if (outputStream != null) {
            outputStream.close();
        }

        // stop receiver
        streamer.stop();

    }

    private static void writeNAL(NALOutputStream outputStream, NAL nal) {
        try {
            if (outputStream != null) {
                outputStream.write(nal);
            }
        } catch (IOException ioe) {
            System.err.println("Cant write to " + filename + " " + ioe);
        }
    }

    private static void resetDroppedLayer(boolean [] droppedLayer) {
        for (int i=0; i < NO_OF_VCLS; i++) {
            droppedLayer[i] = false;
        }
    }

    protected static void printNAL(NAL nal, int count, int total) {
        int size = nal.getSize();

        System.out.printf("%-8d", count);               // N
        System.out.printf(" %-6d", size*8);             // no of bits
        System.out.printf(" %-5d", size);               // no of bytes
        System.out.printf(" %-9d", (total - size) + 1);  // start bytes
        System.out.printf(" %-10d", total);              // end bytes

        System.out.printf(" %-1d", nal.getNRI());       // NRI
        System.out.printf(" %-2d",nal.getType());       // Type
        System.out.printf(" %-7s",nal.getTypeClass());  // VCL or non-VCL
        System.out.printf(" %s", nal.getTypeString());  // Type description
        System.out.println();
                    
    }
    
    
    // set up timer to count throughput
    private static class TimedCount extends TimerTask {
        boolean running = true;
        ChunkStreamer streamer;
        // time to wait - in seconds - for no traffic
        int timeOut;
        
        public TimedCount(ChunkStreamer s, int timeOut) {
            streamer = s;
            this.timeOut = timeOut;
        }

        @Override
        public void run() {
            if (running) {

                long thisTime = System.currentTimeMillis();


                if (count != 0 && ((thisTime - lastTime) / 1000) >= timeOut) {
                    // no recv after 5 secs
                    if (Verbose.level >= 2) {
                        System.err.println("stopping");
                    }
                    
                    System.out.flush();
                    streamer.stop();
                    cancel();
                }
                        
                long elaspsedSecs = (thisTime - startTime)/1000;
                long elaspsedMS = (thisTime - startTime)%1000;

                if (Verbose.level >= 3) {
                    System.err.println("Time: " + elaspsedSecs + "." + elaspsedMS);
                }

            }
        }

        @Override
        public boolean cancel() {
            if (running) {
                running = false;
            }

            return running;
        }

        @Override
        public long scheduledExecutionTime() {
            return 0;
        }

    }


}
