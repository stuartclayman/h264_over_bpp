package test;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;
import java.net.DatagramPacket;

import cc.clayman.h264.*;
import cc.clayman.chunk.*;
import cc.clayman.processor.MultiNALProcessor;
import cc.clayman.net.*;

// A first test of the UDPReceiver

public class TestNetR1 {

    static UDPReceiver receiver = null;

    static int count = 0;
    static int total = 0;

    public static void main(String[] args) {
        try {
            processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("TestNetR1");
        System.exit(1);
    }


    protected static void processTraffic() throws IOException {
        DatagramPacket packet;

        // Setup UDP Receiver
        receiver = new UDPReceiver(6798);
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
        byte[] packetBytes = packet.getData();
        
        // Now extract the header
        // 24 bits for nalNo
        byte b0 = packetBytes[0];
        byte b1 = packetBytes[1];
        byte b2 = packetBytes[2];

        // 7 bits for count + 1 bit for type
        byte b3 = packetBytes[3];

        //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[0], packetBytes[1], packetBytes[2], packetBytes[3]);


        int nalNo = 0 | ((b0 << 16)  & 0x00FF0000) | ((b1 << 8) & 0x0000FF00) | ((b2 << 0) & 0x000000FF);
        

        int nalCount = (b3 & 0xFE) >> 1;
        int type = b3 & 0x01;

        NALType nalType;
        
        if (type == 0 || type == 1)  {
            nalType = (type == 0 ? NALType.VCL : NALType.NONVCL);
        } else {
            throw new Error("Invalid NALType number " + type);
        }
        

        System.out.printf("%-8d", count);               // N
        System.out.printf("%-8d", nalNo);               // NAL no
        System.out.printf("%-8d", nalCount);               // NAL count
        System.out.printf("%-10s", nalType);               // type
        System.out.printf(" %-5d", packet.getLength());         // no of bytes
        System.out.printf(" %-10d", total);             // total bytes

        System.out.printf(" %-5d", packet.getLength() - 4);         // no of payload bytes
    
        System.out.println();
    }
    
}
