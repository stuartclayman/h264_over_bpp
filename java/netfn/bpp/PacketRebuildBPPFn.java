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
 * Similar to BPPBasicBandwidth but has extra rules if the data rate
 * gets close to the bandwidth limit.
 *
 * There is a &gt; 90% rules
 * and a  80% to 90% rule.
 * Only then does it check the significance of the chunks.
 */
public class PacketRebuildBPPFn extends BPPBasicBandwidth implements BPPFn {

    // Construct a BPPFn
    // bandwidthBits is integer:  838860 bits
    public PacketRebuildBPPFn(int bandwidthBits) {
        super(bandwidthBits);
    }
        
    // Construct a BPPFn
    // bandwidthBits is float:  0.8 Mbps
    public PacketRebuildBPPFn(float bandwidthMegabits) {
        super(bandwidthMegabits);
    }



    /**
     * Trim some content chunks
     */
    protected int trimContent(int packetTrimLevel) {
        if (Verbose.level >= 2) {
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
            //
            if (sentThisSec / idealSendThisSec >= 0.9) {
                // over 90% of bandwidth

                // always trim top chunks -- keep chunk 0
                if (c > 0) {
                    // it's a candidate
                    // mark it as trimmed
                    packetMetadataBlock.isDropped[c] = true;
                
                    // update the trimmed count
                    trimmed += packetMetadataBlock.contentSizes[c];

                    if (Verbose.level >= 1) {
                        System.err.println("TRIM Chunk " + c + ": percent = " + ((float)sentThisSec / (float)idealSendThisSec)  + " size: " + packetMetadataBlock.contentSizes[c]);
                    }
                
                }
                
            } else if (sentThisSec / idealSendThisSec >= 0.8) {
                // over 80% of bandwidth

                // always trim top chunk
                if (c == packetMetadataBlock.chunkCount-1) {
                    // it's a candidate
                    // mark it as trimmed
                    packetMetadataBlock.isDropped[c] = true;
                
                    // update the trimmed count
                    trimmed += packetMetadataBlock.contentSizes[c];

                    if (Verbose.level >= 1) {
                        System.err.println("TRIM Chunk " + c + ": percent = " + ((float)sentThisSec / (float)idealSendThisSec)  + " size: " + packetMetadataBlock.contentSizes[c]);
                    }
                
                }
                
            } else if (packetMetadataBlock.significance[c] > packetCommandBlock.threshold && !packetMetadataBlock.isDropped[c]) {
                // Don't try to trim chunks that have already been cut

                // it's a candidate
                // mark it as trimmed
                packetMetadataBlock.isDropped[c] = true;
                
                // update the trimmed count
                trimmed += packetMetadataBlock.contentSizes[c];

                if (Verbose.level >= 1) {
                    System.err.println("TRIM Chunk " + c + ": packetMetadataBlock.significance[" + c + "] == " + packetMetadataBlock.significance[c] + " > threshold " + packetCommandBlock.threshold  + " !packetMetadataBlock.isDropped[" + c + "] == " + !packetMetadataBlock.isDropped[c] + " size: " + packetMetadataBlock.contentSizes[c]);
                }
                
                if (Verbose.level >= 3) {
                    System.err.println("BPPFn: trimmed chunk " + c + " significance " + packetMetadataBlock.significance[c] + " size: " + packetMetadataBlock.contentSizes[c]);
                }

            } else {
                if (Verbose.level >= 1) {
                    System.err.println("NO TRIM Chunk " + c + ": packetMetadataBlock.significance[" + c + "] == " + packetMetadataBlock.significance[c] + " > threshold " + packetCommandBlock.threshold  + " !packetMetadataBlock.isDropped[" + c + "] == " + !packetMetadataBlock.isDropped[c] + " size: " + packetMetadataBlock.contentSizes[c]);
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
}
