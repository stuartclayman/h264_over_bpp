package netfn.bpp;

import java.net.DatagramPacket;
import cc.clayman.net.IP;
import cc.clayman.bpp.BPP;
import cc.clayman.bpp.BPPPacket;
import cc.clayman.bpp.BPPFunction;
import cc.clayman.h264.NALType;
import cc.clayman.util.ANSI;
import cc.clayman.util.Verbose;
import java.util.Optional;

/**
 * A NetFn that does some BPP processing 
 *
 * There are 4 main phases:
 * (i) getting Timing information when a packet arrives;
 * (ii) doing pre-processing of packet to get the packet size and determining
 * the ideal number of bytes to send at the particular offset into a second;
 * (iii) to Check the current second to see if a second
 * boundary has been crossed; and
 * (iv) the Decision making and forwarding which processes each packet.
 *
 * The trimming is done in a faster way by just cutting the payload
 * at a calculated point.
 * This class does not fully unpack and rebuild a packet, but
 * uses a simpler, and faster approach.
 * The client has to double check all the meta-data before using the
 * received packet.
 *
 * This cuts packets instead of splitting and rebuilding.
 *
 * Described in "Enhancing Packet Trimming Behaviour for Programmable Protocols"
 * 12th IEEE International Conference on Network Softwarization (NetSoft),
 * Berlin, July 2026
 */
public class BPPCutFn implements BPPFn {

    // Available bandwidth (in bytes)
    int availableBandwidth = 0;
    // Available bandwidth (in bits)
    int availableBandwidthBits = 0;

    // payload
    byte[] payload = null;
    int packetLength = 0;


    // Datagram header contents
    BPP.BPPHeader packetHeader = null;
    BPP.CommandBlock packetCommandBlock = null;
    BPP.MetadataBlock packetMetadataBlock = null;
    
    int [] contentStartPos = null;
    NALType nalType = null;

    // counts
    int count = 0;
    int chunkCount = 0;
    int totalIn = 0;
    int totalOut = 0;
    int countThisSec = 0;  // packet count this second
    int recvThisSec = 0;   // amount recvd this second
    int sentThisSec = 0;   // amount sent this second
    int trimmedThisSec = 0;   // amount trimmed this second

    int idealSendThisSec = 0;   // the ideal amount to send at the current offset in the second
    int behind = 0;         // how are behind the ideal are we

    // timing
    int seconds = 0;       // no of seconds
    long timeStart = 0;   // when did the second start
    long now = 0;
    long timeOffset = 0;
    float secondOffset = 0.0f;   // offset into the current second

    // Construct a BPPFn
    // bandwidthBits is integer:  838860 bits
    public BPPCutFn(int bandwidthBits) {
        this.availableBandwidthBits = bandwidthBits;
        // 838860 bits = 104858 bytes
        this.availableBandwidth = bandwidthBits >> 3;

        // set timeStart
        timeStart = System.currentTimeMillis();
    }
        
    // Construct a BPPFn
    // bandwidthBits is float:  0.8 Mbps
    public BPPCutFn(float bandwidthMegabits) {
        this.availableBandwidthBits = convertBandwidth(bandwidthMegabits);
        this.availableBandwidth = this.availableBandwidthBits >> 3;
    }

    /**
     * Process the nth packet.
     *
     * Steps are:
     * ▷ Timing
     * ▷ Pre processing of packet
     * ▷ Check current second
     * ▷ Decision making and forwarding
     * @return a byte[] which is the content to forward on
     */
    public Optional<byte[]> process(int count, DatagramPacket packet) throws UnsupportedOperationException {
        this.count = count;

        // reset timer on first packet
        if (count == 1) {
            timeStart = System.currentTimeMillis();
        }
        
        // ▷ Timing
        bppDoTiming();
        
        // ▷ Pre processing of packet
        bppPre(packet);
        
        if ((Message.status & Message.BPPFN_PROCESS) == Message.BPPFN_PROCESS) {
            System.err.println("BPPFn: " + count + " secondOffset: " + secondOffset + " countThisSec " + countThisSec +  " recvThisSec " + recvThisSec + " trimmedThisSec " + trimmedThisSec + " sentThisSec " + sentThisSec + " idealSendThisSec " + idealSendThisSec + " behind " + behind + " percent " + (100 * sentThisSec / idealSendThisSec));
        }

        // ▷ Check current second
        bppCheckTiming();
        
        // ▷ Decision making and forwarding
        Optional<byte[]> result = bppConditionAction(behind, packet);

        return result;
    }

