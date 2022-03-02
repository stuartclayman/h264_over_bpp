package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.net.DatagramPacket;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALProcessor;
import cc.clayman.net.*;
import cc.clayman.terminal.ChunkDisplay;
import cc.clayman.util.Verbose;

// A UDP receiver 

public class UDPListen {

    static UDPReceiver receiver = null;

    static int count = 0;
    static int total = 0;


    // keep time
    static long startTime = 0;
    static long lastTime = 0;

    // listen port
    static int udpPort = 6799;

    // output filename
    static String filename = null;
    static OutputStream outputStream = null;

    static int columns = 80;    // default no of cols on terminal


    public static void main(String[] args) {
        if (args.length == 0) {
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

                } else if (arg0.equals("-c")) {            
                    // columns
                    argc++;

                    String val = args[argc];
                    columns = Integer.parseInt(val);

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
            System.err.println("Columns: " + columns);
        }
        
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }



    static void usage() {
        System.err.println("UDPListen [-f [-|filename]] [-c cols] [-p port]");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        DatagramPacket packet;

        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();

        // open file - maybe
        if (filename != null) {
            try {
                if (filename.equals("-")) {
                    outputStream = System.out;

                    if (Verbose.level >= 2) {
                        System.err.println("Output stream: STDOUT" );
                    }
                } else {
                    outputStream = new FileOutputStream(filename);

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
        Timer timer = null;

        // set up timer to count throughput
        TimerTask timerTask = new TimedCount(5);

        // if there is no timer, start one
        if (timer == null) {
            timer = new Timer();
            timer.schedule(timerTask, 1000, 1000);
        }

        ChunkDepacketizer depacketizer = new RawDepacketizer();

        int lastSeen = 0;

        while ((packet = receiver.getPacket()) != null) {
            lastTime = System.currentTimeMillis();
            
            // RawDepacketizer returns SVCChunkInfos
            SVCChunkInfo chunk = (SVCChunkInfo)depacketizer.convert(packet);
            
            NALType type = chunk.getNALType();
            int nalNo = chunk.getNALNumber();
            int nalCount = chunk.getNALCount();
            int seqNo = chunk.getSequenceNumber();

            count++;

            total += chunk.offset();

            if (Verbose.level >= 1) {
                System.err.println("LISTEN: RECEIVE seqNo: " + seqNo + " NALNumber: " + nalNo + " count: " + nalCount + " NALType: " + type + " lastSeen: " + lastSeen);
            }

            if (lastSeen+1 == seqNo) {

                writePacketData(outputStream, packet, count, total);

                // A good test of the system
                // Can we print using the printChunk()
                // from a class on the server side
                printChunk(chunk, seqNo, total, packet.getData().length);
                //TestMNP1.printChunk(chunk, count, total);

                lastSeen = seqNo;

            } else {
                int missing = seqNo - lastSeen;

                if (Verbose.level >= 1) {
                    for (int m=1; m<=missing; m++) {
                        System.err.printf("%-18s", "LISTEN: DROPPED ");           //
                        System.err.printf("%-8d", lastSeen + m);    // N
                
                        System.err.println("");
                    }
                }

                lastSeen = seqNo;
            }

        }

        if (Verbose.level >= 3) {
            System.err.println("end of loop");
        }

        timer.cancel();

        if (outputStream != null) {
            outputStream.close();
        }

        // stop receiver
        receiver.stop();

    }

    private static void writePacketData(OutputStream outputStream, DatagramPacket packet, int count, int total) {
        try {
            if (outputStream != null) {
                printPacket(outputStream, packet, count, total);
            }
        } catch (IOException ioe) {
            System.err.println("Cant write to " + filename + " " + ioe);
        }
    }

    
    
    protected static void printPacket(OutputStream outputStream, DatagramPacket packet, int count, int total) throws IOException {
        byte[] packetBytes = packet.getData();
        

        // Now extract the header
        // 32 bits for sequence
        byte seq0 = packetBytes[0];
        byte seq1 = packetBytes[1];
        byte seq2 = packetBytes[2];
        byte seq3 = packetBytes[3];

        // 24 bits for nalNo
        byte b0 = packetBytes[4];
        byte b1 = packetBytes[5];
        byte b2 = packetBytes[6];

        // 7 bits for count + 1 bit for type
        byte b3 = packetBytes[7];

        //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[0], packetBytes[1], packetBytes[2], packetBytes[3]);


        int sequence = 0 | ((seq0 << 24)  & 0xFF000000) | ((seq1 << 16)  & 0x00FF0000) | ((seq2 << 8) & 0x0000FF00) | ((seq3 << 0) & 0x000000FF);
        
        int nalNo = 0 | ((b0 << 16)  & 0x00FF0000) | ((b1 << 8) & 0x0000FF00) | ((b2 << 0) & 0x000000FF);
        

        int nalCount = (b3 & 0xFE) >> 1;
        int type = b3 & 0x01;

        NALType nalType;
        
        if (type == 0 || type == 1)  {
            nalType = (type == 0 ? NALType.VCL : NALType.NONVCL);
        } else {
            throw new Error("Invalid NALType number " + type);
        }
        


        int packetLen = packet.getLength();            // no of bytes

        int payloadLen = packet.getLength() - RawPacketizer.HEADER_SIZE;      // no of payload bytes

        for (int b=RawPacketizer.HEADER_SIZE; b<packetLen; b++) {
            outputStream.write(packetBytes[b]);
        }
    }
    
    // set up timer to count throughput
    private static class TimedCount extends TimerTask {
        boolean running = true;
        // time to wait - in seconds - for no traffic
        int timeOut;
        
        public TimedCount(int timeOut) {
            this.timeOut = timeOut;
        }

        @Override
        public void run() {
            if (running) {

                long thisTime = System.currentTimeMillis();


                if (count != 0 && ((thisTime - lastTime) / 1000) >= 5) {
                    // no recv after 5 secs
                    if (Verbose.level >= 3) {
                        System.err.println("stopping");
                    }
                    System.out.flush();
                    receiver.stop();
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

    protected static void printChunk(SVCChunkInfo chunk, int count, int total, int payloadSize) {
        
        // try and find the no of columns from the Environment
        String columnsEnv = System.getenv("COLUMNS");

        if (columnsEnv != null) {
            int columnVal = Integer.parseInt(columnsEnv);

            columns = columnVal;
        }

        System.out.printf("%-8d", count);                     // N
        System.out.printf("%-10s", chunk.getNALType());       // type

        // used up 18 chars

        ChunkDisplay displayer = new ChunkDisplay(columns - 22, payloadSize);
        displayer.display(chunk);
        
        System.out.println(" ");
                    
    }
    

}
