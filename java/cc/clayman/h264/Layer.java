// Layer.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021


package cc.clayman.h264;


/**
 * This holds info about the temporal layer relationships
 * for encodings with groups of 16 GOBs.
 */
public enum Layer {
    // Layers
    L0(0),                      // Layer 0
    L1(1),                      // Layer 1
    L2(2),                      // Layer 2
    L3(3),                      // Layer 3
    L4(4);                      // Layer 4


    private final int value;

    Layer(final int length) {
        value = length;
    }

    public int getValue() { return value; }

    // Get the Ith Layer from the array
    public static Layer get(int i) {
        return array.get(i);
    }

    // An array of Layer
    private static final java.util.ArrayList<Layer> array = new java.util.ArrayList<>();

    // Fill the array
    static {
        for (Layer l: values()) {
            array.add(l);
        }
    }

}
