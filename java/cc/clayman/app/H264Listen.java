package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALRebuilder;
import cc.clayman.processor.MissingNALAnalyser;
import cc.clayman.processor.UDPChunkStreamer;
import cc.clayman.processor.BufferingUDPChunkStreamer;
import cc.clayman.net.*;
import cc.clayman.util.Verbose;


// Use the MultiNALRebuilder to create an H264 stream,
// using the MissingNALAnalyser which does NAL dependency analysis.

public class H264Listen {

    static UDPReceiver receiver = null;
    static UDPChunkStreamer streamer = null;
    static MissingNALAnalyser analyser  = null;

    static int count = 0;
    static int total = 0;

    // No of VCLs / layers per frame
    static int NO_OF_VCLS = 3;

    // keep time
    static long startTime = 0;
    static long lastTime = 0;


    // listen host
    static String host = null;
    static InetAddress inetAddr = null;
    
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

                } else if (arg0.equals("-h")) {
                    // Host
                    argc++;
                    host = args[argc];

                    try {
                        if (host.equals("localhost")) {
                            inetAddr = InetAddress.getLocalHost();
                        } else {
                            inetAddr = InetAddress.getByName(host);
                        }
                    } catch (UnknownHostException uhe) {
                        System.err.println("UnknownHostException " + host);
                        System.exit(1);
                    }

                    

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
        System.err.println("H264Listen [-f [-|filename]] [-h host] [-p port]");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        // Check where we are receiving

        if (inetAddr == null) {
            // Setup UDP Receiver
            receiver = new UDPReceiver(udpPort);
        } else {
            // got an inetAddr
            if (inetAddr.isMulticastAddress()) {
                receiver = new MulticastReceiver(new InetSocketAddress(inetAddr, udpPort));

                if (Verbose.level >= 1) {
                    System.err.println("MulticastReceiver " + inetAddr + " / " + udpPort);
                }

            } else {
                receiver = new UDPReceiver(new InetSocketAddress(inetAddr, udpPort));
            }
        }
                  
        // and the ChunkStreamer using a BPPSVCDepacketizer
        // as we know BPP SVC packets are coming
        streamer = new BufferingUDPChunkStreamer(receiver, new BPPSVCDepacketizer());
        // and the MultiNALRebuilder
        // which takes a collection of chunks and rebuild a stream on NALs
        MultiNALRebuilder rebuilder = new MultiNALRebuilder(streamer, NO_OF_VCLS);

        // Setup rebuilder printer
        if (Verbose.level >= 1) {
            rebuilder.onChunk(new SVCChunkInfoPrinter());
        }

        // plus the MissingNALAnalyser which does NAL dependency analysis
        analyser = new MissingNALAnalyser(rebuilder, NO_OF_VCLS);
        analyser.start();


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


        while (analyser.hasNext()) {
            lastTime = System.currentTimeMillis();

            // A NAL
            NAL nal = analyser.next();

            if (nal != null) {
                count++;

                // Write NAL to the outputStream
                writeNAL(outputStream, nal);
            }
            
        }

        if (outputStream != null) {
            outputStream.close();
        }

        timer.cancel();

        // stop receiver
        analyser.stop();
        rebuilder.stop();
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

    protected static void printNAL(NAL nal, int count, int total) {
        int size = nal.getSize();

        System.err.printf("%-8d", count);               // N
        System.err.printf(" %-6d", size*8);             // no of bits
        System.err.printf(" %-5d", size);               // no of bytes
        System.err.printf(" %-9d", (total - size) + 1);  // start bytes
        System.err.printf(" %-10d", total);              // end bytes

        System.err.printf(" %-1d", nal.getNRI());       // NRI
        System.err.printf(" %-2d",nal.getType());       // Type
        System.err.printf(" %-7s",nal.getTypeClass());  // VCL or non-VCL
        System.err.printf(" %s", nal.getTypeString());  // Type description
        System.err.println();
                    
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
