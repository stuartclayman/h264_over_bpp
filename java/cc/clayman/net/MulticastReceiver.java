// MluticastReceiver.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: Oct 2022

package cc.clayman.net;


import java.io.IOException;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.MulticastSocket;


/**
 * Receive a DatagramPacket over the network using IP multicast as a transport.
 */
public class MulticastReceiver extends UDPReceiver implements Runnable {

    /**
     * Construct a MulticastReceiver.
     */
    public MulticastReceiver(InetSocketAddress addr) {
	// Receiving address
	address = addr.getAddress();
        this.port = addr.getPort();
    }

    /**
     * Set up the socket for the given addr/port,
     * and also a pre-prepared DatagramPacket.
     */
    protected boolean connect() throws IOException {
        socket = new MulticastSocket(port);

        try { 
            ((MulticastSocket)socket).joinGroup(address);
        } catch (IOException ioe) {
            System.err.println("Cannot join multicast group " + address + " / " + port + " " + ioe);
            System.exit(1);
        }
        
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
                ((MulticastSocket)socket).leaveGroup(address);
            } catch (IOException ioe) {
                System.err.println("Cannot leave multicast group " + address + " / " + port + " " + ioe);
            }

            // call super
            super.close();
        }
    }

    

}

