// BPPSVCPacketizer.java
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
import cc.clayman.bpp.BPPPacket;
import cc.clayman.util.Verbose;

/**
 * Take SVCChunkInfo objects and converts them into a BPP packet.
 */
public class BPPSVCPacketizer implements ChunkPacketizer {
    
    final int packetSize;

    final int chunkCount;

    final int headerByteCount;

    int count = 0;

    // V1 packets
    final static int version = 1;

    /**
     * Create a BPPSVCPacketizer given a chunkCount for each packet,
     * which is equal to the no of NALs per frame,
     * The packet size is set to 1500 by default.
     */
    public BPPSVCPacketizer(int chunkCount) {
        packetSize = IP.BASIC_PACKET_SIZE;
        this.chunkCount = chunkCount;

        headerByteCount = IP.IP_HEADER + IP.UDP_HEADER + BPP.BLOCK_HEADER_SIZE
            + BPP.COMMAND_BLOCK_SIZE + (chunkCount * BPP.METADATA_BLOCK_SIZE);
    }

    /**
     * Create a BPPSVCPacketizer given a chunkCount for each packet,
     * which is equal to the no of NALs per frame,
     * and with a packet size.
     */
    public BPPSVCPacketizer(int size, int chunkCount) {
        packetSize = size;
        this.chunkCount = chunkCount;

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
    public byte[] convert(int sequence, int command, int condition, int threshold, int fnSpec, ChunkInfo svcChunk) throws UnsupportedOperationException {
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

            // Now build  BPP Header + Command Block
            BPP.BPPHeader header = new BPP.BPPHeader();

            // fill header with values
            header.version = version;
            header.chunkCount = chunkCount;
            
            bufPos = BPPPacket.writeHeader(packetBytes, header);

            // Command Block
            BPP.CommandBlock commandBlock = new BPP.CommandBlock();

            // fill commandblock with values
            // Command: is passed in
            commandBlock.command = command;
            
            // Condition: is passed in
            commandBlock.condition = condition;
            
            // Function: awaiting implementation
            commandBlock.function = fnSpec;

            // Threshold: 0 - 15 is passed in
            commandBlock.threshold = threshold;

            // Sequence
            commandBlock.sequence = sequence;
            
            
            bufPos = BPPPacket.writeCommandBlock(packetBytes, bufPos, commandBlock);

            if (Verbose.level >= 2) {
                System.err.println("Chunk data: nalNo = " + nalNo + " nalCount = " + nalCount);
            }


            if (Verbose.level >= 2) {
                System.err.printf(" %-6d ver: 0x%04X seq: %d chunkCount: %d command: 0x%05X condition: 0x%03X function 0x%05X threshold: %d\n", count, version, sequence, chunkCount, command, condition, commandBlock.function, threshold);
            }

            // Convert the ChunkInfo to a BPP.MetadataBlock
            BPP.MetadataBlock metadataBlock = chunkInfoToMetadataBlock(chunk);

            // and write into the packet
            bufPos = BPPPacket.writeMetadataBlock(packetBytes, bufPos, metadataBlock);

            
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


    private BPP.MetadataBlock chunkInfoToMetadataBlock(ChunkInfo svcChunk) {
        SVCChunkInfo chunk = (SVCChunkInfo)svcChunk;
        
        ChunkContent[] content = chunk.getChunkContent();

        //System.err.println("content.length = " + content.length);

        // Get the NAL number
        int nalNo = chunk.getNALNumber();

        // Get the NAL count
        int nalCount = chunk.getNALCount();

        // Get NAL type
        int type = chunk.getNALType().getValue();


        BPP.MetadataBlock mb = new BPP.MetadataBlock();
        
        // Allocate arrays for data in MetadataBlock.
        // Might be quicker to reuse existing arrays and clear them.
        int chunkCount = content.length;
        mb.chunkCount = chunkCount;
        mb.contentSizes = new int[chunkCount];
        mb.significance = new int[chunkCount];
        mb.fragments = new int[chunkCount];
        mb.lastFragment = new boolean[chunkCount];
        mb.isDropped = new boolean[chunkCount];
        mb.nalCount = new int[chunkCount];
        mb.nalNo = new int[chunkCount];
        mb.type = new byte[chunkCount];

        
        
        // Visit the Content
        for (int c=0; c<content.length; c++) {
                

            mb.type[c] = (byte)type;
            mb.nalCount[c] = nalCount;
            mb.nalNo[c] = nalNo;
            
            // content size
            mb.contentSizes[c] = content[c].offset();

            // get fragment from content
            mb.fragments[c] = content[c].getFragmentationNumber();
            mb.lastFragment[c] = content[c].isLastFragment();

            // significance
            mb.significance[c] = content[c].getSignificanceValue();

            // dropped
            mb.isDropped[c] = false;
        }

        
        return mb;
    }
}
