// TCPReceiver.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2022

package cc.clayman.net;

import java.io.IOException;
import java.io.EOFException;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import cc.clayman.net.IP;
import cc.clayman.util.Verbose;

/**
 * Receive bytes over the network using TCP as a transport.
 * Convert into a ByteBuffer
 */
public class TCPReceiver implements Runnable {
    /*
     * The socket doing the listening
     */
    Socket socket;

    /*
     * A buffer to receive
     */
    ByteBuffer buffer;

    /*
     * The IP address
     */
    //InetSocketAddress address;

    InetAddress address;


    int port;
    
    /*
     * My thread.
     */
    Thread myThread;

    boolean running = false;

    /*
     * The InetSocketAddress of the last buffer received
     */
    InetAddress srcAddr;

    /*
     * EOF status
     */
    boolean eof = false;

    /*
     * The length of the last buffer received
     */
    int length;

    /*
     * The source port of the last buffer received
     */
    int srcPort;

    // A queue of ByteBuffer
    LinkedBlockingQueue<ByteBuffer> packetQueue = new LinkedBlockingQueue<ByteBuffer>();


    // Caller thread
    Thread caller;
    
    /**
     * Construct a TCPReceiver.
     */
    public TCPReceiver(int port) {
	// address is explicitly set to null
	address = null;
        
        this.port = port;
    }
    
    /**
     * Construct a TCPReceiver.
     */
    public TCPReceiver(InetSocketAddress addr) {
	// Receiving address
	address = addr.getAddress();
        this.port = addr.getPort();
    }

    /**
     * Set up the socket for the given addr/port,
     * and also a pre-prepared ByteBuffer
     */
    protected boolean connect() throws IOException {
        if (this.address == null)
            throw new IOException("No address specified");
        else
            socket = new Socket(address, port);

        return true;
    }

    /**
     * Close the socket 
     */
    protected void close() {
        try {
            eof = true;
            socket.close();
        } catch (Exception ioe) {
            throw new Error("Socket: " + socket + " can't close");
        }
    }

    /**
     * Start the receiver
     */
    public boolean start() {
        try {
            // connect to the network
            connect();
        
            // Run in Thread
            myThread = new Thread(this);
            myThread.start();

            running = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stop the receiver
     */
    public boolean stop() {
        try {
            // close the socket
            close();
            
            running = false;
            myThread.interrupt();

            caller.interrupt();
            
            return true;

        } catch (Exception e) {
            return false;
        }

    }

    /*
     * Create a new buffer
     */
    protected ByteBuffer newPacket(int length) {
        // Need enough space to convert the buffer to a DatagramPacket later on
        return ByteBuffer.allocate((length > IP.BASIC_PACKET_SIZE ? length : IP.BASIC_PACKET_SIZE));
    }




    /**
     * Get a ByteBuffer from the receiver
     */
    public ByteBuffer getPacket() {
        caller = Thread.currentThread();
        
        if (eof) {
            return null;
        } else {
            try {
                // get the next buffer off the queue
                ByteBuffer buffer = packetQueue.take();

                return buffer;
            
            } catch (InterruptedException ie) {
                return null;
            }
        }
    }
    
    /**
     * The main run loop.
     * It receives some bytes off the network and creates a ByteBuffer
     * which is put on the packetQueue
     */
    public void run() {
	// if we get here the thread must be running
        running = true;

        BufferedInputStream bin = null;
        DataInputStream in = null;
        
        try {
            // setup input stream 
            bin = new BufferedInputStream(socket.getInputStream());
            in = new DataInputStream(bin);

        } catch (IOException ioe) {
            if (Verbose.level >= 2) {
                System.err.println("IOException " + ioe);
            }
        }

        byte[] chnk = new byte[4];
        int chunk = 0;

        while (running) {
            try {

                // Expect 8 byte header
                // CHNK + size (as int)
                
                // receive from socket
                in.read(chnk, 0, 4);

                // check chnk == "CHNK"
                if (! (chnk[0] == 'C' &&
                       chnk[1] == 'H' &&
                       chnk[2] == 'N' &&
                       chnk[3] == 'K')) {
                    // stream or logic failure
                    throw new Error("Stream does not have CHNK in correct place");
                }

                // now get the length of the content -- 32 bits
                length = in.readInt();

                // double check size of buffer
                if (length > IP.BASIC_PACKET_SIZE) {
                    throw new Error("TCPReceiver: Chunk " + chunk + " sender created packet of size " + length + " > expected size " + IP.BASIC_PACKET_SIZE);
                }

                // allocate an emtpy buffer for use later
                buffer = newPacket(length);

                if (Verbose.level >= 2) {
                    System.err.println("Buffer allocate " + buffer.capacity());
                }


                // read the content into the buffer

                // the following doesn't always work with TCP streams
                // need to double check some aspects
                //
                // in.readFully(buffer.array(), 0, length);

                // Alternative version
                // Read some bytes in a loop

                int count = 0;  // no of bytes returned on a read()
                int total = 0;  // total bytes read in
                long t0 = 0;
                long t1 = 0;

                while (total < length) {

                    if (Verbose.level >= 2) {
                        System.err.println("Reading " + (length - total) + " @ [" + total + "]");
                    }

                    t0 = System.nanoTime();

                    count = bin.read(buffer.array(), total, length - total);
                    
                    total += count;

                    t1 = System.nanoTime();

                    if (Verbose.level >= 2) {
                        System.err.printf("%1.6f Received %d / %d\n", (((float)(t1 - t0)) / (float)1000000), count, total);
                    }
                }
                

                // prepare the ByteBuffer
                buffer.limit(length);
                buffer.rewind();

		// now notify the receiver with the buffer
                // by putting the buffer on a queue
                packetQueue.put(buffer);

                chunk++;

            } catch (InterruptedException ie) {
                if (Verbose.level >= 2) {
                    System.err.println("InterruptedException " + ie);
                }
            } catch (EOFException ee) {
                eof = true;
                running = false;
            } catch (IOException ioe) {
                if (Verbose.level >= 2) {
                    System.err.println("IOException " + ioe);
                }
            }
        }

        stop();
    }

    /**
     * Is the receiver running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Has the receiver reached EOF
     */
    public boolean isEOF() {
        return eof;
    }

}
