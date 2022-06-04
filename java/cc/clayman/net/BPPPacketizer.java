// BPPPacketizer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.SVCChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.h264.NALType;
import cc.clayman.net.IP;
import cc.clayman.bpp.BPP;
import cc.clayman.util.Verbose;

/**
 * Take SVCChunkInfo objects and converts them into a BPP packet.
 */
public class BPPPacketizer implements ChunkPacketizer {
    
    final int packetSize;

    final int chunkCount;

    final int headerByteCount;

    final int videoKbps;
    
    
    int count = 0;

    /**
     * Create a BPPPacketizer given a chunkCount for each packet,
     * which is equal to the no of NALs per frame,
     * and the bandwidth of the video in kbps.
     * The packet size is set to 1500 by default.
     */
    public BPPPacketizer(int chunkCount, int kbps) {
        packetSize = IP.BASIC_PACKET_SIZE;
        this.chunkCount = chunkCount;

        videoKbps = kbps;

        headerByteCount = IP.IP_HEADER + IP.UDP_HEADER + BPP.BLOCK_HEADER_SIZE
            + BPP.COMMAND_BLOCK_SIZE + (chunkCount * BPP.METADATA_BLOCK_SIZE);
    }

    /**
     * Create a BPPPacketizer given a chunkCount for each packet,
     * which is equal to the no of NALs per frame,
     * the bandwidth of the video in kbps,
     * and with a packet size.
     */
    public BPPPacketizer(int size, int chunkCount, int kbps) {
        packetSize = size;
        this.chunkCount = chunkCount;

        videoKbps = kbps;

        headerByteCount = IP.IP_HEADER + IP.UDP_HEADER + BPP.BLOCK_HEADER_SIZE
            + BPP.COMMAND_BLOCK_SIZE + (chunkCount * BPP.METADATA_BLOCK_SIZE);
    }

    /**
     * Get the payload size.
     * This is the maximum payload a packet can contain, excluding the headers.
     */
    public int getPayloadSize() {
        // Ethernet payload (1500) - IP_HEADER - UDP_HEADER - BLOCK_HEADER_SIZE
        // - COMMAND_BLOCK_SIZE - (chunkCount * METADATA_BLOCK_SIZE)
        return packetSize - IP.IP_HEADER - IP.UDP_HEADER - headerByteCount;
    }
    
