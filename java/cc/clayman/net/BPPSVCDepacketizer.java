// BPPSVCDeacketizer.java
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
import cc.clayman.bpp.BPPPacket;
import cc.clayman.util.Verbose;

/**
 * Take a DatagramPacket  and converts them into a ChunkInfo object.
 */
public class BPPSVCDepacketizer implements ChunkDepacketizer {

    
    DatagramPacket packet;

    int count = 0;
    int totalIn = 0;

    int chunkCount = 0;
    int command = 0;
    int condition = 0;
    int function = 0;
    int threshold = 0;
    int sequence = 0;


    int nalNumber = 0;
    int lastNalNo = 0;
    int nalBaseCount = 0;
    int fragmentNumber = 0;
    int lastFragmentNumber = 0;
    int fragmentBaseCount = 0;
    
    public BPPSVCDepacketizer() {
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
    public SVCChunkInfo convert(DatagramPacket packet) throws UnsupportedOperationException {
        this.packet = packet;

        count++;        
        totalIn += packet.getLength();
        
        byte[] packetBytes = packet.getData();

        
        if (Verbose.level >= 2) {
            System.err.printf("IN:   %8d%6d%10d\n", count, packet.getLength(), totalIn);
        }

        int bufPos = 0;
        
        // Now extract BPP header - 32 bits for BPP
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
        
        // sequence no
        sequence = commandBlock.sequence;
        
        
        if (Verbose.level >= 2) {
            System.err.printf(" %-6d ver: 0x%04X seq: %d chunkCount: %d command: 0x%05X condition:  0x%03X function 0x%05X threshold: %d\n", count, version, sequence, chunkCount, command, condition, function, threshold);
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
        mb.positions = new int[chunkCount];

        int nalCount = 0;
        int nalNo = 0;
        NALType nalType = null;

        // Read the MetadataBlock
        bufPos = BPPPacket.readMetadataBlock(packetBytes, packet.getLength(), bufPos, mb);

        // Process Metadata and update variables
        for (int c=0; c<chunkCount; c++) {
            nalCount = mb.nalCount[c];
            nalNo = mb.nalNo[c];

            // check if nalNo has wrapped
            // 4095 = 12 bits of 1s
            // only do on first chunk
            int lastNalNoMod = lastNalNo % 4096;
            if (c==0 && nalNo < lastNalNoMod) {
                // sometimes packets get reordered, so we need to check
                // if the values are similar
                if (lastNalNoMod > 4090 && nalNo < 5) {
                    nalBaseCount += 4096;
                }
            }

            // process read nalNo
            nalNumber = nalBaseCount + nalNo;

            // check if new nalNo
            if (c == 0 && nalNumber > lastNalNo) {
                lastNalNo = nalNumber;
                // reset fragmentBaseCount
                fragmentBaseCount = 0;
                lastFragmentNumber = 0;
            }

            // check if fragment has wrapped
            // 31 = 5 bits of 1s
            // only do on first chunk
            if (c== 0 && mb.fragments[c] < (lastFragmentNumber % 32)) {
                fragmentBaseCount += 32;
            }
            
            // process read fragment
            fragmentNumber = fragmentBaseCount + mb.fragments[c];
            lastFragmentNumber = fragmentNumber;

            if (mb.type[c] == 0 || mb.type[c] == 1)  {
                nalType = (mb.type[c] == 0 ? NALType.VCL : NALType.NONVCL);
            } else {
                throw new Error("Invalid NALType number " + mb.type[c]);
            }
        }
        
        // Create a ChunkInfo
        // Pass in array of sizes
        SVCChunkInfo chunkInfo = new SVCChunks(mb.contentSizes);
        chunkInfo.setSequenceNumber(sequence);

        // Cross check the contentSizes == packet size
        // First find what is in the metadatablock
        int headerContent = 0;
        for (int c=0; c<chunkCount; c++) {
            headerContent += mb.contentSizes[c];


            if (Verbose.level >= 2) {
                System.err.printf(" %-6d Content: chunk: %d  contentBytes: %d mb.content: %d\n", count, mb.positions[c], headerContent, mb.contentSizes[c]);
            }
        }

        // Actual content in the packet
        int packetContent = this.packet.getLength() - IP.IP_HEADER - IP.UDP_HEADER - BPP.BLOCK_HEADER_SIZE - BPP.COMMAND_BLOCK_SIZE - (chunkCount * BPP.METADATA_BLOCK_SIZE);

        if (Verbose.level >= 2) {
            System.err.printf(" %-6d Check: Header: %d  Packet: %d byte[]: %d\n", count, headerContent, packetContent, packetBytes.length);
        }
        
        // How much is left in packet
        int remaining = packetContent;
        
        // bufPos now should be at first content

        // Check if sizes in header match packet size
        if (headerContent == packetContent) {
            // everything is in order

            for (int c=0; c<chunkCount; c++) {
        

                chunkInfo.setNALType(nalType);
                chunkInfo.setNALNumber(nalNumber);
                chunkInfo.setNALCount(nalCount);

                    
                // Wrap the bytes of the packet
                ByteBuffer buf = ByteBuffer.wrap(packet.getData(), bufPos, mb.contentSizes[c]);

                remaining -= mb.contentSizes[c];

                if (Verbose.level >= 2) {
                    System.err.printf(" %-6d Content: chunk: %d  bufPos: %d mb.content: %d remaining: %d %s\n", count, mb.positions[c], bufPos, mb.contentSizes[c], remaining, "GOOD");
                }

                // skip content bytes
                bufPos +=  mb.contentSizes[c];


                // add the payload to the chunk
                // TODO: change addPayload(buf) TO addPayload(buf, position)
                ChunkContent content = chunkInfo.addPayload(buf, mb.positions[c]);
                content.setFragmentationNumber(fragmentNumber);
                content.setLastFragment(mb.lastFragment[c]);
                content.setIsDropped(mb.isDropped[c]);
                content.setPosition(mb.positions[c]);

            }
            
        } else {
            // Looks like the packet got CUT in transmission
            // header size does not match packet size
            for (int c=0; c<chunkCount; c++) {

                if (remaining == 0) {
                    // the chunks were cut during transmission

                    chunkInfo.setNALType(nalType);
                    chunkInfo.setNALNumber(nalNumber);
                    chunkInfo.setNALCount(nalCount);

                    // is the current chunk cut
                    boolean chunkCut = false;
                    
                    if (mb.contentSizes[c] == 0) {
                        // the sender actually had a chunk of 0 bytes
                    } else {
                        chunkCut = true;
                    }

                    // Empty ByteBuffer
                    ByteBuffer buf = ByteBuffer.allocate(0);

                    if (Verbose.level >= 2) {
                        System.err.printf(" %-6d Content: chunk: %d  bufPos: %d expected: %d remaining: %d %s\n", count, mb.positions[c], bufPos, mb.contentSizes[c], remaining, (chunkCut ? "CUT" : "ZERO"));
                    }
                    
                    // add the payload to the chunk
                    // TODO: change addPayload(buf) TO addPayload(buf, position)
                    ChunkContent content = chunkInfo.addPayload(buf, mb.positions[c]);
                    content.setFragmentationNumber(fragmentNumber);
                    content.setLastFragment(mb.lastFragment[c]);
                    content.setIsDropped(chunkCut);
                    content.setPosition(mb.positions[c]);

                } else {
                    // we still have the chunks

                    chunkInfo.setNALType(nalType);
                    chunkInfo.setNALNumber(nalNumber);
                    chunkInfo.setNALCount(nalCount);

                    
                    // Wrap the bytes of the packet
                    ByteBuffer buf = ByteBuffer.wrap(packet.getData(), bufPos, mb.contentSizes[c]);

                    remaining -= mb.contentSizes[c];

                    if (Verbose.level >= 2) {
                        System.err.printf(" %-6d Content: chunk: %d  bufPos: %d mb.content: %d remaining: %d %s\n", count, mb.positions[c], bufPos, mb.contentSizes[c], remaining, "OK");
                    }

                    // skip content bytes
                    bufPos +=  mb.contentSizes[c];


                    // add the payload to the chunk
                    // TODO: change addPayload(buf) TO addPayload(buf, position)
                    ChunkContent content = chunkInfo.addPayload(buf, mb.positions[c]);
                    content.setFragmentationNumber(fragmentNumber);
                    content.setLastFragment(mb.lastFragment[c]);
                    content.setIsDropped(mb.isDropped[c]);
                    content.setPosition(mb.positions[c]);
                }
            }
        
        }

        return chunkInfo;
        
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
