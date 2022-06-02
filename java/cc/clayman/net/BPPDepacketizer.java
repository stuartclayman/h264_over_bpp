// BPPDeacketizer.java
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
 * Take a DatagramPacket  and converts them into a ChunkInfo object.
 */
public class BPPDepacketizer implements ChunkDepacketizer {

    
    DatagramPacket packet;

    int count = 0;

    int chunkCount;

    int nalNumber = 0;
    int lastNalNo = 0;
    int nalBaseCount = 0;
    int fragmentNumber = 0;
    int fragmentBaseCount = 0;
    
    public BPPDepacketizer() {
        chunkCount = 1;
    }

    public BPPDepacketizer(int chunkCount) {
        this.chunkCount = chunkCount;
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
    public SVCChunkInfo convert(DatagramPacket packet) throws UnsupportedOperationException {
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

        bufPos += BPP.COMMAND_BLOCK_SIZE;
        

        int command = 0;
        int condition = 0;
        int threshold = 0;

        // command is top 5 bits of b4
        command = (b4 & 0xFC) >> 3;

        // condition is bottom 3 bits of b4 and top 5 bits of b5
        condition = ((b4 & 0x07) << 5) | (b5 & 0xFC) >> 3;

        // threshold is bottom 3 bits of b5 and top 5 bits of b6
        threshold = ((b5 & 0x07) << 5) | (b6 & 0xFC) >> 3;
        

        
        if (Verbose.level >= 2) {
            System.err.printf(" %-6d ver: 0x%04X chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", count, version, chunkCount, command, condition, threshold);
        }
        

        // Visit each ChunkContent in the packet
        // and try to get the data out
        int [] contentSizes = new int[chunkCount];
        int [] fragments = new int[chunkCount];
        boolean [] lastFragment = new boolean[chunkCount];
        boolean [] isDropped = new boolean[chunkCount];
        
        int nalCount = 0;
        int nalNo = 0;
        NALType nalType = null;

        for (int c=0; c<chunkCount; c++) {
        
            // Find per-chunk Metadata Block - 48 bits / 6 bytes 
            //  -  22 bits (OFFi [5 bits (NAL Count) + 12 bits (NAL No) + 5 bits (Frag No)])
            //   + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
            //   + 6 bits (PAD)
            // NAL No is limited to 12 bits - max 4095 - so can wrap
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
            nalCount = (offI >> 17) & 0x0000001F;
            nalNo = (offI >> 5) & 0x00000FFF;
            fragment = (offI & 0x0000001F);

            // check if nalNo has wrapped
            // 4095 = 12 bits of 1s
            // only do on first chunk
            if (c==0 && nalNo < (lastNalNo % 4096)) {
                nalBaseCount += 4096;
            }

            // process read nalNo
            nalNumber = nalBaseCount + nalNo;

            // check if new nalNo
            if (c == 0 && nalNumber > lastNalNo) {
                lastNalNo = nalNumber;
                // reset fragmentBaseCount
                fragmentBaseCount = 0;
            }

            // process read fragment
            fragmentNumber = fragmentBaseCount + fragment;

            // check if fragment has wrapped
            // 31 = 5 bits of 1s
            // only do on first chunk
            if (c== 0 && fragment == 0) {
                fragmentBaseCount += 32;
            }
            
            if (type == 0 || type == 1)  {
                nalType = (type == 0 ? NALType.VCL : NALType.NONVCL);
            } else {
                throw new Error("Invalid NALType number " + type);
            }
            
            
            if (Verbose.level >= 1) {
                System.err.printf("  %-3dOFFi: nalNo: %d nalCount: %d fragment: %d \n", (c+1), nalNumber, nalCount, fragment);
                System.err.printf("     CSi: contentSize: %d  SIGi:  %d\n", csI, sigI);
                System.err.printf("     OFi: %s FFi: %s  NAL: %s\n", ofI, ffI, nalType);
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
        SVCChunkInfo chunk = new SVCChunks(contentSizes);

        // bufPos now should be at first content

        for (int c=0; c<chunkCount; c++) {
        

            chunk.setNALType(nalType);
            chunk.setNALNumber(nalNumber);
            chunk.setNALCount(nalCount);

                    
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
        
}
