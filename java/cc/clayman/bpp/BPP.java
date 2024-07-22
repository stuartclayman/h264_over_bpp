// BPP.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.bpp;

/*
 * A BPP Packet layout
 *
 * Overview:
 * BPP Block Header:  4 bytes (32 bits)
 *  - 32 bits (mix of fields)
 *
 * Command Block:     7 bytes (56 bits)
 *  - 3 bits (Command) + 3 bits (Condition) + 14 bits (Function) + 4 bits (Threshold) 
 *    Function is 1 bit to signify run function + fn bits + args
 *  - 32 bits (Sequence No)
 *
 * Metadata Block:    6 bytes (48 bits) times no of chunks
 *  - 22 bits (OFFi [5 bits (Chunk Offset) + 12 bits (Source Frame No) + 5 bits (Frag No)])
 *    + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
 *    + 1 bit (1 = VCL / 0 = NONVCL)
 *    + 5 bits (PAD)
 
    +------------------+--------------------+--------------------+-----------------+
    | BPP Block Header (32 bits)            | ChunkCount(5)| P(3)|                 *
    +------------------+--------------------+--------------------+-----------------+
    | Command (3) | Condition (3) | Funct (14)   | Threshold (4) * SeqNo (8)       *
    +------------------+--------------------+--------------------+-----------------+
    | SeqNo (24)                                                 *   OFFi (8)      |
    +------------------+--------------------+--------------------+-----------------+
    | OFFi (14)                       | CSi (14)                        | SIGi (4) |
    +------------------+--------------------+--------------------+-----------------+
    | O F V | P(5) *  <NEXT>                                                   |
    +------------------+--------------------+--------------------+-----------------+
      ^ ^ ^
      |  \  ----------\
  OFi (1)  FFi (1)    VCL(1)


// Chunk count is written in the block header. The size of the field is 5 bits. 
// CSi is Chunk Sizei
// Checksum is in BPP Block Header
*/
public class BPP {

    public static final int BLOCK_HEADER_SIZE = 4;

    public static final int COMMAND_BLOCK_SIZE = 7;

    public static final int METADATA_BLOCK_SIZE = 6;

    /**
     * A structure for the BPPHeader, so we can group the values.
     */
    public static final class BPPHeader {
        public int version;                             // Version of structure
        public int chunkCount;                          // No of chunks in the packet
    }
    

    /**
     * A structure for the CommandBlock, so we can group the values.
     */
    public static final class CommandBlock {
        public int command = 0;                         // 3 bits:  0 -> 7
        public int condition = 0;                       // 3 bits:  0 -> 7
        public int function = 0;                        // 14 bits:
        public int threshold =0 ;                       // 4 bits:  0 -> 15
        public int sequence = 0;
    }

    /**
     * A structure for the MetadataBlock, so we can group the values.
     */
    public static final class MetadataBlock {
        // Per-chunk Metadata Block - 48 bits / 6 bytes 
        //  -  22 bits (OFFi [5 bits (NAL Count) + 12 bits (NAL No) + 5 bits (Frag No)])
        //   + 14 bits (CSi) + 4 bits (SIGi) + 1 bit (OFi) + 1 bit (FFi)
        //   +  1 bit (type: VCL/NONVCL) + 5 bits (PAD)
        //
        // NAL No is limited to 12 bits - max 4095 - so can wrap
        // Frag No is limited to 5 bits - max 31 - so can wrap
        public int chunkCount = 0;

        public int [] nalCount = null;
        public int [] nalNo = null;
        public int [] fragments = null;

        public int [] contentSizes = null;
        public int [] significance = null;
        public boolean [] lastFragment = null;
        public boolean [] isDropped = null;
        public byte [] type = null;
    }

    /**
     * An inner class, to hold the Command (3 bits)
     */
    public static final class Command {
        public static final int NONE = 0x000;           // 000 Do nothing to the packet
        public static final int DROP = 0x001;           // 001 Drop the packet
        public static final int WASH = 0x002;           // 010 Wash some chunks
        public static final int FLUSH = 0x003;          // 011 Wash all the chunks
    }

    /**
     * An inner class, to hold the Condition (3 bits)
     * When to run the Command at the network node
     */
    public static final class Condition {
        public static final int LIMITED   = 0x000;      // 000 If bandwidth is limited
        public static final int ALWAYS    = 0x001;      // 001 Always
        public static final int NEVER     = 0x002;      // 010 Never
        public static final int LIMITEDFN  = 0x007;     // 111 If bandwidth is limited
                                                        // and nothing washed, then
                                                        // run fn on the threshold value
    }

    /**
     * An inner class, to hold the Function bits.
     */
    public static final class Function {
        public static final int NONE   = 0x000;                 // 000 None 
        public static final int RELAX_THRESHOLD   = 0x001;      // 001 If we should relax the threshold
    }
    
}
