// Frame.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021


package cc.clayman.h264;


/**
 * This holds info about the temporal layer relationships
 * for encodings with groups of 16 GOBs.
 */

public enum Frame {
    // 
    I(0),                      // I Frame 
    P(1),                      // P Frame 
    B(2);                      // B Frame 



    private final int value;

    Frame(final int length) {
        value = length;
    }

    public int getValue() { return value; }    
}

