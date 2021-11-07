// RawDeacketizer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.chunk.MultiChunkInfo;
import cc.clayman.h264.NALType;
import cc.clayman.net.IP;

/**
 * Take a DatagramPacket  and converts them into a ChunkInfo object.
 */
public class RawDepacketizer implements ChunkDepacketizer {

    // The Raw header size
    // Contains startNAL number, no of NALs in the Chunk, NAL type
    // 4 bytes: 24 bits / 3 bytes: NAL no,
    // 1 byte:  7 bits: no of NALs, 1 bit: type (0 VCL, 1 NONVCL)

    public final static int HEADER_SIZE = RawPacketizer.HEADER_SIZE;

    // Each ChunkContent might have a sub header
    // 1 byte:  7 bits: fragment no, 1 bit: last fragment

    public final static int CHUNK_HEADER_SIZE = RawPacketizer.CHUNK_HEADER_SIZE;
    
    DatagramPacket packet;

    int count = 0;

    int sequence = 0;
    
    public RawDepacketizer() {
    }



    /**
     * Get the payload size.
     * This is the no of payload bytes the  packet contains, excluding the headers.
     */
    public int getPayloadSize() {
        return packet.getLength() - HEADER_SIZE;
    }
    
    /**
     * Convert a DatagramPacket into a ChunkInfo 
     * @throws UnsupportedOperationException if it can't work out what to do
     */
    public ChunkInfo convert(DatagramPacket packet) throws UnsupportedOperationException {
        this.packet = packet;
        count++;
        
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
        
        // Create a ChunkInfo
        ChunkInfo chunk = new MultiChunkInfo(1, getPayloadSize());
        chunk.setNALType(nalType);
        chunk.setNALNumber(nalNo);
        chunk.setNALCount(nalCount);
        chunk.setSequenceNumber(sequence);


        // Wrap the bytes of the packet
        ByteBuffer buf = ByteBuffer.wrap(packet.getData(), HEADER_SIZE, getPayloadSize());

        // add the payload to the chunk
        chunk.addPayload(buf);

        return chunk;
        
    }
        
}