    /**
     * Convert a SVCChunkInfo into byte[]
     * @throws UnsupportedOperationException if the Chunk is too big to fit in a packet
     */
    public byte[] convert(int sequence, ChunkInfo svcChunk) throws UnsupportedOperationException {
        SVCChunkInfo chunk = (SVCChunkInfo)svcChunk;
        
        count++;

        // How many bytes will the packet really need
        int totalChunkSpaceUsed = chunk.offset();

        int sizeNeeded = totalChunkSpaceUsed;

        int bufPos = 0;

        if (sizeNeeded > packetSize) {
            // there's too much content in the chunk
            throw new UnsupportedOperationException("Chunk size: " + totalChunkSpaceUsed + " too big for packet size: " + packetSize + " plus header " + headerByteCount + ". sizeNeeded = " + sizeNeeded);
        } else {
            ChunkContent[] content = chunk.getChunkContent();

            int chunkCount = content.length;

            //System.err.println("content.length = " + content.length);

            byte[] packetBytes = new byte[sizeNeeded + headerByteCount];


            // Get the NAL number
            int nalNo = chunk.getNALNumber();

            // Get the NAL count
            int nalCount = chunk.getNALCount();

            // Get NAL type
            NALType type = chunk.getNALType();

            // Now build the 32 bit BPP Header + 24 bit Command Block

            // 32 bits for BPP header
            final int version = 0x0C;
            packetBytes[0] = (byte)((version << 4) & 0xFF);
            packetBytes[1] = (byte)(0x00);
            packetBytes[2] = (byte)((chunkCount & 0x3F) << 3);
            packetBytes[3] = (byte)(0x00);

            // increase bufPos
            bufPos += BPP.BLOCK_HEADER_SIZE;


            // 24 bits for Command Block
            // build int, then pack into bytes
            int commandBlock = 0;

            // Command: 00001 = PacketWash, 00011= drop
            int command = 0x00001;
            // Condition: in kbps. So 1094 Kbps -> 109.4 * 10 K ~= 109
            // So we send Kbps / 10
            int condition = videoKbps / 10;

            // Threshold: 0 - 255
            // This is used by the network node to drop chunks
            // Might need to be passed in at run-time
            int threshold = 5;
            
            commandBlock = (command << 19) | ((byte)(condition & 0x000000FF) << 11) | ((byte)(threshold & 0x000000FF) << 3);
            
            packetBytes[4] = (byte)(((commandBlock & 0x00FF0000) >> 16) & 0xFF);
            packetBytes[5] = (byte)(((commandBlock & 0x0000FF00) >> 8) & 0xFF);
            packetBytes[6] = (byte)(((commandBlock & 0x000000FF) >> 0) & 0xFF);

            if (Verbose.level >= 2) {
                System.err.println("Chunk data: nalNo = " + nalNo + " nalCount = " + nalCount);
            }

            // increase bufPos
            bufPos += BPP.COMMAND_BLOCK_SIZE;

            if (Verbose.level >= 2) {
                System.err.printf(" %-6d ver: 0x%04X chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", count, version, chunkCount, command, condition, threshold);
            }
        

            // Visit the Content
            for (int c=0; c<content.length; c++) {
                
                int contentSize = content[c].offset();

                // get fragment from content
                int fragment = content[c].getFragmentationNumber();
                boolean isLastFragment = content[c].isLastFragment();

                // Add per-chunk Metadata Block - 48 bits / 6 bytes 
                //  -  22 bits (OFFi [5 bits (NAL Count) + 12 bits (NAL No) + 5 bits (Frag No)])
                //   + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
                //   + 6 bits (PAD)
                // NAL No is limited to 12 bits - max 4095 - so can wrap
                // Frag No is limited to 5 bits - max 31 - so can wrap
                
                int offI = 0;
                int csI = 0;
                // significance probably calculated on-the-fly, from the NAL
                int sigI = content[c].getSignificanceValue();
                
            
                offI = ((nalCount & 0x0000001F) << 17) | ((nalNo & 0x00000FFF) << 5) | ((fragment & 0x0000001F) << 0);

                //System.err.printf(" offI = %d  0x%5X \n", offI, offI);

                // chunk size - 14 bits
                csI = (contentSize & 0x00003FFF);

                // now build the next 6 bytes
                
                // need 8 bits: 14 - 21 of offI
                packetBytes[bufPos] = (byte)(((offI & 0x003FC000) >> 14) & 0xFF);
                // need 8 bits: 6 - 13 of offI
                packetBytes[bufPos+1] = (byte)(((offI & 0x00003FC0) >> 6) & 0xFF);
                // need 6 bits: 0 - 5 of offI
                packetBytes[bufPos+2] = (byte)((((offI & 0x0000003F) >> 0) << 2) & 0xFF);


                // need 2 bits: 12 - 13 of csI
                packetBytes[bufPos+2] |= (byte)(((csI & 0x00003000) >> 12) & 0x03);
                // need 8 bits: 4 - 11 of csI
                packetBytes[bufPos+3] = (byte)(((csI &  0x00000FF0) >> 4) & 0xFF);
                // need 4 bits: 0 - 3 of csI
                packetBytes[bufPos+4] = (byte)((((csI &  0x0000000F) >> 0) << 4) & 0xFF);

                // need 4 bits: 0 - 3 of sigI
                packetBytes[bufPos+4] |= (byte)(((sigI & 0x0000000F) >> 0) & 0x0F);

                // need 1 bit for OFi
                final boolean ofI = false;
                packetBytes[bufPos+5] = (byte)(((ofI ? 1 : 0)<< 7) & 0xFF);
                // need 1 bit for FFi
                packetBytes[bufPos+5] |= (byte)(((isLastFragment ? 1 : 0) << 6) & 0xFF);
                // need 1 bit for VCL/NONVCL
                packetBytes[bufPos+5] |= (byte)((type.getValue() & 0x01) << 5);

                // need 5 bits of PAD

                // increase bufPos
                bufPos += BPP.METADATA_BLOCK_SIZE;

                
                if (Verbose.level >= 2) {
                    System.err.printf("  %-3dOFFi: nalNo: %d nalCount: %d fragment: %d \n", (c+1), nalNo, nalCount, fragment);
                    System.err.printf("     CSi: contentSize: %d  SIGi:  %d\n", csI, sigI);
                    System.err.printf("     OFi: %s FFi: %s  NAL: %s\n", ofI, isLastFragment, type);
                }

            }

            // Visit the Content again, and add the Content
            for (int c=0; c<content.length; c++) {
                
                // Get the payload from the ChunkContent
                byte[] contentBytes = content[c].getPayload();
                int contentSize = content[c].offset();

                // now add the bytes to the packetBytes
                // source_arr,  sourcePos,  dest_arr,  destPos, len
                System.arraycopy(contentBytes, 0, packetBytes, bufPos, contentSize);

                bufPos += contentSize;
            }


            if (Verbose.level >= 3) {
                System.err.println("BPP: bufPos = " + bufPos);
            }
                
            return packetBytes;
        }
        
    }
}