    // ▷ Timing
    public void bppDoTiming() {
        countThisSec++;
        
        // timing
        now = System.currentTimeMillis();
        // Millisecond offset between now and timeStart 
        timeOffset = now - timeStart;
        // What is the offset in this second
        secondOffset = (float)timeOffset / 1000;
    }
        
        
    // ▷ Pre processing of packet
    // @return how far below the ideal bandwidth is this packet
    public int bppPre(DatagramPacket packet) {
        // check bandwidth is enough
        payload = packet.getData();
        packetLength = packet.getLength(); 

        totalIn += packetLength;
        recvThisSec += packetLength;


        behind = calculateBelow();

        return behind;
    }
        
    // ▷ Check current second
    public void bppCheckTiming() {
        if (timeOffset >= 1000) {
            // we crossed a second boundary

            // Once per second msg
            if ((Message.status & Message.BPPFN_SECOND) == Message.BPPFN_SECOND) {
                System.err.println("BPPFn: " + count + " secondOffset: " + secondOffset + " countThisSec " + countThisSec +  " recvThisSec " + recvThisSec + " trimmedThisSec " + trimmedThisSec + " sentThisSec " + sentThisSec + " idealSendThisSec " + idealSendThisSec + " behind " + behind + " percent " + (100 * sentThisSec / idealSendThisSec));
            }

            seconds++;
            timeStart = now;
            countThisSec = 0;
            recvThisSec = 0;
            sentThisSec = 0;
            trimmedThisSec = 0;
            secondOffset = 0;
        }

    }
        
    // ▷ Decision making and forwarding
    public Optional<byte[]> bppConditionAction(int behind, DatagramPacket packet) {
        // Check Packet
        
        // Look into the packet headers to get command and condition
        unpackDatagramHeaders();

        int command = packetCommandBlock.command;
        int condition = packetCommandBlock.condition;

        // and check the Command
        if (command == BPP.Command.WASH) {
            // it's a WASH command

            if (condition == BPP.Condition.NEVER) {
                // Never do washing
                return Optional.empty();

            } else {
                // Condition is LIMITED, LTDFUNC, or ALWAYS

                // Calculate amount to trim
                int packetTrimLevel = calculateTrimAmount(behind);
            
                if (packetTrimLevel > 0) {
                    // Call the Trim Fn
                    Optional<byte[]> newPayload = actionTrim(behind, packet, packetTrimLevel);
                    totalOut += newPayload.get().length;
                    sentThisSec += newPayload.get().length;

                    return newPayload;

                } else {
                    // Get the network function to forward the packet
                    int size = packetLength;
                    totalOut += size;
                    sentThisSec += size;
                    
                    return Optional.empty();
                }

            }

        } else if (command == BPP.Command.NONE) {
            // do no processing
            // and forward it
            return Optional.empty();

        } else if (command == BPP.Command.DROP) {
            // drop the packet
            // null tells the caller to drop
            return null;

        } else {
            // We got a bad Command
            System.err.printf("BPPFn: Unknown BPP Command: %s\n" + command);

            // Just forward the packet
            return Optional.empty();
        }
        
    }

