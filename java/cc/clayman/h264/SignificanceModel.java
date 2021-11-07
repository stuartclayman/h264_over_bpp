// SignificanceModel.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021


package cc.clayman.h264;


/**
 * This holds info about the significance relationships
 */
public interface SignificanceModel {

    /**
     * A model takes a Frame a Temporal and a Layer and returns a Significance value.
     */
    public int getSignificanceValue(Frame f, Temporal t, Layer l);
    
    public class Tuple<Frame, Temporal, Integer> { 
        public final Frame frame;               // The Frame type: I, P, ...
        public final Temporal temporal;         // The temporal layer: T0, T1, ...
        public final Layer layer;               // The quality layer

        
        public Tuple(Frame f, Temporal t, Layer l) {
            this.frame = f;
            this.temporal = t;
            this.layer = l;
        } 

        @Override
        public String toString() {
            return "(" + frame + "," + temporal + ", " + layer + ")";
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof Tuple)){
                return false;
            }

            Tuple other_ = (Tuple)other;

            return other_.frame.equals(this.frame) &&
                other_.temporal.equals(this.temporal) &&
                other_.layer == this.layer;
                
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = prime * layer.hashCode() + frame.hashCode() + temporal.hashCode();
            return result;
        }
    }

}
