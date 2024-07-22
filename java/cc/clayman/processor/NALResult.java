// NALResult.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.processor;

import java.util.Optional;
import cc.clayman.h264.NAL;
import cc.clayman.h264.NALType;

/**
 * Used as a return value from NALRebuilder next()
 *
 * A way to overcome the fact the Java types are not as powerful as Haskell types.
 * Would prefer:  NAL int | WASHED int | DROPPED int | LOST int
 */
public class NALResult {

    public final State state;
    public final NALType nalType;
    public final int number;    // Might be the NAL number in a sequence
                                // or a layer number 
    
    public final NAL nal;

    public NALResult(State state, NALType nalType, int no) {
        this.state = state;
        this.nalType = nalType;
        this.number = no;
        this.nal = null;
    }

    public NALResult(State state, NALType nalType, int no,  NAL nal) {
        if (state != State.NAL) {
            throw new IllegalStateException("State value not valid with NAL");
        } else {
            this.state = state;
            this.nalType = nalType;
            this.number = no;
            this.nal = nal;
        }
    }

    public Optional<NAL> getNAL() {
        return Optional.of(nal);
    }

    public String toString() {
        return "NALResult: " + state + " " + nalType + " " + number + " " + (nal == null ? "" : nal);
    }

    
    /**
     * An inner enum, to hold the state value
     */
    public enum State {
        NAL,                    // The result is a NAL
        WASHED,                 // The network node washed a NAL
        DROPPED,                // The NAL was dropped
        LOST;                   // Something was lost in transmission
    }
}
