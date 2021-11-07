// TemporalLayerModelGOB16.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021


package cc.clayman.h264;


/**
 * This holds info about the temporal layer relationships
 * for encodings with groups of 16 GOBs.
 *
 * The first 7 (1-7) are different.  Then a rolling sequence of 16 VCL NALs
 * (8 - 23, 24 - 39, 40 - 55, ...).
 *
 */
public class TemporalLayerModelGOB16 implements TemporalLayerModel {
    // The first 7 VCL NALs are different
    // With Layers there will be N copies, where there are N layers
    // 1 - 7
    private static final TemporalLayerModel.Tuple[] first7 = {
        new TemporalLayerModel.Tuple(Frame.I, Temporal.T0, -1),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T2, 2),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T3, -1),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -3),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -2),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T3, 0),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -2)
    };

    // A rolling sequence of 16 VCL NALs
    // 8 - 23, 24 - 39, 40 - 55, ...
    private static final TemporalLayerModel.Tuple[] gob16Tuples = {
        new TemporalLayerModel.Tuple(Frame.I, Temporal.T0, 8),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T1, -1),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -3),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T2, 1),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T3, -2),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -4),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -3),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T3, -1),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -3),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -2),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T2, 2),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T3, -1),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -3),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -2),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T3, 0),
        new TemporalLayerModel.Tuple(Frame.P, Temporal.T4, -2)

    };


    /**
     * Get a TemporalLayerModel for a speified NAL.
     * Pass in the VCL NAL number. Starts at 1.
     * @return a Tuple
     */
    public TemporalLayerModel.Tuple getLayerInfo(int count) {
        if (count == 0) {
            throw new Error("Count starts from 1");
        } else if (count < 8) {
            int offset = count - 1;
            TemporalLayerModel.Tuple m = first7[offset];
            return m;
        } else {
            int offset = (count - 8) % 16;
            TemporalLayerModel.Tuple m = gob16Tuples[offset];
            return m;
            
        }
    }
}
