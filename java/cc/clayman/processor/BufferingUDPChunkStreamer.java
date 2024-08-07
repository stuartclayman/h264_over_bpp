// BufferingUDPChunkStreamer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.processor;

import java.util.ArrayList;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.ChunkStreamer;
import cc.clayman.net.ChunkDepacketizer;
import cc.clayman.net.BPPDepacketizer;
import cc.clayman.net.UDPReceiver;
import cc.clayman.util.Verbose;

/*
 * The reads packets from a UDPReceiver and returns ChunkInfo objects,
 * but does some buffering.
 * Buffers a number of packets, and orders them by sequence no.
 */
public class BufferingUDPChunkStreamer extends UDPChunkStreamer implements ChunkStreamer {
    int bufferSize = 3;

    ArrayList<ChunkInfo> list = null;

    /**
     * A UDPChunkStreamer takes a UDPReceiver and returns a ChunkInfo
     * on each call
     * Buffer size is 3 by default
     * @param receiver a UDPReceiver
     */
    public BufferingUDPChunkStreamer(UDPReceiver receiver) {
        super(receiver);
        allocateBuffer();
    }

    /**
     * A UDPChunkStreamer takes a UDPReceiver and returns a ChunkInfo
     * on each call
     * @param receiver a UDPReceiver
     * @param depacketizer a ChunkDepacketizer
     */
    public BufferingUDPChunkStreamer(UDPReceiver receiver, ChunkDepacketizer depacketizer) {
        super(receiver, depacketizer);
        allocateBuffer();
    }

    /**
     * A UDPChunkStreamer takes a UDPReceiver and returns a ChunkInfo
     * on each call
     * @param receiver a UDPReceiver
     * @param bufferSize 
     */
    public BufferingUDPChunkStreamer(UDPReceiver receiver, int bufferSize) {
        super(receiver);
        this.bufferSize = bufferSize;
        allocateBuffer();
    }

    /**
     * A UDPChunkStreamer takes a UDPReceiver and returns a ChunkInfo
     * on each call
     * @param receiver a UDPReceiver
     * @param depacketizer a ChunkDepacketizer
     * @param bufferSize 
     */
    public BufferingUDPChunkStreamer(UDPReceiver receiver, ChunkDepacketizer depacketizer, int bufferSize) {
        super(receiver, depacketizer);
        this.bufferSize = bufferSize;
        allocateBuffer();
    }

    /**
     * Allocate a buffer
     */
    protected void allocateBuffer() {
        list = new ArrayList<ChunkInfo>(bufferSize);
    }

    /**
     * Returns true if there are more ChunkInfo
     */
    public boolean hasNext() {
        if (receiver.isEOF() || (! receiver.isRunning())) {
            if (Verbose.level >= 2) {
                System.err.println("BufferingUDPChunkStreamer hasNext() EOF: size = " + list.size());
            }
            // on EOF or not running we need to drain the list
            if (list.size() == 0) {
                return false;
            } else {
                return true;
            }
        } else {
            // not EOF
            return true;
        }
    }

    /**
     * Returns the next ChunkInfo 
     */
    public ChunkInfo next() {
        // do we need to drain the list
        if (receiver.isEOF() || (!receiver.isRunning())) {
            if (list.size() == 0) {
                return null;
            } else {
                // just drain, dont read any more
                if (Verbose.level >= 3) {
                    System.err.println("BufferingUDPChunkStreamer next(): chunk " + list.get(0).getSequenceNumber() + " size = " + list.size());
                }
            }
        } else {
            while (list.size() < bufferSize) {
                // we need to read in some more
                packet = receiver.getPacket();
                
                if (packet == null) {
                    // the receiver has nothing to pass on
                    if (Verbose.level >= 2) {
                        System.err.println("BufferingUDPChunkStreamer getPacket() returns null.  size = " + list.size());
                    }
                    break;
            
                } else {
                    // convert the packet into a ChunkInfo
                    ChunkInfo chunk = depacketizer.convert(packet);

                    if (list.size() == 0) {
                        // empty list - so add it
                        list.add(chunk);
                    } else {
                        boolean added = false;
                        
                        // check if the chunk arrived out of order
                        for (int i=0; i < list.size(); i++) {
                            ChunkInfo inList = list.get(i);

                            if (chunk.getSequenceNumber() < inList.getSequenceNumber()) {
                                // out of order
                                list.add(i, chunk);
                                added = true;

                                if (Verbose.level >= 2) {
                                    System.err.println("BufferingUDPChunkStreamer next: chunk " + chunk.getSequenceNumber() + " out of order");
                                }

                                break;
                            }
                        }

                        // we got to the end of the existing one
                        if (!added) {
                            list.add(chunk);
                        }
                    }
                }
            }
        }

        if (list.size() > 0) {
            // the array list is the right size
            // get the first one
            ChunkInfo first = list.get(0);
            // now remove it
            list.remove(0);

            // return it
            return first;
        } else {
            return null;
        }
        
    }

    
}
