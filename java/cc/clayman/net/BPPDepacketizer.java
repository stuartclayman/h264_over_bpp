// BPPDeacketizer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: April 2023

package cc.clayman.net;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.MultiChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.net.IP;
import cc.clayman.bpp.BPP;
import cc.clayman.util.Verbose;

/**
 * Take a DatagramPacket  and converts them into a ChunkInfo object.
 */
public class BPPDepacketizer implements ChunkDepacketizer {

    
    DatagramPacket packet;

    int count = 0;

    int chunkCount = 0;
    int command = 0;
    int condition = 0;
    int threshold = 0;
    int sequence = 0;


    int fragmentNumber = 0;
    int lastFragmentNumber = 0;
    int fragmentBaseCount = 0;
    
    public BPPDepacketizer() {
        chunkCount = 1;
    }


    /**
     * Get the payload size.
     * This is the no of payload bytes the  packet contains, excluding the headers.
     */
    public int getPayloadSize() {
        return packet.getLength() - IP.IP_HEADER - IP.UDP_HEADER - BPP.BLOCK_HEADER_SIZE - BPP.COMMAND_BLOCK_SIZE - (chunkCount * BPP.METADATA_BLOCK_SIZE);
    }
    
    /**
     * Convert a DatagramPacket into a ChunkInfo 
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public ChunkInfo convert(DatagramPacket packet) throws UnsupportedOperationException {
        this.packet = packet;
        count++;
        
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
            System.err.printf(" %-6d ver: 0x%04X seq: %d chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", count, version, sequence, chunkCount, command, condition, threshold);
        }
        

        // Visit each ChunkContent in the packet
        // and try to get the data out
        int [] contentSizes = new int[chunkCount];
        int [] fragments = new int[chunkCount];
        boolean [] lastFragment = new boolean[chunkCount];
        boolean [] isDropped = new boolean[chunkCount];
        
        for (int c=0; c<chunkCount; c++) {
        
            // Find per-chunk Metadata Block - 48 bits / 6 bytes 
            //  -  22 bits (OFFi [5 bits (Chunk Offset) + 12 bits (Source Frame No) + 5 bits (Frag No)])
            //   + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
            //   +  6 bits (PAD)
            //
            // Source Frame No is limited to 12 bits - max 4095 - so can wrap
            // Frag No is limited to 5 bits - max 31 - so can wrap
                
            int offI = 0;
            int csI = 0;
            int sigI = 0;
            int fragment = 0;
            boolean ofI = false;
            boolean ffI = false;

            // first get bytes into structural elements

            // OFFi
            // 8 bits
            offI =  ((packetBytes[bufPos] & 0xFF) << 14);
            // 8 bits
            offI |= ((packetBytes[bufPos+1] & 0xFF) << 6);
            // 6 bits
            offI |= ((packetBytes[bufPos+2] & 0xFC) >> 2);

            //System.err.printf(" offI = %d  0x%5X \n", offI, offI);
            
            // CSi
            // 2 bits
            csI = ((packetBytes[bufPos+2] & 0x3) << 12);
            // 8 bits
            csI |= ((packetBytes[bufPos+3] & 0xFF) << 4);
            // 4 bits
            csI |= ((packetBytes[bufPos+4] & 0xF0) >> 4);

            // SIGi
            sigI = (packetBytes[bufPos+4] & 0x0F);

            ofI = (packetBytes[bufPos+5] & 0x80) == 0 ? false : true;
            ffI = (packetBytes[bufPos+5] & 0x40) == 0 ? false : true;

            int type = ((packetBytes[bufPos+5] & 0x20) >> 5) ;

            bufPos += BPP.METADATA_BLOCK_SIZE;
            
            // now unpack values
            fragment = (offI & 0x0000001F);


            // check if fragment has wrapped
            // 31 = 5 bits of 1s
            // only do on first chunk
            if (c== 0 && fragment < (lastFragmentNumber % 32)) {
                fragmentBaseCount += 32;
            }
            
            // process read fragment
            fragmentNumber = fragmentBaseCount + fragment;
            lastFragmentNumber = fragmentNumber;

            
            if (Verbose.level >= 2) {
                System.err.printf("  %-3dOFFi: fragment: %d \n", (c+1), fragment);
                System.err.printf("     CSi: contentSize: %d  SIGi:  %d\n", csI, sigI);
                System.err.printf("     OFi: %s FFi: %s  \n", ofI, ffI);
            }

            // save the contentSize
            contentSizes[c] = csI;

            // fragmentation info
            fragments[c] = fragmentNumber;
            lastFragment[c] = ffI;
            isDropped[c] = ofI;
            
        }

        
        // Create a ChunkInfo
        // Pass in array of sizes
        ChunkInfo chunk = new MultiChunkInfo(contentSizes);
        chunk.setSequenceNumber(sequence);

        // bufPos now should be at first content

        for (int c=0; c<chunkCount; c++) {
        

            // Wrap the bytes of the packet
            ByteBuffer buf = ByteBuffer.wrap(packet.getData(), bufPos, contentSizes[c]);

            // skip content bytes
            bufPos +=  contentSizes[c];


            // add the payload to the chunk
            ChunkContent content = chunk.addPayload(buf);
            content.setFragmentationNumber(fragments[c]);
            content.setLastFragment(lastFragment[c]);
            content.setIsDropped(isDropped[c]);



        }


        return chunk;
        
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
