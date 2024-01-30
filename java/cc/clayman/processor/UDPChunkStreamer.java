// UDPChunkStreamer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.processor;

import java.net.DatagramPacket;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.ChunkStreamer;
import cc.clayman.net.ChunkDepacketizer;
import cc.clayman.net.BPPDepacketizer;
import cc.clayman.net.UDPReceiver;

/*
 * The reads packets from a UDPReceiver and returns ChunkInfo objects.
 */
public class UDPChunkStreamer implements ChunkStreamer {
    // The UDP receiver
    UDPReceiver receiver = null;

    // A received packet
    DatagramPacket packet = null;

    // A ChunkDepacketizer to convert packets to ChunkInfo
    ChunkDepacketizer depacketizer = null;

    
    /**
     * A UDPChunkStreamer takes a UDPReceiver and returns a ChunkInfo
     * on each call
     * @param receiver a UDPReceiver
     */
    public UDPChunkStreamer(UDPReceiver receiver) {
        this.receiver = receiver;
        depacketizer = new BPPDepacketizer();
    }

    /**
     * A UDPChunkStreamer takes a UDPReceiver and returns a ChunkInfo
     * on each call
     * @param receiver a UDPReceiver
     * @param depacketizer a ChunkDepacketizer
     */
    public UDPChunkStreamer(UDPReceiver receiver, ChunkDepacketizer depacketizer) {
        this.receiver = receiver;
        this.depacketizer = depacketizer;
    }

    /**
     * Returns true if there are more ChunkInfo
     */
    public boolean hasNext() {
        return ! receiver.isEOF();
    }

    /**
     * Returns the next ChunkInfo 
     */
    public ChunkInfo next() {
        if (receiver.isEOF()) {
            return null;
        } else {
            packet = receiver.getPacket();

            if (packet == null) {
                // the receiver has gone away
                return null;
            
            } else {
                // convert the packet into a ChunkInfo
                ChunkInfo chunk = depacketizer.convert(packet);
                return chunk;
            }
        }
    }

    /**
     * Start the streamer
     */
    public boolean start() {
        // Here we need to start the UDPReceiver
        receiver.start();

        return true;
    }
    
    /**
     * Stop the streamer
     */
    public boolean stop() {
        // Here we need to stop the UDPReceiver
        receiver.stop();

        return true;
    }        
    
    
}


