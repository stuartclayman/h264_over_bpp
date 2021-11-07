// H264InputStream.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: June 2021


package cc.clayman.h264;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.EOFException;
import java.nio.ByteBuffer;

/**
 * An InputStream that has H264 encoded data in it.
 * Skip through and get each NAL.
 */
public class H264InputStream  {
    DataInputStream  theInputStream = null;		// THE input stream
    BufferedInputStream bufferedStream = null;

    // The ByteBuffer holds data from the stream
    // and stores the bytes read
    // Expect to go one NAL at a time
    ByteBuffer inBuffer = null;

    // A byte offset into the InputStream
    int streamPos = 0;

    // A byte offset into the ByteBuffer
    int bufPos = 0;

    // A byte offset into the ByteBuffer for reading the data
    int readPos = 0;

    // A byte offset into the ByteBuffer for the next valid data
    int nextPos = 0;

    // Have we reached EOF
    boolean eof = false;

    // Have we finished
    boolean finished = false;

    // BUF SIZE
    // 16K is fine for most files, but some need a bigger buffer
    private final int BUF_SIZE = 4 * 1024;


    /**
     * Construct the H264 stream from an InputStream.
     */
    public H264InputStream(InputStream stream) {
	// create the IO stream
	bufferedStream = new BufferedInputStream(stream); 
	theInputStream = new DataInputStream (bufferedStream);

	// allocate a buffer to store data 
	inBuffer = ByteBuffer.allocate(BUF_SIZE);
    }

    /**
     * Close the stream.
     */
    public void close() throws IOException {
	theInputStream.close();
    }


    /**
     * Has the IO stream reached EOF?
     */
    public boolean isEOF() { 
	return eof;
    }

    /**
     * Get the current NAL
     */
    public NAL getNAL() {
        if (!finished && findNALStartCode() != -1) {
            return collectNAL();
        } else {
            if (!finished) {
                // on EOF there is still one more NAL before we finish
                finished = true;
                return collectNAL();
            } else {
                return null;
            }
        }
    }


    /**
     * Try and find the NAL START CODE.
     * It can be 00 00 01 or 00 00 00 01
     * Returns the position at the end of the NAL
     * or -1 on EOF or other IOException.
     */
    protected int findNALStartCode() {
        if (eof) {                // don't do anytihng on EOF
            return -1;
            
        } else {
            // The buffer position() will be just after the NAL
        
            try {
                // Grab some bits and checks them against a value.
                while(next_bits(24) != 0x000001 && next_bits(32) != 0x00000001);

                readPos = bufPos;

                //System.err.println("F readPos = " + readPos + " nextPos = " + nextPos + " bufPos = " + bufPos);


                while(more_data_in_byte_stream() && next_bits(24) != 0x000001 && next_bits(32) != 0x00000001);


                if (!eof) {

                    // nextPos should be just after a NAL marker
                    // so patch it up to be before the NAL marker
                    if  (next_bits(32) == 0x00000001) {
                        nextPos -= 3;
                    } else if (next_bits(24) == 0x000001) {
                        nextPos -= 2;
                    }

                }
            
                //System.err.println("S readPos = " + readPos + " nextPos = " + nextPos + " bufPos = " + bufPos);

                return bufPos ;
            } catch (EOFException ioe) {
                //System.err.println("EOF at " + inBuffer.position() + " bufPos = " + bufPos + " nextPos = " + nextPos);
                
                return -1;
            } catch (IOException ioe) {
                System.err.println("IOException " + ioe);
                
                return -1;
            }
        }
    }


    /**
     * Collect the latest NAL.
     */
    protected NAL collectNAL() {
        // Here we return the current ByteBuffer
        // and create a new one for the next NAL

	// allocate a buffer to store data 
	ByteBuffer newBuf = ByteBuffer.allocate(BUF_SIZE);

        
        // Suck out next NAL marker into a new ByteBuffer
        int sizeNAL = bufPos - nextPos;
        for (int i = 0; i < sizeNAL; i++) {
            byte val = inBuffer.get(nextPos+ i);
            newBuf.put(val);

            //System.err.println("val[" + (nextPos + i) + "] = " + val + " -> newBuf[" + i + "] = " + newBuf.get(i));
        }
        newBuf.flip();

        // Prepare the NAL
        inBuffer.position(0);
        NAL nal = new NAL(readPos, nextPos, inBuffer);

        // Cross check the the first bytes and last byte look correct
        //System.err.println(inBuffer.get(0) + " " + inBuffer.get(1)+ " " + inBuffer.get(2) + " " + inBuffer.get(3) + " .... " + (int)(inBuffer.get(nal.getSize()-1) & 0xff));
        
        // Now reset the values for the inBuffer for the next time around
        readPos = sizeNAL;
        nextPos = 0;
        bufPos = sizeNAL;
        inBuffer = newBuf;
        inBuffer.position(sizeNAL);
        inBuffer.limit(inBuffer.capacity());

        //System.out.println("readPos = " + readPos + " nextPos = " + nextPos + " bufPos = " + bufPos);

        return nal;
    }


