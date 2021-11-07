// UDPReceiver.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import cc.clayman.net.IP;

/**
 * Receive a DatagramPacket over the network using UDP as a transport.
 */
public class UDPReceiver implements Runnable {
    /*
     * The socket doing the listening
     */
    DatagramSocket socket;

    /*
     * A packet to receive
     */
    DatagramPacket packet;

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
     * The InetSocketAddress of the last packet received
     */
    InetAddress srcAddr;

    /*
     * EOF status
     */
    boolean eof = false;

    /*
     * The length of the last packet received
     */
    int length;

    /*
     * The source port of the last packet received
     */
    int srcPort;

    // A queue of DatagramPackets
    LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<DatagramPacket>();


    // Caller thread
    Thread caller;
    
    /**
     * Construct a UDPReceiver.
     */
    public UDPReceiver(int port) {
	// address is explicitly set to null
	address = null;
        
        this.port = port;
    }
    
    /**
     * Construct a UDPReceiver.
     */
    public UDPReceiver(InetSocketAddress addr) {
	// Receiving address
	address = addr.getAddress();
        this.port = addr.getPort();
    }

    /**
     * Set up the socket for the given addr/port,
     * and also a pre-prepared DatagramPacket.
     */
    protected boolean connect() throws IOException {
        if (this.address == null)
            socket = new DatagramSocket(port);
        else
            socket = new DatagramSocket(port, address);

	// allocate an emtpy packet for use later
	packet = newPacket();

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
     * Create a new packet
     */
    protected DatagramPacket newPacket() {
        return new DatagramPacket(new byte[IP.BASIC_PACKET_SIZE], IP.BASIC_PACKET_SIZE);
    }




    /**
     * Get a DatagramPacket from the receiver
     */
    public DatagramPacket getPacket() {
        caller = Thread.currentThread();
        
        if (eof) {
            return null;
        } else {
            try {
                // get the next packet off the queue
                DatagramPacket packet = packetQueue.take();

                return packet;
            
            } catch (InterruptedException ie) {
                return null;
            }
        }
    }
    
    /**
     * The main run loop.
     * It takes a DatagramPacket off the packet queue and sends it to the socket.
     */
    public void run() {
	// if we get here the thread must be running
        running = true;
        
        while (running) {
            try {
                // receive from socket
                socket.receive(packet);

                srcAddr = packet.getAddress();
                length = packet.getLength();
                srcPort = packet.getPort();

                //System.err.println("Received: " + srcAddr + "/" + srcPort + " length: " + length);


		// now notify the receiver with the packet
                // by putting the packet on a queue
                packetQueue.put(packet);

                // allocate an emtpy packet for use later
                packet = newPacket();

            } catch (InterruptedException ie) {
            } catch (IOException ioe) {
            }
        }
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
