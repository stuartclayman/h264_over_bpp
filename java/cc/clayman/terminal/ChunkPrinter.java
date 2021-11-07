// ChunkPrinter.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2021

package cc.clayman.terminal;

import java.io.PrintStream;
import cc.clayman.chunk.ChunkInfo;


/**
 * Print a ChunkInfo to an OutputStream
 */
public class ChunkPrinter {
    PrintStream outputStream;

    // Default no of columns
    int columns = 80;

    /**
     * Create a ChunkPrinter with a specified PrintStream
     * Use Default no of columns: 80
     */
    public ChunkPrinter(PrintStream stream) {
        outputStream = stream;
    }

    /**
     * Create a ChunkPrinter with a specified PrintStream
     */
    public ChunkPrinter(PrintStream stream, int columns) {
        outputStream = stream;
        this.columns = columns;
    }


    public void printChunk(ChunkInfo chunk, int count, int total, int payloadSize) {
        
        // try and find the no of columns from the Environment
        String columnsEnv = System.getenv("COLUMNS");


        if (columnsEnv != null) {
            int columnVal = Integer.parseInt(columnsEnv);

            columns = columnVal;
        }

        outputStream.printf("%-8d", count);                     // N
        outputStream.printf("%-10s", chunk.getNALType());       // type

        // used up 18 chars

        ChunkDisplay displayer = new ChunkDisplay(columns - 22, payloadSize);
        displayer.display(chunk);
        
        outputStream.println(" ");
                    
    }


}
