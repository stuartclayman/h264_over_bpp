// DynamicSplit.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

/*
 * A ChunkSizeCalculator that splits the chunks based in the input amounts.
 */
public class DynamicSplit implements ChunkSizeCalculator {

    /**
     * Calculate the Chunk sizes
     */
    public int[] calculate(int payloadSize, int [] sizes) {
        int count = sizes.length;

        // allocate an array
        int[] result = new int[count];

        // first we determine how many chunks we need to hold data, based on the input amounts
        int howMany = 0;
        int bytesNeeded = 0;
        for (int c=0; c<count; c++) {
            bytesNeeded += sizes[c];

            if (sizes[c] > 0) {
                howMany++;
            }
        }
        
        
        // start with an estimate of even split
        int left = payloadSize;
        int estimate = payloadSize / howMany;

        // how many get the estimate
        int countEstimate = 0;
        
        // now allocate the result elements
        for (int c=0; c < count; c++) {
            if (sizes[c] == 0) {
                result[c] = 0;
            } else {
                // have some data to hold
                if (sizes[c] >= estimate) {
                    // too much
                    result[c] = estimate;
                    left -= estimate;
                    countEstimate++;
                } else {
                    result[c] = sizes[c] + 1;
                    left -= sizes[c] + 1;
                }
            }
        }

        // if there is any space left we can use it.

        if (countEstimate > 0) {
            int extra = left / countEstimate;

            // now see if we can increase the allocation
            for (int c=0; c < count; c++) {
                if (result[c] == 0) {
                    // nothing to do
                } else if (result[c] == estimate) {
                    result[c] += extra;                
                }
            }
        
        }        

        //for (int c=0; c<count; c++) {
        //    System.err.print(" val " + result[c]);
        //}
        //System.err.println();


        return result;
    }
    
}