    // Trim some content from the packet.
    // Attempt to remove packetTrimLevel bytes.
    public Optional<byte[]> actionTrim(int behind, DatagramPacket packet, int packetTrimLevel) {
        int command = packetCommandBlock.command;
        int condition = packetCommandBlock.condition;

        // If we need to trim something, we need to look at the content
        unpackDatagramContent();

        int trimmedAmount = trimContent(packetTrimLevel);

        int size = packetLength - trimmedAmount;

        // check if we didn't trim enough
        // and the condition is set
        // and there is a need for more than 100 bytes
        if (trimmedAmount < packetTrimLevel && condition == BPP.Condition.LTDFUNC) {
            if (packetTrimLevel - trimmedAmount > BPPFn.ENOUGH_FOR_EVALUATION) {
                if (Verbose.level >= 1) {
                    System.err.printf("    MORE Threshold %d TRIM_LEVEL %d TRIM: %d NEED %d \n", packetCommandBlock.threshold, packetTrimLevel, trimmedAmount, (packetTrimLevel - trimmedAmount) );
                }

                // Check if Condition is LTDFUNC
                // i.e run a Function when the bandwidth is limited
                if (condition == BPP.Condition.LTDFUNC) {
                    // we didn't trim enough
                    BPPFunction fn = BPPFunction.convert(packetCommandBlock.function);
                    //System.err.println("Run " + fn + " arg " + fn.getArg());

                    // arrange to relax the threshold
                    int oldTrimmedSize = size;

                    packetCommandBlock.threshold -= fn.getArg();
                        
                    //System.err.printf("    MORE Threshold %d  \n", packetCommandBlock.threshold);
                            
                    // try and trim some more
                    // subtract amount already trimmed
                    int newTrimLevel = packetTrimLevel - trimmedAmount;
                    int nextTrimmedAmount = trimContent(newTrimLevel);

                    if (Verbose.level >= 1) {
                        System.err.printf("    MORE nextTrimmedAmount %d  \n", nextTrimmedAmount);
                    }

                    if (nextTrimmedAmount > 0) {
                        // yes some more was trimmed
                        // so subtract that from the size
                        size -= nextTrimmedAmount;

                        if (Verbose.level >= 2) {
                            System.err.printf("SUCCESS TRIM_LEVEL %d TRIM: %d NEED %d \n", packetTrimLevel, trimmedAmount + nextTrimmedAmount, (packetTrimLevel - trimmedAmount - nextTrimmedAmount) );
                        }
                    }

                }
            } else {
                if (Verbose.level >= 2) {
                    System.err.printf("    DONT TRIM %d\n", (packetTrimLevel - trimmedAmount) );
                }
            }
        }
                        
        // Total to trim
        int trimmedTotal = packetLength - size;

        if (Verbose.level >= 1) {
            System.err.printf("   TRIM_ACTION %d\n", trimmedTotal);
        }
          
        trimmedThisSec += trimmedTotal;

        //System.err.printf("packetLength = %d size = %d trimmedTotal = %d\n", packetLength, size, trimmedTotal);

        // Now create the packet payload, from the original packet
        // by cutting at position size
        byte[] newPayload = cutContent(size);

        return Optional.of(newPayload);
    }

    // Calculate how far below the ideal we are
    // A negative value means we are over the ideal
    public int calculateBelow() {
        // The ideal no of bytes to send at this offset into a second 
        idealSendThisSec = (int) (availableBandwidth * secondOffset);

        // always allow a full packet
        if (idealSendThisSec < IP.BASIC_PACKET_SIZE) {
            idealSendThisSec = IP.BASIC_PACKET_SIZE;
        }
        
        // How far behind the ideal are we
        int behind = idealSendThisSec - sentThisSec;

        //if (Verbose.level >= 1) {
        //    System.err.printf("  BEHIND " + behind + "\n");
        //}


        return behind;
    }
    
    // Calculate how much should be trimmed from the packet,
    // given how far behind or ahead we are
    public int calculateTrimAmount(int behind) {

        // work out packetTrimLevel
        int packetTrimLevel = 0;

        if (behind > 0) {
            // fine - we are behind the ideal send amount
            packetTrimLevel = 0;

            if (Verbose.level >= 2) {
                System.err.printf(" NO_CUT\n");
            }
        } else {
            // we are ahead of ideal
            packetTrimLevel = -behind;

            if (Verbose.level >= 2) {
                System.err.printf("  YES_CUT_NEEDED " + packetTrimLevel + "\n");
            }
        }

        return packetTrimLevel;
    }
    
    // Get the bandwidth in bits
    public int getBandwidth() {
        return availableBandwidthBits;

    }    

    // Adjust the bandwidth (in bits per second)
    public void setBandwidth(int bitsPerSecond) {
        this.availableBandwidthBits = bitsPerSecond;
        this.availableBandwidth = bitsPerSecond >> 3;
    }    

                
    // convert float 0.8 Mbps -> 838860 bits
    protected int convertBandwidth(float bb) {
        return (int)(bb * 1024 * 1024);
    }