    /**
     * Next bits grabs some bits (up to 32 of them).
     * The spec uses bits
     *
     */
    public int next_bits(int n) throws IOException {

        if (n > 32) {
            throw new Error("No more than 32 bits");
        }

        
        // mark it
        inBuffer.mark();
        
        // how many bytes available
        //System.out.println("TOP: position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity());
        
        int available =  bufPos;                        // available bytes
        int startPosition = inBuffer.position();        // where write position is

        // how many bytes do we need
        int needed = n / 8;
        
        // need more bytes ?
        if (needed > available) {
            //System.out.println("grab " + (needed - available));
            
            // grab some
            readBytes(needed - available);
        }



        // check buffer position after read
        if (bufPos != inBuffer.position()) {
            System.err.println("bufPos " + bufPos + " != inBuffer.position() " + inBuffer.position());
        }

        // try resetting position
        inBuffer.position(bufPos - needed);

        //System.out.println("next_bits: position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity());

        // Now check the number of bits
        // and do a sift right, if necessary
        int  next_bits_val = inBuffer.getInt();

        //System.out.println("RAW next_bits_val = " + next_bits_val + " SHIFT " + ( (32 - n)));

        next_bits_val = next_bits_val >>> ( (32 - n));

        //System.out.println("RES next_bits_val = " + next_bits_val);


        //System.out.println("position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity());

        // reset position for next read
        inBuffer.position(bufPos);
        

        //System.out.println("BOT: position = " + inBuffer.position() + " limit " + inBuffer.limit() + " capacity " + inBuffer.capacity() + " next_bits_val " + next_bits_val);
        
        
        return next_bits_val;
        
    }

    /**
     * Read a byte, and return true.
     * Support fn for findNALStartCode()
     */
    protected boolean more_data_in_byte_stream() throws IOException {
        readByte();

        nextPos = bufPos - 1;

        return true;
    }
     
    /**
     * Read a byte from the underlying InputStream 
     * and keep a copy of it in the ByteBuffer.
     */
    protected byte readByte() throws IOException {
        byte b = 0;
        
        if (eof) {
            throw new EOFException("eof");
        } else {

            try { 
                // read the byte
                b = theInputStream.readByte();

                streamPos++;
                bufPos++;

                // add it to the  buffer
                addByteToBuffer(b);

                return b;
            } catch (EOFException ee) {
                // hit EOF during readByte() -- save the byte
                streamPos++;
                bufPos++;

                // add it to the  buffer
                addByteToBuffer(b);

                // mark eof as true
                eof  = true;
                return b;
            }
        }
    }

    
    /**
     * Read some bytes
     */
    protected int readBytes(int n) throws IOException {
	for (int count=0; count<n; count++) {
            readByte();

            /*
	    byte b = theInputStream.readByte();
            streamPos++;
            bufPos++;
            //System.out.println(" " + streamPos);
	    // add it to the pack buffer
            addByteToBuffer(b);
            */
	}

	return n;
    }

    /**
     * Add a byte to the buffer
     */
    protected int addByteToBuffer(byte b) {

        int inBufferPos = inBuffer.position();

        // check if we have enough room to add this byte
        if (inBufferPos == inBuffer.capacity() - 1) {
            // the buffer is full, so we need to allocate more space
            // allowing for an int - i.e. 4 bytes

            //System.err.println("ByteBuffer allocate " + inBuffer.capacity() * 2);

            // Make one double the size
            ByteBuffer newBuf = ByteBuffer.allocate(inBuffer.capacity() * 2);

            // copy in old bytes
            inBuffer.rewind();
            newBuf.put(inBuffer);

            // reset position
            newBuf.position(inBufferPos);

            // link to newBuf
            inBuffer = newBuf;
        }

        
	inBuffer.put(b);

        return inBuffer.position();
    }

    
    /**
     * Return the stream pos
     */
    public long getStreamPosition() {
	return streamPos;
    }

}
