// BPPPacket.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: July 2024

package cc.clayman.bpp;

import cc.clayman.util.Verbose;

/*
 * A BPP Packet object
 */
public class BPPPacket {

    /**
     * Get the packet header from the packetBytes into a BPP.BPPHeader
     * @return buffer position after filling header
     */
    public final static int readHeader(byte[] packetBytes, BPP.BPPHeader header) {
        int bufPos = 0;
        
        // Now extract BPP header - 32 bits for BPP
        byte b0 = packetBytes[bufPos+0];
        byte b1 = packetBytes[bufPos+1];
        byte b2 = packetBytes[bufPos+2];
        byte b3 = packetBytes[bufPos+3];

        // increase bufPos
        bufPos += BPP.BLOCK_HEADER_SIZE;

        // Check version pattern
        header.version = (b0 & 0xF0) >> 4;
        header.chunkCount = (b2 & 0xF8) >> 3;

        return bufPos;
        
    }
    

    /**
     * Set the packet header from a BPP.BPPHeader
     * @return buffer position after filling header
     */
    public final static int writeHeader(byte [] packetBytes, BPP.BPPHeader header) {
        // set buf pos to 0 for the header
        int bufPos = 0;

        int version = header.version;
        int chunkCount = header.chunkCount;

        // 32 bits for BPP header
        packetBytes[bufPos+0] = (byte)((version << 4) & 0xFF);
        packetBytes[bufPos+1] = (byte)(0x00);
        packetBytes[bufPos+2] = (byte)((chunkCount & 0x1F) << 3);
        packetBytes[bufPos+3] = (byte)(0x00);

        bufPos += BPP.BLOCK_HEADER_SIZE;

        return bufPos;
    }

    /**
     * Get the command header from the packetBytes into a BPP.CommandBlock
     * @return buffer position after filling header
     */
    public final static int readCommandBlock(byte[] packetBytes, int bufPos, BPP.CommandBlock commandBlock) {
        // Now extract the Command Block
        byte b4 = packetBytes[bufPos+0];
        byte b5 = packetBytes[bufPos+1];
        byte b6 = packetBytes[bufPos+2];

        //System.err.printf(" 0x%02X 0x%02X 0x%02X \n",  b4, b5, b6);

        byte b7 = packetBytes[bufPos+3];
        byte b8 = packetBytes[bufPos+4];
        byte b9 = packetBytes[bufPos+5];
        byte b10 = packetBytes[bufPos+6];
                
        // increase bufPos
        bufPos += BPP.COMMAND_BLOCK_SIZE;
        

        // command is top 3 bits of b4
        commandBlock.command = (b4 & 0xE0) >> 5;

        // condition is middle 3 bits of b4 
        commandBlock.condition = (b4 & 0x1C) >> 2;

        // function is bottom 2 bits of b4, all bits of b5, and top 4 bits of b6
        commandBlock.function = ((b4 & 0x03) << 12) | ((b5 & 0xFF) << 4) | (b6 & 0xF0) >> 4;

        // threshold is bottom 4 bits of b6
        commandBlock.threshold = (b6 & 0x0F);
        
        // sequence no
        commandBlock.sequence = ((b7 & 0xFF) << 24) | ((b8  & 0xFF) << 16) | ((b9  & 0xFF) << 8) | (b10  & 0xFF) ;

        return bufPos;
    }


    /**
     * Write the command header from the packetBytes into a BPP.CommandBlock
     * @return buffer position after filling header
     */
    public final static int writeCommandBlock(byte[] packetBytes, int bufPos, BPP.CommandBlock cb) {

        // build int, then pack into bytes
        int commandBlock = 0;

        int command = cb.command;
        int condition = cb.condition;
        int function = cb.function;
        int threshold = cb.threshold;
        int sequence = cb.sequence;

        commandBlock = ((command & 0x00000007) << 21) | ((condition & 0x00000007) << 18) | ((function & 0x00003FFF) << 4) | ((threshold & 0x0000000F) << 0);

        packetBytes[bufPos+0] = (byte)(((commandBlock & 0x00FF0000) >> 16) & 0xFF);
        packetBytes[bufPos+1] = (byte)(((commandBlock & 0x0000FF00) >> 8) & 0xFF);
        packetBytes[bufPos+2] = (byte)(((commandBlock & 0x000000FF) >> 0) & 0xFF);

        //System.err.printf(" 0x%02X 0x%02X 0x%02X \n",  packetBytes[bufPos+0], packetBytes[bufPos+1], packetBytes[bufPos+2]);

        // Add Sequence no
        packetBytes[bufPos+3] = (byte)(((sequence & 0xFF000000) >> 24) & 0xFF);
        packetBytes[bufPos+4] = (byte)(((sequence & 0x00FF0000) >> 16) & 0xFF);
        packetBytes[bufPos+5] = (byte)(((sequence & 0x0000FF00) >> 8) & 0xFF);
        packetBytes[bufPos+6] = (byte)(((sequence & 0x000000FF) >> 0) & 0xFF);
            
        // increase bufPos
        bufPos += BPP.COMMAND_BLOCK_SIZE;

        return bufPos;
    }

