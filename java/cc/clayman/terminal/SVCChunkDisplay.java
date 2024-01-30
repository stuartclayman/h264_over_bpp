// SVCChunkDisplay.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: April 2023

package cc.clayman.terminal;

import cc.clayman.chunk.SVCChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.h264.NALType;
import cc.clayman.util.ANSI;

/**
 * Generate some lines of data on the terminal.
 */
public class SVCChunkDisplay extends ChunkDisplay {


    private final static String[] COLOURS = {
        ANSI.RED_BG, ANSI.CYAN_BG, ANSI.GREEN_BG
    };
    
    /**
     * Create a SVCChunkDisplay object.
     * Use the Default terminal width of 80
     */
    public SVCChunkDisplay(int maxPayloadSize) {
        super(maxPayloadSize);
    }
    
    /**
     * Create a SVCChunkDisplay object.
     * The terminal width is in termWidth
     */
    public SVCChunkDisplay(int termWidth, int maxPayloadSize) {
        super(termWidth, maxPayloadSize);
    }

    /**
     * Display a SVCChunkInfo
     */
    public int display(SVCChunkInfo chunk) {
        int totalOut = 0;
        
        calculateBytesPerChar();


        System.out.printf("%s", ANSI.WHITE_BG + " " + ANSI.RESET_COLOUR);
        
        ChunkContent[] content = chunk.getChunkContent();

        for (int c=0; c<content.length; c++) {
            int chunkSize = content[c].offset();

            // work out how many characters to display
            int charactersToDisplay = (int)Math.ceil((double)chunkSize / bytesPerChar);

            //System.err.println("display: chunkSize = " + chunkSize + " bytesPerChar = " + bytesPerChar + " charactersToDisplay = " + charactersToDisplay);

            totalOut += charactersToDisplay;

            if (chunkSize == 0) {
            } else {
                String format = "%s%s%" + charactersToDisplay + "s%s%s";
                String colour;
                
                if (chunk.getNALType() == NALType.VCL) {
                    if (chunk.chunkCount() == 1) {
                        colour =  ANSI.MAGENTA_BG;
                    } else {
                        colour = COLOURS[c];
                    }
                } else {
                    colour = ANSI.BLACK_BG + ANSI.WHITE;
                }
                
                System.out.printf(format, ANSI.FAINT_COLOUR, colour, " " + chunkSize, ANSI.FAINT_OFF, ANSI.RESET_COLOUR);
            }

        }

        return totalOut;
        
    }



}
