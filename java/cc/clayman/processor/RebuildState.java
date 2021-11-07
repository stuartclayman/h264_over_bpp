// RebuildState.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.processor;

import java.util.List;
import java.util.Optional;
import cc.clayman.h264.NAL;
import cc.clayman.h264.NALType;

/**
 * Used as a return value from NALRebuilder process()
 *
 * A way to overcome the fact the Java types are not as powerful as Haskell types.
 * Would prefer:  NAL_VALUES int [NAL] | PROCESSING int | FRAGMENT_END int | MISSSING int
 */
public class RebuildState {

    public final State state;
    public final NALType nalType;
    public final int nalNumber;
    
    public final List<NAL> nals;

    public RebuildState(State state, NALType nalType, int nalNo) {
        this.state = state;
        this.nalType = nalType;
        this.nalNumber = nalNo;
        this.nals = null;
    }

    public RebuildState(State state, NALType nalType,  int nalNo,  List<NAL> nals) {
        if (state != State.NAL_VALUES) {
            throw new IllegalStateException("State value not valid with List<NAL>");
        } else {
            this.state = state;
            this.nalType = nalType;
            this.nalNumber = nalNo;
            this.nals = nals;
        }
    }

    public Optional<List<NAL>> getNALs() {
        return Optional.of(nals);
    }

    /**
     * An inner enum, to hold the state value
     */
    public enum State {
        NAL_VALUES,             // The result is a list of NALs
        PROCESSING,             // Some Chunks are being processed
        FRAGMENT_END,           // All the fragments are marked as being last one
        MISSSING;               // A Chunk is missing
    }
}
