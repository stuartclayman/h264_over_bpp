// NAL.java
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
public class NAL {
    int markerSize = 0;
    int size = 0;
    ByteBuffer inBuffer = null;

    // Type lookup table
    // Info from https://yumichan.net/video-processing/video-compression/introduction-to-h264-nal-unit/
    protected final static Map<Integer, String> CONSTANT_MAP = 
        Collections.unmodifiableMap(new HashMap<Integer, String>() {{ 
            put(0, "Unspecified");
            put(1, "Slice layer without partitioning non IDR");
            put(2, "Slice data partition A layer");
            put(3, "Slice data partition B layer");
            put(4, "Slice data partition C layer");
            put(5, "Slice layer without partitioning IDR");
            put(6, "Supplemental enhancement information (SEI)");
            put(7, "Sequence parameter set");
            put(8, "Picture parameter set");
            put(9, "Access unit delimiter");
            put(10, "End of sequence");
            put(11, "End of stream");
            put(12, "Filler data");
            put(13, "Sequence parameter set extension");
            put(14, "Prefix NAL unit");
            put(15, "Subset sequence parameter set");
            put(16, "Reserved");
            put(17, "Reserved");
            put(18, "Reserved");
            put(19, "Coded slice of an auxiliary coded picture without partitioning");
            put(20, "Coded slice extension");
            put(21, "Coded slice extension for depth view components");
            put(22, "Reserved");
            put(23, "Reserved");
            put(24, "Unspecified");
            put(25, "Unspecified");
            put(26, "Unspecified");
            put(27, "Unspecified");
            put(28, "Unspecified");
            put(29, "Unspecified");
            put(30, "Unspecified");
            put(31, "Unspecified");
        }});

    
    // Video Coding Layer (VCL)  types are 1-5, 19,20,21
    // Meta info - non-VCL - types are 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    protected final static List<Integer> videoTypes = Arrays.asList(1, 2, 3, 4, 5, 19, 20, 21);
    protected final static List<Integer> nonVideoTypes = Arrays.asList(6, 7, 8, 9, 10, 11, 12, 13, 14, 15);


    // Used by subclasses
    protected NAL() {}
    
    /**
     * Construct a NAL given the number of marker bytes, the size of the NAL,
     * the full set of bytes.
     */
    public NAL(int markerSize, int size, ByteBuffer buf) {
        this.markerSize = markerSize;
        this.size = size;
        this.inBuffer = buf;
        this.inBuffer.limit(size);
        this.inBuffer.rewind();


        //System.out.println("NAL: " + " size = " + size + " position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity());
        
    }

    /**
     * Get the header
     */
    public int getHeader()  {
        // Now get Header
        // It's just after the NAL marker
        byte header = inBuffer.get(markerSize);

        //  +---------------+
        //  |0|1|2|3|4|5|6|7|
        //  +-+-+-+-+-+-+-+-+
        //  |F|NRI|  Type   |
        //  +---------------+

        return header;

    }


    /**
     * Get the forbidden bit from header
     */
    public int getForbidden() {
        int header = getHeader();
        
        return (header >> 7);
    }
    
    /**
     * Get the NRI  from header
     */
    public int getNRI() {
        int header = getHeader();
        
        return (header & 0x7f) >> 5;
    }
    
    /**
     * Get the Type  from header
     */
    public int getType() {
        int header = getHeader();
        
        return header & 0x1f;
    }
    
    /**
     * Get the Type Class, based on the type.
     * Can be "VCL" for Video Coding Layer , or "non-VCL"
     */
    public NALType getTypeClass() {
        int type = getType();
        
        if (videoTypes.contains(type)) {
            return NALType.VCL;
        } else {
            return NALType.NONVCL;
        }
    }


    /**
     * Is this a Video Coding Layer.
     */
    public boolean isVideo() {
        return videoTypes.contains(getType()) ? true : false;
    }
    
    /**
     * Get the Type String from header
     */
    public String getTypeString() {
        int header = getHeader();
        
        int type = header & 0x1f;

        return CONSTANT_MAP.get(type);
    }
    
    
    /** 
     * The size of the NAL + the marker
     */
    public int getSize() {
        return size ;
    }

    /** 
     * The size of the NAL
     */
    public int getNALSize() {
        return size - markerSize;
    }

    /** 
     * The size of the NAL marker from the input stream.
     * Either 0x000001 or 0x00000001
     */
    public int getMarkerSize() {
        return markerSize;
    }

    /**
     * Get the Nth byte of the NAL.
     * Byte 0 is the header.
     */
    public byte get(int n) {
        return inBuffer.get(n + markerSize);
    }


    /** 
     * Get the raw data
     */
    public ByteBuffer buffer() {
        inBuffer.rewind();
        return inBuffer;
    }

    /**
     * to string
     */
    public String toString() {
        return getTypeClass() + " Content: " + inBuffer.position() + " -> " + inBuffer.limit();
    }
}
