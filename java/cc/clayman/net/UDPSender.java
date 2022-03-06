// UDPSender.java
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


/**
 * Take a payload as byte[] and send them over the network using UDP as a transport.
 */
public class UDPSender implements Runnable {

    // Host
    String host;

    // Port
    int port;

    // InetAddress of host
    InetAddress inetAddr;

    // socket
    DatagramSocket socket;

    // isConnected
    boolean isConnected;

    // counter
    int outCounter = 0;
    int seqNo = 0;
    
    // eof
    boolean eof = false;

    // A queue of DatagramPackets
    LinkedBlockingQueue<DatagramPacket> packetQueue = new LinkedBlockingQueue<DatagramPacket>();

    // The Thread
    Thread myThread;

    // are we running
    boolean running = false;


    /**
     * A UDPSender needs a host and port for the end point.
     */
    public UDPSender(String host, int port) throws UnknownHostException, IOException {
        this.host = host;
        this.port = port;
        isConnected = false;

    }

    /**
     * Connect
     */
    protected boolean connect() throws IOException {
        if (isConnected) {
            throw new IOException("Cannot connect again to: " + socket);
        } else {
            //socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), 0));
            socket = new DatagramSocket();

            if (host.equals("localhost")) {
                socket.connect(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), port));
                inetAddr = InetAddress.getLocalHost();
            } else {
                socket.connect(new InetSocketAddress(InetAddress.getByName(host), port));
                inetAddr = InetAddress.getByName(host);
            }

            System.err.println("UDPSender connect " +  socket.getLocalAddress() + ":" + socket.getLocalPort() + " to " + socket.getInetAddress() + ":" + socket.getPort());
            isConnected = true;
            return true;
        }
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
     * Start the sender
     */
    public boolean start() {
        try {
            // connect to the network
            connect();
        
            // Run in Thread
            myThread = new Thread(this);
            myThread.start();

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Stop the sender
     */
    public boolean stop() {
        try {
            // close the socket
            close();
            
            running = false;
            myThread.interrupt();

            return true;

        } catch (Exception e) {
            return false;
        }

    }

    /**
     * Get the remote host.
     */
    public InetAddress getRemoteHost() {
        return inetAddr;
    }

    /**
     * Get the port no.
     */
    public int getRemotePort() {
        return port;
    }

    /**
     * Get the port no.
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the Socket.
     */
    public DatagramSocket getSocket() {
        return socket;
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
                // get the next packet off the queue
                DatagramPacket packet = packetQueue.take();
            
                transmitDatagram(packet);
            } catch (InterruptedException ie) {
            } catch (IOException ioe) {
            }
        }
    }
    

    /**
     * 
     * @return -1 if something goes wrong
     * @return 0 if there is no delegate or no data plane
     */
    public int sendPayload(byte[] recvArray) {

        try {
            // Create a DatagramPacket
            // Set inetAddr and port
            // Although we did a connect(), some platforms don't seem to do it properly.
            //DatagramPacket packet = new DatagramPacket(recvArray, recvArray.length, inetAddr, port);
            DatagramPacket packet = new DatagramPacket(recvArray, recvArray.length);
            
            // add the DatagramPacket to the queue
            packetQueue.put(packet);

            // increase seqNo for next message
            seqNo++;

            return 1;
        } catch (InterruptedException ie) {
            System.err.println("Can't add DatagramPacket " + (seqNo+1) + " to queue");
            return 0;
        }
    }
    
    /**
     * Send a Packet with the specified packer
     */
    protected boolean transmitDatagram(DatagramPacket packet) throws IOException {

        

        if (! socket.isClosed()) {

            try {

                // send it
                socket.send(packet);
                outCounter++;

                //System.err.print("+");

                return true;
            } catch (java.net.PortUnreachableException pue) {

                System.err.println("Socket to " + host + ":" + port + " PORT unreachable");

                return false;
            }

        } else {
            System.err.println("Socket to " + host + ":" + port + " ALREADY CLOSED -- channel is closed");

            return false;
        }
    }


    /**
     * TO String
     */
    @Override
    public String toString() {
        if (socket == null) {
            return host + ":" + port + " (no socket)";
        } else {
            return host + ":" + port + (socket.isConnected() ? " (connected)" : " (NOT connected)");
        }
    }

}
