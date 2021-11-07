// SignificanceModel3Layers.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021


package cc.clayman.h264;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * This holds info about the significance model relationships
 * for 3 layers and BPP
 *
 */
public class SignificanceModel3Layers implements SignificanceModel {
    private static final Map<SignificanceModel.Tuple, Integer> significanceMap = initMap();

    private static Map<SignificanceModel.Tuple, Integer> initMap() {
        Map<SignificanceModel.Tuple, Integer> map = new HashMap<>();

        // I T0
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L0), 1);
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L1), 6);
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L2), 11);
        // P T1
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L0), 2);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L1), 7);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L2), 12);
        // P T2
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L0), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L1), 8);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L2), 13);
        // P T3
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L0), 4);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L1), 9);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L2), 14);
        // P T4
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L0), 5);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L1), 10);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L2), 15);


        return Collections.unmodifiableMap(map);
    }


    /**
     * A model takes a Frame a Temporal and a Layer and returns a Significance value.
     */
    public int getSignificanceValue(Frame f, Temporal t, Layer l) {
        SignificanceModel.Tuple tuple = new SignificanceModel.Tuple(f, t, l);

        return significanceMap.get(tuple);
    }
    

}