    /*********** PACKET PROCESSING ******************/

    /**
     * Unpack  the DatagramPacket into objects
     */
    protected void unpackDatagramHeaders() {
        
        byte[] packetBytes = payload;

        /* Unpack the Datagram */
        int bufPos = 0;
        
        // Now extract BPP header - 32 bits for BPP
        BPP.BPPHeader header = new BPP.BPPHeader();

        bufPos = BPPPacket.readHeader(packetBytes, header);
        
        packetHeader = header;
        

        //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[0], packetBytes[1], packetBytes[2], packetBytes[3]);

        // Now extract the Command Block
        BPP.CommandBlock commandBlock = new BPP.CommandBlock();


        // Use the local readCommandBlock() which only grabs some fields

        bufPos = readCommandBlock(packetBytes, bufPos, commandBlock);
        
        packetCommandBlock = commandBlock;


        if (Verbose.level >= 2) {
            System.err.printf(" %-6d ver: 0x%04X seq: %d chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", count, header.version, commandBlock.sequence, header.chunkCount, packetCommandBlock.command, packetCommandBlock.condition, packetCommandBlock.threshold);
        }        
    }

    // Unpack the MetadataBlock but not the chunks    
    protected void unpackDatagramContent() {
        
        byte[] packetBytes = payload;

        int bufPos = 0;
        
        // skip the Block Header
        bufPos += BPP.BLOCK_HEADER_SIZE;
        // skip the Command Block
        bufPos += BPP.COMMAND_BLOCK_SIZE;


        // Visit each ChunkContent in the packet
        // and try to get the data out
        BPP.MetadataBlock mb = new BPP.MetadataBlock();
        
        // Allocate arrays for data in MetadataBlock.
        // Might be quicker to reuse existing arrays and clear them.
        int chunkCount = packetHeader.chunkCount;
        mb.chunkCount = chunkCount;
        mb.contentSizes = new int[chunkCount];
        mb.significance = new int[chunkCount];
        mb.fragments = new int[chunkCount];
        //mb.lastFragment = new boolean[chunkCount];
        mb.isDropped = new boolean[chunkCount];
        mb.nalCount = new int[chunkCount];
        mb.nalNo = new int[chunkCount];
        mb.type = new byte[chunkCount];
        mb.positions = new int[chunkCount];

        // Read the MetadataBlock
        // Use the local readMetadataBlock() which only grabs some fields
        bufPos = readMetadataBlock(packetBytes, bufPos, mb);

        packetMetadataBlock = mb;
        
        // check type, nalCount, and nalNo
        byte type = mb.type[chunkCount-1];
        if (type == 0 || type == 1)  {
            nalType = (type == 0 ? NALType.VCL : NALType.NONVCL);
        } else {
            throw new Error("Invalid NALType number " + type);
        }

        int nalCount = mb.nalCount[chunkCount-1];
        int nalNo = mb.nalNo[chunkCount-1];


        /*
         * No need to look at the actual content chunks
         
        // Save start point of each content
        // bufPos now should be at first content
        contentStartPos = new int[chunkCount];

        for (int c=0; c<chunkCount; c++) {
            contentStartPos[c] = bufPos;

            if (Verbose.level >= 2) {
                //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[bufPos+0], packetBytes[bufPos+1], packetBytes[bufPos+2], packetBytes[bufPos+3]);
                //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[bufPos+4], packetBytes[bufPos+5], packetBytes[bufPos+6], packetBytes[bufPos+7]);
            }
            
            bufPos += packetMetadataBlock.contentSizes[c];
        }        
        */
    }


