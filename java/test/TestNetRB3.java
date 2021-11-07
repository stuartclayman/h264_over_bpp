package test;

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

// A test of the MultiNALRebuilder

public class TestNetRB3 {

    static UDPReceiver receiver = null;
    static UDPChunkStreamer streamer = null;
    static MultiNALRebuilder rebuilder = null;

    static int count = 0;
    static int total = 0;

    static int NO_OF_VCLS = 3;

    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    // listen port
    static int udpPort = 6799;

    // output filename
    static String filename = null;
    static NALOutputStream outputStream = null;


    public static void main(String[] args) {
        if (args.length == 0) {
        } else if (args.length == 1) {
            String val = args[0];
            udpPort = Integer.parseInt(val);
            System.err.println("Listen on port: " + udpPort);
            
        } else if (args.length >= 2) {
            // have flags too

            int argc = 0;

            while (argc < args.length-2) {  // allow for port at end
                String arg0 = args[argc];

                if (arg0.equals("-f")) {
                    // Output filename
                    argc++;
                    filename = args[argc];
                } else {
                    usage();
                }

                argc++;
            }

            String val = args[argc];
            udpPort = Integer.parseInt(val);
            System.err.println("Listen on port: " + udpPort);
            
        } else {
            usage();
        }

        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("TestNetRB3 [-f filename] [port]");
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


        // open file - maybe
        if (filename != null) {
            try {
                outputStream = new NALOutputStream(new FileOutputStream(filename));
            } catch (IOException ioe) {
            }
        }

        // Timer stuff
        startTime = System.currentTimeMillis();
        lastTime = System.currentTimeMillis();

        // Wait for 5 secs before ending
        TimerTask timerTask = new TimedCount(streamer, 5);

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

                System.err.println(nalResult);




                if (nalResult.state == NALResult.State.NAL) {

                    nal = nalResult.getNAL().get();

                } else if (nalResult.state == NALResult.State.WASHED) {
                    System.err.println("Washed " + nalResult.number + " / " + qualityLayer);

                    droppedLayer[qualityLayer] = true;
                    continue;
                                    
                } else if (nalResult.state == NALResult.State.DROPPED) {
                    // there was a reason to not rebuild the NAL
                    // probably data was stripped in the network
                    System.err.println("Dropped " + nalResult.number + " / " + qualityLayer);
                    droppedLayer[qualityLayer] = true;
                    continue;

                }

                                
                if (nal != null) {
                    count++;
                    total += nal.getSize();

                    // A good test of the system
                    // Can we print using the printChunk()
                    // from a class on the server side
                    Test2.printNAL(nal, count, total);

                    // Is it a VCL 
                    if (nal.isVideo()) {
                        // Keep track of no of VCLs
                        if (prevIsNonVCL) {
                            vclCount++;
                            prevIsNonVCL = false;
                        }


                        // Look at TemporalLayerModelGOB16
                        TemporalLayerModel.Tuple currentNALModel = model.getLayerInfo(vclCount);

                        System.out.printf("Frame No: %s Frame: %s Temporal: %s  Qualitylayer: %d\n", vclCount + currentNALModel.adjustment, currentNALModel.frame, currentNALModel.temporal, qualityLayer);

                        if (currentNALModel.frame == Frame.I && qualityLayer == 0) {
                            // It's an I frame, so reset droppedLayers
                            resetDroppedLayer(droppedLayer);
                        }
                           
                        // write out nal
                        if (droppedLayer[qualityLayer] == false)  {
                            writeNAL(outputStream, nal);
                        } else {
                            System.err.println("NOT WRITING " + nalResult.number + " / " + qualityLayer);
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
                    System.err.println("stopping");
                    System.out.flush();
                    streamer.stop();
                    cancel();
                }
                        
                long elaspsedSecs = (thisTime - startTime)/1000;
                long elaspsedMS = (thisTime - startTime)%1000;

                //System.err.println("Time: " + elaspsedSecs + "." + elaspsedMS);

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