    /**
     * Get the metadata block from the packetBytes into a BPP.MetadataBlock
     * @return buffer position after filling header
     */
    public final static int readMetadataBlock(byte[] packetBytes, int bufPos, BPP.MetadataBlock mb) {
        for (int c=0; c<mb.chunkCount; c++) {
        
            // Find per-chunk Metadata Block - 48 bits / 6 bytes 
            //  -  22 bits (OFFi [5 bits (NAL Count) + 12 bits (NAL No) + 5 bits (Frag No)])
            //   + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
            //   +  1 bit (type: VCL/NONVCL) + 5 bits (PAD)
            //
            // NAL No is limited to 12 bits - max 4095 - so can wrap
            // Frag No is limited to 5 bits - max 31 - so can wrap

            int offI = 0;
            int csI = 0;
            int sigI = 0;
            boolean ofI = false;
            boolean ffI = false;
            byte type = 0;

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

            type = (byte) (((packetBytes[bufPos+5] & 0x20) >> 5) & 0xFF);

            bufPos += BPP.METADATA_BLOCK_SIZE;
            
            // now unpack values from OFFi
            // [5 bits (NAL Count) + 12 bits (NAL No) + 5 bits (Frag No)])
            int nalCount = (offI >> 17) & 0x0000001F;
            int nalNo = (offI >> 5) & 0x00000FFF;
            int fragmentNo = (offI & 0x0000001F);

            mb.type[c] = type;
            mb.nalCount[c] = nalCount;
            mb.nalNo[c] = nalNo;
        
            //System.err.printf("  %-3dOFFi: nalNo: %d nalCount: %d fragment: %d \n", (c+1), nalNo, nalCount, fragment);
            //System.err.printf("     CSi: contentSize: %d  SIGi:  %d\n", csI, sigI);
            //System.err.printf("     OFi: %s  FFi: %s  NAL: %s\n", ofI, ffI, nalType);

            // save the contentSize
            mb.contentSizes[c] = csI;

            // fragmentation info
            mb.fragments[c] = fragmentNo;
            mb.lastFragment[c] = ffI;

            // significance
            mb.significance[c] = sigI;

            // dropped
            mb.isDropped[c] = ofI;
            
            
            if (Verbose.level >= 2) {
                System.err.printf("  %-3dOFFi: nalNo: %d nalCount: %d fragment: %d \n", (c+1), nalNo, nalCount, fragmentNo);
                System.err.printf("     CSi: contentSize: %d  SIGi:  %d\n", csI, sigI);
                System.err.printf("     OFi: %s FFi: %s  NAL: %s\n", offI, ffI, type);
            }

        }


        return bufPos;
    }

    /**
     * Write the metadata block from the packetBytes into a BPP.MetadataBlock
     * @return buffer position after filling header
     */
    public final static int writeMetadataBlock(byte[] packetBytes, int bufPos, BPP.MetadataBlock mb) {
        for (int c=0; c<mb.chunkCount; c++) {
                
            // Get the payload info
            int contentSize = mb.contentSizes[c];

            // get fragment from content
            int fragment = mb.fragments[c];
            boolean isLastFragment = mb.lastFragment[c];
            boolean isDroppedChunk = mb.isDropped[c];

            int nalCount = mb.nalCount[c];
            int nalNo = mb.nalNo[c];
            int nalType = mb.type[c];

            if (isDroppedChunk) {
                // it's dropped, so send no content
                // set contentSize to 0
                contentSize = 0;
            }

            //System.err.println("BPP: contentSize = " + contentSize);


            // Add per-chunk Metadata Block - 48 bits / 6 bytes 
            //  -  22 bits (OFFi [5 bits (Chunk Offset) + 12 bits (Source Frame No) + 5 bits (Frag No)])
            //   + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
            //   + 6 bits (PAD)
                
            int offI = 0;
            int csI = 0;
            // significance probably calculated on-the-fly, from the NAL
            int sigI = mb.significance[c];
                
            
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
            packetBytes[bufPos+5] = (byte)(((isDroppedChunk ? 1 : 0) << 7) & 0xFF);
            // need 1 bit for FFi
            packetBytes[bufPos+5] |= (byte)(((isLastFragment ? 1 : 0) << 6) & 0xFF);
            // need 1 bit for VCL/NONVCL
            packetBytes[bufPos+5] |= (byte)((nalType & 0x01) << 5);

            // need 5 bits of PAD

            // increase bufPos
            bufPos += BPP.METADATA_BLOCK_SIZE;

            if (Verbose.level >= 2) {
                System.err.printf("  %-3dOFFi: nalNo: %d nalCount: %d fragment: %d \n", (c+1), nalNo, nalCount, fragment);
                System.err.printf("     CSi: contentSize: %d  SIGi:  %d\n", csI, sigI);
                System.err.printf("     OFi: %s FFi: %s  NAL: %s\n", offI, isLastFragment, nalType);
            }

        }

        return bufPos;
    }

}
