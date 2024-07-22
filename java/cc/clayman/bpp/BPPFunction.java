// BPPFunction.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: July 2024

package cc.clayman.bpp;

/*
 * A representation of a BPP function
 */

public abstract class BPPFunction {
    /**
     * Get a 14 bit respresentation of the function and args
     */
    public abstract int representation();

    /**
     * Get the arg
     */
    public abstract int getArg();

    /**
     * Set the arg
     */
    public abstract void setArg(int val);

    /**
     * Convert a 14 bit respresentation to the function 
     * Try 6 bits for the function and 8 bits for the arg
     */
    public static BPPFunction convert(int bits) {
        int fnBits = ((bits & 0x3FFF) >> 8) & 0x3F;


        //System.err.printf("BPPFunction convert bits = 0x%05X fnBits = 0x%02X\n", bits, fnBits);

        if (fnBits == BPP.Function.NONE) {
            // allocation a function
            return new None();

        } else if (fnBits == BPP.Function.RELAX_THRESHOLD) {
            // allocation a function
            BPPFunction fn = new RelaxThreshold();
            // get the function to process the args
            fn.setArg(bits & 0xFF);

            return fn;
        } else {
            return null;
        }
    }


    public static final class None extends BPPFunction {
        public None() {}
        
        public int representation() {
            return 0;
        }

        public int getArg() {
            return 0;
        }

        public void setArg(int val) {
        }

        public String toString() {
            return "None()";
        }
    }

    public static final class RelaxThreshold extends BPPFunction {
        public int arg = 0;

        public RelaxThreshold() {}
        
        public RelaxThreshold(int arg) {
            this.arg = arg;
        }

        public int representation() {
            return ((BPP.Function.RELAX_THRESHOLD & 0x03F) << 8) | (arg & 0xFF);
        }

        public int getArg() {
            return arg;
        }

        public void setArg(int val) {
            arg = val;
        }

        public String toString() {
            return "RelaxThreshold(" + arg + ")";
        }
    }


}
