// NAL2.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: June 2021


package cc.clayman.h264;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

/**
 * An H264 NAL - Network Abstraction Layer.
 */
public class NAL2 extends NAL {

    private final static Map<Integer, String> SLICE_MAP = 
        Collections.unmodifiableMap(new HashMap<Integer, String>() {{ 
            put(0, "P Slice");
            put(1, "B Slice");
            put(2, "I Slice");
        }});
            
    /**
     * Construct a NAL given the number of marker bytes, the size of the NAL,
     * the full set of bytes.
     */
    public NAL2(int markerSize, int size, ByteBuffer buf) {
        super(markerSize, size, buf);

        bs_init();

        //System.out.println("NAL: " + " size = " + size + " position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity());
        
    }

    /**
     * Construct a NAL given an exisiting NAL.
     */
    public NAL2(NAL nab) {
        this.markerSize = nab.markerSize;
        this.size = nab.size;
        this.inBuffer = nab.inBuffer;


        bs_init();

        //System.out.println("NAL: " + " size = " + size + " position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity());
        
    }


    // Inspriation from
    // https://github.com/shi-yan/H264Naked/blob/master/h264bitstream-0.1.9/h264_stream.c
    // and
    // https://github.com/shi-yan/H264Naked/blob/master/h264bitstream-0.1.9/bs.h

    int bits_left = 0;
    int p = 1;                  // ptr / offset into NAL
    int end = 0;


    protected void bs_init() {
         bits_left = 8;
         p = 1;                  // ptr / offset into NAL
         end = size - markerSize;
    }
    
    public boolean bs_eof(NAL b) {
        if (p >= end) {
            System.err.println("eof: true");
            return true;
        } else {
            //System.err.println("eof: false");
            return false;
        }
    }

    
    public long bs_read_u1(NAL b) {
        long r = 0;
    
        bits_left--;

        if (! bs_eof(b))
            {
                r = (get(p) >> bits_left) & 0x01;
            }

        if (bits_left == 0) {
            p ++;
            bits_left = 8;
        }

        return r;
    }

    public long bs_read_u(NAL b, int n) {
        long r = 0;
        int i;
        
        for (i = 0; i < n; i++) {
            r |= ( bs_read_u1(b) << ( n - i - 1 ) );
        }
        return r;
    }

    public long bs_read_ue(NAL b) {
        long r = 0;
        int i = 0;

        while( (bs_read_u1(b) == 0) && (i < 32) && (!bs_eof(b)) ) {
                i++;
            }
        r = bs_read_u(b, i);
        r += (1 << i) - 1;
        return r;
    }


    public String getSliceType(int n) {
        return SLICE_MAP.get(n);
    }

}
