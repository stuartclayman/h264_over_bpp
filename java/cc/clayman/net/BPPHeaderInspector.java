// BPPHeaderInspector.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.net;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

import cc.clayman.chunk.SVCChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.chunk.SVCChunks;
import cc.clayman.h264.NALType;
import cc.clayman.net.IP;
import cc.clayman.bpp.BPP;
import cc.clayman.util.Verbose;

/**
 * Take a DatagramPacket which is in BPP format and inspect the header.
 */
public class BPPHeaderInspector {
    int chunkCount=0;
    int command = 0;
    int condition = 0;
    int threshold = 0;
    int sequence = 0;

    /**
     * Constructor
     */
    public BPPHeaderInspector(DatagramPacket packet) throws UnsupportedOperationException {
        byte[] packetBytes = packet.getData();

        int bufPos = 0;
        
        // Now extract BPP header - 32 bits for BPP
        byte b0 = packetBytes[0];
        byte b1 = packetBytes[1];
        byte b2 = packetBytes[2];
        byte b3 = packetBytes[3];

        bufPos += BPP.BLOCK_HEADER_SIZE;

        // Check version pattern
        int version = (b0 & 0xF0) >> 4;
        chunkCount = (b2 & 0xF8) >> 3;

        //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[0], packetBytes[1], packetBytes[2], packetBytes[3]);

        // Now extract the Command Block
        byte b4 = packetBytes[4];
        byte b5 = packetBytes[5];
        byte b6 = packetBytes[6];

        // Get the Sequence No bytes
        byte b7 = packetBytes[7];
        byte b8 = packetBytes[8];
        byte b9 = packetBytes[9];
        byte b10 = packetBytes[10];
        

        bufPos += BPP.COMMAND_BLOCK_SIZE;
        

        // command is top 5 bits of b4
        command = (b4 & 0xFC) >> 3;

        // condition is bottom 3 bits of b4 and top 5 bits of b5
        condition = ((b4 & 0x07) << 5) | (b5 & 0xFC) >> 3;

        // threshold is bottom 3 bits of b5 and top 5 bits of b6
        threshold = ((b5 & 0x07) << 5) | (b6 & 0xFC) >> 3;
        
        // sequence no
        sequence = ((b7 & 0xFF) << 24) | ((b8  & 0xFF) << 16) | ((b9  & 0xFF) << 8) | (b10  & 0xFF) ;
        
        
        if (Verbose.level >= 2) {
            System.err.printf("ver: 0x%04X seq: %d chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", version, sequence, chunkCount, command, condition, threshold);
        }
    }

    /**
     * Get the sequence number.
     */
    public int getSequenceNumber() {
        return sequence;
    }

    /**
     * Get the Chunk count.
     */
    public int getChunkCount() {
        return chunkCount;
    }

    /**
     * Get the command.
     */
    public int getCommand() {
        return command;
    }

    /**
     * Get the condition.
     */
    public int getCondition() {
        return condition;
    }

    /**
     * Get the threshold
     */
    public int getThreshold() {
        return threshold;
    }

}

