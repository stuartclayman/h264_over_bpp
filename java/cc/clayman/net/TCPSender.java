// TCPSender.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Sept 2022

package cc.clayman.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import cc.clayman.util.Verbose;

/**
 * Take a payload as byte[] and send them over the network using TCP as a transport.
 *
 * The TCPSender listens on a ServerSocket for one incoming connection,
 * and then sends the bytes back to the caller.  Then it stops.
 */
public class TCPSender implements Runnable {

    // Host
    String host;

    // Port
    int port;

    // InetAddress of host
    InetAddress inetAddr;

    // socket
    ServerSocketChannel channel = null;
    ServerSocket serverSocket = null;
    SocketChannel client  = null;
    Socket socket = null;

    // isConnected
    boolean isConnected;

    // counter
    int outCounter = 0;
    int seqNo = 0;
    
    // eof
    boolean eof = false;

    // A queue of DatagramPackets
    LinkedBlockingQueue<ByteBuffer> packetQueue = new LinkedBlockingQueue<ByteBuffer>();

    // The Thread
    Thread myThread;

    // are we running
    boolean running = false;


    /**
     * A TCPSender needs a host and port for the end point.
     */
    public TCPSender(String host, int port) throws UnknownHostException, IOException {
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
            // Open a ServerSocketChannel
            channel = ServerSocketChannel.open();
            //channel.configureBlocking(true);

            // and get it's socket
            serverSocket = channel.socket();

            if (host.equals("localhost")) {
                serverSocket.bind(new InetSocketAddress(port));
                inetAddr = InetAddress.getLocalHost();
            } else {
                serverSocket.bind(new InetSocketAddress(host, port));
                inetAddr = InetAddress.getByName(host);
            }

            // Now wait for an incoming connection
            client = channel.accept();
            // and get the socket for that
            socket = client.socket();

            //System.err.println("TCPSender channel = " + channel + " serverSocket = " + serverSocket + " client = " + client + " socket = " + socket);

            //System.err.println("TCPSender connect " +  socket.getLocalAddress() + ":" + socket.getLocalPort() + " to " + socket.getInetAddress() + ":" + socket.getPort());

            
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
            serverSocket.close();
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
            // and listen for a new inbound connection
            connect();
        
            // Run in Thread
            myThread = new Thread(this);
            myThread.start();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
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
    public Socket getSocket() {
        return socket;
    }

    /**
     * The main run loop.
     * It takes a ByteBuffer off the packet queue and sends it to the socket.
     */
    public void run() {
	// if we get here the thread must be running
        running = true;
        
        while (running) {
            try {
                // get the next buffer off the queue
                ByteBuffer packet = packetQueue.take();
            
                transmitBuffer(packet);
                
            } catch (InterruptedException ie) {
                if (Verbose.level >= 2) {
                    System.err.println("InterruptedException " + ie);
                }
            } catch (IOException ioe) {
                if (Verbose.level >= 2) {
                    System.err.println("IOException " + ioe);
                }
            }
        }
    }
    
    
    /**
     * Send a Packet with the specified packet
     */
    protected boolean transmitBuffer(ByteBuffer packet) throws IOException {

        

        if (! socket.isClosed()) {

            try {

                // send it
                client.write(packet);
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
     * 
     * @return 0 if something goes wrong
     * @return 1 normally
     */
    public int sendPayload(byte[] recvArray) {

        try {
            // Create a ByteBuffer
            
            // Allocate enough for the content + 8 bytes for the header
            ByteBuffer packet = ByteBuffer.allocate(recvArray.length + 8);

            // Patch in 'CHNK' + the length of the content
            packet.put(0, (byte)'C');
            packet.put(1, (byte)'H');
            packet.put(2, (byte)'N');
            packet.put(3, (byte)'K');

            // 32 bits for size
            packet.position(4);
            packet.putInt(recvArray.length);

            // check for really big numbers
            if (recvArray.length > 65535) {
                throw new Error("TCPSender: reader created payload of size " + recvArray.length + " > expected size " + 65535);
            }

            // This works too
            //packet.put(4, (byte)(((recvArray.length & 0xFF000000) >> 24) & 0xFF));
            //packet.put(5, (byte)(((recvArray.length & 0x00FF0000) >> 16) & 0xFF));
            //packet.put(6, (byte)(((recvArray.length & 0x0000FF00) >>  8) & 0xFF));
            //packet.put(7, (byte)(((recvArray.length & 0x000000FF) >>  0) & 0xFF));

                        
            // copy in the payload
            packet.position(8);
            packet.put(recvArray, 0, recvArray.length);

            // prepare the ByteBuffer
            packet.rewind();
            
            
            // add the ByteBuffer to the queue
            packetQueue.put(packet);

            // increase seqNo for next message
            seqNo++;

            return 1;
        } catch (InterruptedException ie) {
            System.err.println("Can't add ByteBuffer " + (seqNo+1) + " to queue");
            return 0;
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
