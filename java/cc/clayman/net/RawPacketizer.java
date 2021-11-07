// RawPacketizer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.h264.NALType;
import cc.clayman.net.IP;

/**
 * Take ChunkInfo objects and converts them into a Raw packet.
 */
public class RawPacketizer implements ChunkPacketizer {

    // The Raw header size
    // Contains startNAL number, no of NALs in the Chunk, NAL type
    // 4 bytes: 32 bits:  sequence no
    // 4 bytes: 24 bits / 3 bytes: NAL no,
    // 1 byte:  7 bits: no of NALs, 1 bit: type (0 VCL, 1 NONVCL)

    public final static int HEADER_SIZE = 8;

    // Each ChunkContent might have a sub header
    // 1 byte:  7 bits: fragment no, 1 bit: last fragment

    public final static int CHUNK_HEADER_SIZE = 1;
    
    final int packetSize;

    final int headerByteCount;

    
    public RawPacketizer() {
        packetSize = IP.BASIC_PACKET_SIZE;
        headerByteCount = HEADER_SIZE;
    }

    /**
     * A RawPacketizer with a packet size
     */
    public RawPacketizer(int size) {
        packetSize = size;
        headerByteCount = HEADER_SIZE;
    }

    /**
     * Get the payload size.
     * This is the maximum payload a packet can contain, excluding the headers.
     */
    public int getPayloadSize() {
        // Ethernet payload (1500) - IP_HEADER - UDP_HEADER
        // - HEADER_SIZE (4 byte header)
        return IP.BASIC_PACKET_SIZE - IP.IP_HEADER - IP.UDP_HEADER - headerByteCount;
    }
    
    /**
     * Convert a ChunkInfo into byte[]
     * @throws UnsupportedOperationException if the Chunk is too big to fit in a packet
     */
    public byte[] convert(int sequence, ChunkInfo chunk) throws UnsupportedOperationException {

        // How many bytes will the packet really need
        int totalChunkSpaceUsed = chunk.offset();

        int sizeNeeded = totalChunkSpaceUsed; 

        System.err.println("Raw: totalChunkSpaceUsed = " + totalChunkSpaceUsed);

        int bufPos = 0;

        if (sizeNeeded > packetSize) {
            // there's too much content in the chunk
            throw new UnsupportedOperationException("Chunk size: " + totalChunkSpaceUsed + " too big for packet size: " + packetSize + " plus header");
        } else {
            byte[] packetBytes = new byte[sizeNeeded + headerByteCount];


            // Get the NAL number
            int nalNo = chunk.getNALNumber();
            
            // Get the NAL count
            int nalCount = chunk.getNALCount();

            // Get NAL type
            NALType type = chunk.getNALType();

            // Now build the header
            
            // 32 bits for sequence no
            packetBytes[0] = (byte)(((sequence & 0xFF000000) >> 24) & 0xFF);
            packetBytes[1] = (byte)(((sequence & 0x00FF0000) >> 16) & 0xFF);
            packetBytes[2] = (byte)(((sequence & 0x0000FF00) >> 8) & 0xFF);
            packetBytes[3] = (byte)(((sequence & 0x000000FF) >> 0) & 0xFF);

            // 24 bits for nalNo
            packetBytes[4] = (byte)(((nalNo & 0x00FF0000) >> 16) & 0xFF);
            packetBytes[5] = (byte)(((nalNo & 0x0000FF00) >> 8) & 0xFF);
            packetBytes[6] = (byte)(((nalNo & 0x000000FF) >> 0) & 0xFF);

            // 7 bits for count + 1 bit for type
            packetBytes[7] = (byte) (((nalCount & 0xFF) << 1) | (type.getValue() & 0x01));

            //System.err.printf(" %d 0x%02X 0x%02X 0x%02X 0x%02X \n", nalNo, packetBytes[0], packetBytes[1], packetBytes[2], packetBytes[3]);


            // increase bufPos
            bufPos += HEADER_SIZE;

            ChunkContent[] content = chunk.getChunkContent();

            for (int c=0; c<content.length; c++) {
                
                // 7 bits for fragment no + 1 bit for last fragment
                //packetBytes[4] = (byte) (((content[c].getFragmentationNumber() & 0xFF) << 1) | (content[c].isLastFragment() ? 0x01 : 0x0));



                // Get the payload from the ChunkContent
                byte[] contentBytes = content[c].getPayload();

                int contentSize = content[c].offset();

                System.err.println("Raw: content[c].offset() = " + contentSize);
                

                // now add the bytes to the packetBytes
                // source_arr,  sourcePos,  dest_arr,  destPos, len
                System.arraycopy(contentBytes, 0, packetBytes, bufPos, contentSize);

                bufPos += contentSize;

            }

            System.err.println("Raw: bufPos = " + bufPos);
                
            return packetBytes;
        }
        
    }
}
