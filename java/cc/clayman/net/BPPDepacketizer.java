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
import cc.clayman.bpp.BPPPacket;
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
    int function = 0;
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

        BPP.BPPHeader header = new BPP.BPPHeader();

        bufPos = BPPPacket.readHeader(packetBytes, header);
        
        int version = header.version;
        chunkCount =  header.chunkCount;

        // Now extract the Command Block
        BPP.CommandBlock commandBlock = new BPP.CommandBlock();

        bufPos = BPPPacket.readCommandBlock(packetBytes, bufPos, commandBlock);
        
        // command 
        command = commandBlock.command;

        // condition 
        condition = commandBlock.condition;

        // function 
        function = commandBlock.function;

        // threshold 
        threshold = commandBlock.threshold;
                        
        
        if (Verbose.level >= 2) {
            System.err.printf(" %-6d ver: 0x%04X seq: %d chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", count, version, sequence, chunkCount, command, condition, threshold);
        }
        
        // Visit each ChunkContent in the packet
        // and try to get the data out
        BPP.MetadataBlock mb = new BPP.MetadataBlock();
        
        // Allocate arrays for data in MetadataBlock.
        // Might be quicker to reuse existing arrays and clear them.
        mb.chunkCount = chunkCount;
        mb.contentSizes = new int[chunkCount];
        mb.significance = new int[chunkCount];
        mb.fragments = new int[chunkCount];
        mb.lastFragment = new boolean[chunkCount];
        mb.isDropped = new boolean[chunkCount];
        mb.nalCount = new int[chunkCount];
        mb.nalNo = new int[chunkCount];
        mb.type = new byte[chunkCount];

        // Read the MetadataBlock
        bufPos = BPPPacket.readMetadataBlock(packetBytes, bufPos, mb);

        // Process Metadata and update variables
        for (int c=0; c<chunkCount; c++) {
            // check if fragment has wrapped
            // 31 = 5 bits of 1s
            // only do on first chunk
            if (c== 0 && mb.fragments[c] < (lastFragmentNumber % 32)) {
                fragmentBaseCount += 32;
            }
            
            // process read fragment
            fragmentNumber = fragmentBaseCount + mb.fragments[c];
            lastFragmentNumber = fragmentNumber;

        }

        // Create a ChunkInfo
        // Pass in array of sizes
        ChunkInfo chunk = new MultiChunkInfo(mb.contentSizes);
        chunk.setSequenceNumber(sequence);

        // bufPos now should be at first content

        for (int c=0; c<chunkCount; c++) {
        

            // Wrap the bytes of the packet
            ByteBuffer buf = ByteBuffer.wrap(packet.getData(), bufPos, mb.contentSizes[c]);

            // skip content bytes
            bufPos +=  mb.contentSizes[c];


            // add the payload to the chunk
            ChunkContent content = chunk.addPayload(buf);
            content.setFragmentationNumber(mb.fragments[c]);
            content.setLastFragment(mb.lastFragment[c]);
            content.setIsDropped(mb.isDropped[c]);



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