    /**
     * Work out some content chunks to trim
     */
    protected int trimContent(int packetTrimLevel) {
        if (Verbose.level >= 3) {
            System.err.println("BPPFn: " + count + " Trim needed of " + packetTrimLevel);
        }
            
        int trimmed = 0;
            
        // now we try to trim some chunk content
        // try from the highest to the lowest
        // TODO: go from least significance to highest significance
        // instead of chunk position
        for (int c=packetMetadataBlock.chunkCount-1; c>=0; c--) {
            if (Verbose.level >= 3) {
                System.err.println("isDropped[" + c + "] = " + packetMetadataBlock.isDropped[c]);
            }

            // can we delete this content
            
            if (packetMetadataBlock.significance[c] > packetCommandBlock.threshold && !packetMetadataBlock.isDropped[c]) {
                // it's a candidate
                // mark it as trimmed
                packetMetadataBlock.isDropped[c] = true;
                
                // update the trimmed count
                trimmed += packetMetadataBlock.contentSizes[c];

                if (Verbose.level >= 3) {
                    System.err.println("BPPFn: trimmed chunk " + c + " significance " + packetMetadataBlock.significance[c] + " size: " + packetMetadataBlock.contentSizes[c]);
                }
            }

            if (trimmed >= packetTrimLevel) {
                // we've achieved the target
                // so no need to do any more
                break;
            }
        }

        if (Verbose.level >= 3) {
            System.err.println("BPPFn: trimContent trimmed " + trimmed);
        }

        return trimmed;
    }


    /**
     * Cut the packet at position size
     */
    protected byte[] cutContent(int size) {

        if (Verbose.level >= 3) {
            System.err.println("BPPFn: cutContent keep: " + size + " trim: " + (packetLength - size));
        }
        
        byte[] packetBytes = new byte[size];

        // Copy 'size' bytes from payload to new packetBytes
        System.arraycopy(payload, 0, packetBytes, 0, size);
                
        return packetBytes;
        
    }

    /**
     * Get the command header from the packetBytes into a BPP.CommandBlock
     * @return buffer position after filling header
     */
    public final static int readCommandBlock(byte[] packetBytes, final int inBufPos, BPP.CommandBlock commandBlock) {
        // This is copied from cc.clayman.bpp.BPPPacket
        // but is cut down to speed it up

        int bufPos = inBufPos;
        
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
        // NOT NEEDED: commandBlock.function = ((b4 & 0x03) << 12) | ((b5 & 0xFF) << 4) | (b6 & 0xF0) >> 4;

        // threshold is bottom 4 bits of b6
        commandBlock.threshold = (b6 & 0x0F);
        
        // sequence no
        commandBlock.sequence = ((b7 & 0xFF) << 24) | ((b8  & 0xFF) << 16) | ((b9  & 0xFF) << 8) | (b10  & 0xFF) ;

        return bufPos;
    }

    /**
     * Get the metadata block from the packetBytes into a BPP.MetadataBlock
     * @return buffer position after filling header
     */
    public final static int readMetadataBlock(byte[] packetBytes, final int inBufPos, BPP.MetadataBlock mb) {

        int bufPos = inBufPos;
        
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
            int chunkPos = 0;

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

            // SIGi - 4 bits
            sigI = (packetBytes[bufPos+4] & 0x0F);

            // OFi - 1 bit
            // NOT NEEDED ofI = (packetBytes[bufPos+5] & 0x80) == 0 ? false : true;
            // FFi - 1 bit
            // NOT NEEDED ffI = (packetBytes[bufPos+5] & 0x40) == 0 ? false : true;
            // type - 1 bit
            type = (byte) (((packetBytes[bufPos+5] & 0x20) >> 5) & 0xFF);
            // chunk position
            chunkPos = ((packetBytes[bufPos+5] & 0x3F) & 0xFF);

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
            // NOT NEEDED mb.lastFragment[c] = ffI;

            // significance
            mb.significance[c] = sigI;

            // position
            mb.positions[c] = chunkPos;
            
            // dropped
            mb.isDropped[c] = ofI;
            
            
            if (Verbose.level >= 2) {
                System.err.printf("  %-3dOFFi: nalNo: %d nalCount: %d fragment: %d \n", (c+1), nalNo, nalCount, fragmentNo);
                System.err.printf("     CSi: contentSize: %d  SIGi:  %d  Pos:  %d\n", csI, sigI, chunkPos);
                System.err.printf("     OFi: %s FFi: %s  Type: %s\n", offI, ffI, type);
            }

        }


        return bufPos;
    }
    
}
