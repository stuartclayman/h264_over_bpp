// TemporalLayerModel.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021


package cc.clayman.h264;


/**
 * This holds info about the temporal layer relationships
 */
public interface TemporalLayerModel {

    /**
     * A model takes the count of the VCL NALs, and returns a Tuple.
     * There are many non-VCL NALs, but these should not be included in the count.
     *
     * Apply this adjustment to calculate the source frame no. where
     * source frame no = vcl count + adjustment
     */
    public TemporalLayerModel.Tuple<Frame, Temporal, Integer> getLayerInfo(int count);
    
    public class Tuple<Frame, Temporal, Integer> { 
        public final Frame frame;               // The Frame type: I, P, ...
        public final Temporal temporal;         // The temporal layer: T0, T1, ...
        public final int adjustment;            // The adjustment to the VCL NAL number
                                                // to get source frame number,
                                                // but ignoring the layer
        
        public Tuple(Frame f, Temporal t, int adj) {
            this.frame = f;
            this.temporal = t;
            this.adjustment = adj;
        } 

        @Override
        public String toString() {
            return "(" + frame + "," + temporal + ", " + adjustment + ")";
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
                other_.adjustment == this.adjustment;
                
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = adjustment;
            result = prime * result + frame.hashCode() + temporal.hashCode();
            return result;
        }
    }

}
