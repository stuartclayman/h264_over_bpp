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
*/
public abstract class AbstractBPPFn implements BPPFn {

    // Amount of bytes for running a function when the bandwidth is limited
    public final static int ENOUGH_FOR_EVALUATION = 100;
    
    // Available bandwidth (in bytes)
    int availableBandwidth = 0;
    // Available bandwidth (in bits)
    int availableBandwidthBits = 0;

    // payload
    byte[] payload = null;
    int packetLength = 0;


    // Datagram contents
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

    int idealSendThisSec = 0;   // the ideal amount to send at the current offset in the second

    // timing
    int seconds = 0;       // no of seconds
    long timeStart = 0;   // when did the second start
    long now = 0;
    long timeOffset = 0;
    float secondOffset = 0.0f;   // offset into the current second


    // Construct a BPPFn
    // bandwidthBits is integer:  838860 bits
    public AbstractBPPFn(int bandwidthBits) {
        this.availableBandwidthBits = bandwidthBits;
        // 838860 bits = 104858 bytes
        this.availableBandwidth = bandwidthBits >> 3;

        // set timeStart
        timeStart = System.currentTimeMillis();
    }
        
    // Construct a BPPFn
    // bandwidthBits is float:  0.8 Mbps
    public AbstractBPPFn(float bandwidthMegabits) {
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
        
        // ▷ Timing
        bppDoTiming();
        
        // ▷ Pre processing of packet
        int behind = bppPre(packet);
        
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


        int behind = calculateBelow();

        if (Verbose.level >= 2) {
            System.err.printf("BPPFn: " + count + " secondOffset: " + secondOffset + " countThisSec " + countThisSec +  " recvThisSec " + recvThisSec + " sentThisSec " + sentThisSec + " idealSendThisSec " + idealSendThisSec + " behind " + behind);
        }

        return behind;
    }
        
