// SignificanceModel4LayersReza1.java
// Author: Mohammadreza Ghafari
// Email: <mohammadreza.ghafari@loria.fr>
// Date: August 2026


package cc.clayman.h264;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * This holds info about the significance model relationships
 * for 4 layers and BPP
 *
 */
public class SignificanceModel4LayersReza1 implements SignificanceModel {
    private static final Map<SignificanceModel.Tuple, Integer> significanceMap = initMap();

    private static Map<SignificanceModel.Tuple, Integer> initMap() {
        Map<SignificanceModel.Tuple, Integer> map = new HashMap<>();

        // keeping all 4P
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L0), 1);
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L1), 1);
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L2), 1);
        map.put(new SignificanceModel.Tuple(Frame.I, Temporal.T0, Layer.L3), 1);
        // P T1
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L0), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L1), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L2), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T1, Layer.L3), 3);
        // P T2
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L0), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L1), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L2), 3);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T2, Layer.L3), 3);
        // P T3
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L0), 5);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L1), 5);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L2), 5);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T3, Layer.L3), 5);
        // P T4
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L0), 7);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L1), 7);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L2), 7);
        map.put(new SignificanceModel.Tuple(Frame.P, Temporal.T4, Layer.L3), 7);


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
