// UDPReceiver.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.net;

import java.io.IOException;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import cc.clayman.net.IP;
import cc.clayman.util.Verbose;

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
    int length = -1;


    /*
     * How many times did no packet arrive
     */
    int noPacketCount = 0;

    /*
     * After NoTrafficThreshold packets, go back to waiting
     */
    int NoTrafficThreshold = 10;

    /*
     * The source port of the last packet received
     */
    int srcPort;

    // A queue of DatagramPackets
    LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<DatagramPacket>();


    // Caller thread
    Thread caller;


    protected UDPReceiver() {
        // for subclasses
    }
    
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
        if (! socket.isClosed()) {
            try {
                eof = true;
                socket.close();
            } catch (Exception ioe) {
                throw new Error("Socket: " + socket + " can't close");
            }
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
        DatagramPacket packet = null;

        caller = Thread.currentThread();
        
        // called getPacket() when not running
        // so nothing to do
        if (! running) {
            System.err.println("UDPReceiver: getPacket() not running");
            return null;
        }
        

        // If EOF and queue is empty
        // so nothing to do
        if (eof && packetQueue.size() == 0) {
            return null;
        } else {
            // check if we get a packet

            // Loop around getPacketInner() a number of times
            // After NoTrafficThreshold attempts we decide there is nothing
            while (!eof) {
                // call getPacketInner()
                packet = getPacketInner();
                    
                if (packet == null) {
                    // poll timed out
                    noPacketCount++;

                    // Did we get to the NoTrafficThreshold
                    if (noPacketCount == NoTrafficThreshold) {
                        if (Verbose.level >= 2) {
                            System.err.println("UDPReceiver: no packet after 200ms " + noPacketCount + " times");
                        }

                        // reset length for start up / no traffic condition
                        length = -1;

                        return null;
                    } else {
                        // We didn;t get to NoTrafficThreshold
                        // so we try again
                        if (Verbose.level >= 2) {
                            System.err.println("UDPReceiver: packet = null -- no packet after 200ms " + noPacketCount + " times");
                        }
                
                        continue;
                    }
                } else {
                    // we got a packet
                    noPacketCount = 0;

                    //System.err.println("UDPReceiver: packet recvd -- no packet after 200ms " + noPacketCount + " times");
                    return packet;

                }
            }

            return null;

        }
    }

    
    /**
     * Get a DatagramPacket from the receiver
     */
    protected DatagramPacket getPacketInner() {

        if (eof && packetQueue.size() == 0) {
            return null;
        } else {
            try {
                // get the next packet off the queue
                DatagramPacket packet;

                if (length == -1) {
                    // start up / no traffic condition
                    // we just sit and wait until something arrives
                    packet = packetQueue.take();
                    noPacketCount = 0;
                    
                } else {
                    // traffic flowing condition
                    // we wait 200ms before we decide there's no traffic
                    packet = packetQueue.poll(200L, TimeUnit.MILLISECONDS);

                }

                return packet;

            } catch (InterruptedException ie) {
                if (Verbose.level >= 2) {
                    System.err.println("UDPReceiver: InterruptedException " + ie);
                }
                return null;
            }
        }
    }
    
    /**
     * The main run loop.
     * It receives a DatagramPacket  off the network 
     * which is put on the packetQueue
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
                if (Verbose.level >= 2) {
                    System.err.println("InterruptedException " + ie);
                    //ie.printStackTrace();
                }

                running = false;

            } catch (EOFException ee) {
                if (Verbose.level >= 2) {
                    System.err.println("EOFException " + ee);
                    //ee.printStackTrace();
                }
                
                eof = true;
                running = false;

            } catch (IOException ioe) {
                if (running) {
                    if (Verbose.level >= 2) {
                        System.err.println("IOException " + ioe);
                    }
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