    // ▷ Check current second
    public void bppCheckTiming() {
        if (timeOffset >= 1000) {
            // we crossed a second boundary
            seconds++;
            timeStart = now;
            countThisSec = 0;
            recvThisSec = 0;
            sentThisSec = 0;
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
                // Condition is LIMITED, LIMITEDFN, or ALWAYS

                // Calculate amount to trim
                int packetTrimLevel = calculateTrimAmount(behind);
            
                if (packetTrimLevel > 0) {
                    // If we need to trim something, we need to look at the content
                    unpackDatagramContent();

                    int trimmedAmount = trimContent(packetTrimLevel);

                    int size = packetLength - trimmedAmount;

                    // check if we didn't trim enough
                    // and there is a need for more than 100 bytes
                    if (trimmedAmount < packetTrimLevel) {
                        if (packetTrimLevel - trimmedAmount > ENOUGH_FOR_EVALUATION) {
                            System.err.printf("    MORE Threshold %d TRIM_LEVEL %d TRIM: %d NEED %d \n", packetCommandBlock.threshold, packetTrimLevel, trimmedAmount, (packetTrimLevel - trimmedAmount) );

                            // Check if Condition is LIMITEDFN
                            // i.e run a Function when the bandwidth is limited
                            if (condition == BPP.Condition.LIMITEDFN) {
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

                                System.err.printf("    MORE nextTrimmedAmount %d  \n", nextTrimmedAmount);

                                if (nextTrimmedAmount > 0) {
                                    // yes some more was trimmed
                                    // so subtract that from the size
                                    size -= nextTrimmedAmount;

                                    System.err.printf("SUCCESS TRIM_LEVEL %d TRIM: %d NEED %d \n", packetTrimLevel, trimmedAmount + nextTrimmedAmount, (packetTrimLevel - trimmedAmount - nextTrimmedAmount) );
                                }

                            }
                        } else {
                            System.err.printf("    DONT TRIM %d\n", (packetTrimLevel - trimmedAmount) );
                        }
                    }
                        
                    totalOut += size;
                    sentThisSec += size;

                    // Now rebuild the packet payload, from the original packet
                    byte[] newPayload = packContent();

                    return Optional.of(newPayload);

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
                System.err.printf(" NO_TRIM\n");
            }
        } else {
            // we are ahead of ideal
            packetTrimLevel = -behind;
            if (Verbose.level >= 1) {
                System.err.printf("  YES_TRIM " + packetTrimLevel + "\n");
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

        bufPos = BPPPacket.readCommandBlock(packetBytes, bufPos, commandBlock);
        
        packetCommandBlock = commandBlock;

        //System.err.printf("%-6d ver: 0x%04X chunkCount: %d command: 0x%05X condition: %d threshold: %d\n", count, version, chunkCount, command, condition, threshold);
    }

    
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
        mb.lastFragment = new boolean[chunkCount];
        mb.isDropped = new boolean[chunkCount];
        mb.nalCount = new int[chunkCount];
        mb.nalNo = new int[chunkCount];
        mb.type = new byte[chunkCount];

        // Read the MetadataBlock
        bufPos = BPPPacket.readMetadataBlock(packetBytes, bufPos, mb);

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

    }


    /**
     * Trim some content chunks
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
     * Pack some headers and content into a new BPP byte[]
     */
    protected byte[] packContent() {

        // The new size is the incoming size - the trimmed content chunks
        int trimmedSize = 0;

        for (int c=0; c<packetMetadataBlock.chunkCount; c++) {
            if (packetMetadataBlock.isDropped[c]) {
                trimmedSize += packetMetadataBlock.contentSizes[c];
            }
        }
        
        if (Verbose.level >= 3) {
            System.err.println("BPPFn: packContent trimmed " + trimmedSize);
        }
        
        byte[] packetBytes = new byte[packetLength - trimmedSize];

        int bufPos = 0;


        // Now build  BPP Header + Command Block
        BPP.BPPHeader header = packetHeader;

        bufPos = BPPPacket.writeHeader(packetBytes, header);

        // Command Block
        BPP.CommandBlock commandBlock = packetCommandBlock;            
            
        bufPos = BPPPacket.writeCommandBlock(packetBytes, bufPos, commandBlock);

        if (Verbose.level >= 2) {
            System.err.println("Chunk data: seq: " + packetCommandBlock.sequence + " nalNo: " + packetMetadataBlock.nalNo[0] + " nalCount: " + packetMetadataBlock.nalCount[0]);
        }

        // Visit the Content
        BPP.MetadataBlock mb = packetMetadataBlock;
        
        bufPos = BPPPacket.writeMetadataBlock(packetBytes, bufPos, mb);

        
        // Now add in the content
        for (int c=0; c<packetMetadataBlock.chunkCount; c++) {
            boolean isTrimmedChunk = packetMetadataBlock.isDropped[c];
                
            if (!isTrimmedChunk) {
                // send content chunks which are not trimmed
                // now add the bytes to the packetBytes
                // source_arr,  sourcePos,  dest_arr,  destPos, len
                System.arraycopy(payload, contentStartPos[c], packetBytes, bufPos, packetMetadataBlock.contentSizes[c]);

                if (Verbose.level >= 3) {
                    //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[bufPos+0], packetBytes[bufPos+1], packetBytes[bufPos+2], packetBytes[bufPos+3]);
                    //System.err.printf(" 0x%02X 0x%02X 0x%02X 0x%02X \n",  packetBytes[bufPos+4], packetBytes[bufPos+5], packetBytes[bufPos+6], packetBytes[bufPos+7]);
                }
                
                bufPos += packetMetadataBlock.contentSizes[c];

            } else {
                if (Verbose.level >= 3) {
                    System.err.println("packContent: content " + c + " is trimmed");
                }
            }

        }

        //System.err.println("BPP: bufPos = " + bufPos);
                
        return packetBytes;
        
    }

    
}
