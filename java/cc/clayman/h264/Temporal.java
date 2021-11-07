// Temporal.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021


package cc.clayman.h264;


/**
 * This holds info about the temporal layer relationships
 * for encodings with groups of 16 GOBs.
 */
public enum Temporal {
    // Temporals
    T0(0),                      // Temporal 0
    T1(1),                      // Temporal 1
    T2(2),                      // Temporal 2
    T3(3),                      // Temporal 3
    T4(4);                      // Temporal 4
    private final int value;

    Temporal(final int length) {
        value = length;
    }

    public int getValue() { return value; }    
}
