package test;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.net.DatagramPacket;

import cc.clayman.chunk.*;
import cc.clayman.net.*;
import cc.clayman.util.Verbose;

// A first test of Printing Chunks from network

public class TestNetPC1 {

    static UDPReceiver receiver = null;
    
    static int count = 0;
    static int total = 0;

    // listen port
    static int udpPort = 6789;

    public static void main(String[] args) {
        if (args.length >= 1) {
            
            // have flags too

            int argc = 0;
            String arg0 = args[argc];

            if (arg0.equals("-p")) {
                // Port
                argc++;

                String val = args[argc];
                udpPort = Integer.parseInt(val);
            } else if (arg0.startsWith("-v")) {
                if (arg0.equals("-v")) {
                    Verbose.level = 1;
                } else  if (arg0.equals("-vv")) {
                    Verbose.level = 2;
                } else  if (arg0.equals("-vvv")) {
                    Verbose.level = 3;
                }
            }
        }

        
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("TestPC1");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        DatagramPacket packet;

        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();
        
        while ((packet = receiver.getPacket()) != null) {

            count++;

            total += packet.getLength();

            printPacket(packet, count, total);

        }

        // stop receiver
        receiver.stop();

    }
    
    protected static void printPacket(DatagramPacket packet, int count, int total) {
        // header inspector
        BPPHeaderInspector inspector = new BPPHeaderInspector(packet);

        int chunkCount = inspector.getChunkCount();

        // BPPSVCDepacketizer returns SVCChunkInfo
        ChunkDepacketizer depacketizer = new BPPSVCDepacketizer();


        SVCChunkInfo chunk = (SVCChunkInfo)depacketizer.convert(packet);

        // sequence
        int seqNo = chunk.getSequenceNumber();

        int nalCount = chunk.chunkCount();

        int nalNo = chunk.getNALNumber();

        


        System.out.printf("%-8d", count);               // N
        System.out.printf("%-10d", seqNo);               // seq no
        System.out.printf("%-10d", nalNo);               // NAL no
        System.out.printf("%-8d", nalCount);               // NAL count
        System.out.printf(" %-5d", packet.getLength());         // no of bytes
        System.out.printf(" %-10d", total);             // total bytes

        System.out.printf(" %-5d", packet.getLength() - 4);         // no of payload bytes
    
        // Visit the Content
        ChunkContent[] content = chunk.getChunkContent();

        System.out.printf("%-4d", content.length);                  // content length

        for (int c=0; c<content.length; c++) {
                
            int contentSize = content[c].offset();
            System.out.printf(" %-5d", contentSize);                // no of bytes chunk
        }

        System.out.println();

        cc.clayman.terminal.ChunkDisplay displayer = new cc.clayman.terminal.SVCChunkDisplay(1500);
        displayer.display(chunk);
        System.out.println();


    }
    
}
