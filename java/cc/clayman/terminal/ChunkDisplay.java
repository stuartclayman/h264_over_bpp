// ChunkDisplay.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.terminal;

import cc.clayman.chunk.ChunkInfo;
import cc.clayman.chunk.ChunkContent;
import cc.clayman.h264.NALType;
import cc.clayman.util.ANSI;

/**
 * Generate some lines of data on the terminal.
 */
public class ChunkDisplay {

    // Default terminal width
    int termWidth = 80;

    // The max payload size
    int maxPayloadSize = 0;

    // The no of payload bytes to a terminal character
    int bytesPerChar;

    private final static String[] COLOURS = {
        ANSI.RED_BG, ANSI.CYAN_BG, ANSI.GREEN_BG
    };
    
    /**
     * Create a ChunkDisplay object.
     * Use the Default terminal width of 80
     */
    public ChunkDisplay(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }
    
    /**
     * Create a ChunkDisplay object.
     * The terminal width is in termWidth
     */
    public ChunkDisplay(int termWidth, int maxPayloadSize) {
        this.termWidth = termWidth;
        this.maxPayloadSize = maxPayloadSize;
    }

    /**
     * Display a ChunkInfo
     */
    public int display(ChunkInfo chunk) {
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

    /**
     * Get the current terminal width
     */
    public int getTerminalWidth() {
        return termWidth;
    }

    /**
     * Set the current terminal width
     * @return old width
     */
    public int setTerminalWidth(int width) {
        int old = termWidth;
        termWidth = width;
        return old;
    }

    /**
     * Calculate the number of bytes per terminal character
     */
    protected void calculateBytesPerChar() {
        int number = maxPayloadSize / termWidth;

        bytesPerChar = number;
    }

}
