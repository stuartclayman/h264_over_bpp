// NALOutputStream.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021


package cc.clayman.h264;

import java.io.OutputStream;
import java.io.IOException;

/**
 * A NAL OutputStream writes NALs.
 */
public class NALOutputStream extends OutputStream {
    OutputStream outStream;

    public NALOutputStream(OutputStream stream) {
        outStream = stream;
    }

    public void write(int b) throws IOException {
        outStream.write(b);
    }
    
    /**
     * Write a NAL to the OutputStream
     */
    public void write(NAL nal) throws IOException {
        int markerSize = nal.getMarkerSize();

        if (markerSize == 3) {
            outStream.write((byte)0);
            outStream.write((byte)0);
            outStream.write((byte)1);
        } else if (markerSize == 4) {
            outStream.write((byte)0);
            outStream.write((byte)0);
            outStream.write((byte)0);
            outStream.write((byte)1);
        } 

        int nalSize = nal.getNALSize();

        for (int b=0; b<nalSize; b++) {
            outStream.write(nal.get(b));
        }
    }
}
