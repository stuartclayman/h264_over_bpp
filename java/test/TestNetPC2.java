package test;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.net.DatagramPacket;

import cc.clayman.chunk.*;
import cc.clayman.net.*;
import cc.clayman.util.Verbose;
import cc.clayman.terminal.ChunkDisplay;


// A second test of Printing Chunks from network

public class TestNetPC2 {

    static UDPReceiver receiver = null;
    
    static int count = 0;
    static int total = 0;

    // the last sequence no seen
    static int lastSeqNo = 0;

    
    // listen port
    static int udpPort = 6789;

    // show Displayer
    static boolean displayerOn = false;

    public static void main(String[] args) {
        if (args.length >= 1) {
            
            // have flags too

            int argc = 0;
            while (argc < args.length) {
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
                } else if (arg0.equals("-D")) {
                    displayerOn = true;
                }

                argc++;

            }
        }

        
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("TestPC2");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        DatagramPacket packet;

        // Setup UDP Receiver
        receiver = new UDPReceiver(udpPort);
        receiver.start();
        
        while (! receiver.isEOF()) {

            packet = receiver.getPacket();

            if (packet == null) {
                // the receiver has nothing to pass on
                break;
            } else {
                count++;

                total += packet.getLength();

                printPacket(packet, count, total);
            }

        }

        // stop receiver
        receiver.stop();

    }
    
    protected static void printPacket(DatagramPacket packet, int count, int total) {
        // header inspector
        BPPHeaderInspector inspector = new BPPHeaderInspector(packet);

        int chunkCount = inspector.getChunkCount();

        // BPPDepacketizer returns ChunkInfo
        ChunkDepacketizer depacketizer = new BPPDepacketizer();


        ChunkInfo chunk = depacketizer.convert(packet);

        // sequence
        int seqNo = chunk.getSequenceNumber();

        // check if anything in the sequence is missing

        if (seqNo > lastSeqNo + 1) {
            // missing
            for (int m=lastSeqNo+1; m<seqNo; m++) {
                System.out.printf("%-8d", count);            // N
                System.out.printf("%-10d", m);               // missing seq no
                System.out.println();
                //count++;
            }
        }


        System.out.printf("%-8d", count);               // N
        System.out.printf("%-10d", seqNo);               // seq no
        System.out.printf(" %-5d", packet.getLength());         // no of bytes
        System.out.printf(" %-10d", total);             // total bytes

        System.out.printf(" %-5d", packet.getLength() - 4);         // no of payload bytes
    
        // Visit the Content
        ChunkContent[] content = chunk.getChunkContent();

        System.out.printf("%-4d", content.length);                  // content length

        for (int c=0; c<content.length; c++) {
                
            if (content[c].isDropped()) {
                System.out.printf(" %-5s", "D");                // dropped
            } else {
                int contentSize = content[c].offset();
                System.out.printf(" %-5d", contentSize);        // no of bytes chunk
            }
        }

        System.out.println();

        lastSeqNo = seqNo;

        if (displayerOn) {
            ChunkDisplay displayer = new ChunkDisplay(1500);
            displayer.display(chunk);
            System.out.println();
        }


    }
    
}
