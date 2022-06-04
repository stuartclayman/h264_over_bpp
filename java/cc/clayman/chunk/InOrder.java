// InOrder.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.chunk;

/*
 * A ChunkSizeCalculator that creates chunks in the order they come.
 */
public class InOrder implements ChunkSizeCalculator {

    /**
     * Calculate the Chunk sizes
     */
    public int[] calculate(int payloadSize, int [] sizes) {
        int count = sizes.length;

        // allocate an array
        int[] result = new int[count];

        boolean isSet = false;
        
        for (int c=0; c<count; c++) {
            if (!isSet) {
                if (sizes[c] == 0) {
                    // already used up
                    result[c] = 0;
                } else if (sizes[c] > 0 && left > 4) {
                    // more to collect
                    // need to have at least 4 bytes so that the NAL marker fits in
                    result[c] = payloadSize;
                    isSet = true;
                }
            } else {
                // already set a val, so do no more
                result[c] = 0;
            }

        }

        //System.out.println(java.util.Arrays.toString(sizes) + " --> " + java.util.Arrays.toString(result));


        return result;
    }
    
}
