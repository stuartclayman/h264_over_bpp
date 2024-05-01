package netfn.udp;

import netfn.mgmt.*;

import java.io.IOException;
import java.net.InetAddress;

import cc.clayman.util.Verbose;

// A main() wrapper for UDPForwarder
public class UDPForward {

    // listen port
    static int udpPort = 6799;

    // forward host
    static String forwardHost = "localhost";
    // forward port
    static int forwardPort = 6798;


    // HTTP Listen Port for ManagementListener
    static int httpPort = 8080;

    
    static int columns = 80;    // default no of cols on terminal

    // in mega-bits
    static int bandwidthBits = 1 * 1024 * 1024;   // default: 1 Mb
    static int packetsPerSecond = 100;  // default: 100

    // Forwarder
    static UDPForwarder forwarder = null;
    
    
    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
        } else if (args.length >= 1) {
            
            // have flags too

            int argc = 0;

            while (argc < args.length) {  // allow for port at end
                String arg0 = args[argc];

                if (arg0.equals("-p")) {
                    // Port
                    argc++;

                    String val = args[argc];
                    udpPort = Integer.parseInt(val);

                } else if (arg0.equals("-h")) {
                    // Host
                    argc++;
                    forwardHost = args[argc];

                } else if (arg0.equals("-P")) {
                    // Forward Port
                    argc++;

                    String val = args[argc];
                    forwardPort = Integer.parseInt(val);

                } else if (arg0.equals("-H")) {
                    // HTTP Listen Port
                    argc++;

                    String val = args[argc];
                    httpPort = Integer.parseInt(val);

                } else if (arg0.equals("-c")) {            
                    // columns
                    argc++;

                    String val = args[argc];
                    columns = Integer.parseInt(val);

                } else if (arg0.equals("-b")) {   /* -b bandwidthBits -- the estimated bandwidth (in Mbits) e.g 0.8 or 1.2 */
                    // get next arg
                    argc++;

                    String countValue =  args[argc];

                    try {
                        float mbps = Float.parseFloat(countValue);
                        bandwidthBits =  (int)(mbps * 1024 * 1024);
                    } catch (Exception e) {
                        System.err.println("Bad bandwidth value: " + countValue);
                    }

                 } else if (arg0.equals("-r")) {   /* -r rate -- the no of packets per second */
                    // get next arg
                    argc++;

                    String countValue =  args[argc];

                    try {
                        packetsPerSecond = Integer.parseInt(countValue);
                    } catch (Exception e) {
                        System.err.println("Bad packets per second " + countValue);
                    }

                } else if (arg0.startsWith("-v")) {
                    if (arg0.equals("-v")) {
                        Verbose.level = 1;
                    } else  if (arg0.equals("-vv")) {
                        Verbose.level = 2;
                    } else  if (arg0.equals("-vvv")) {
                        Verbose.level = 3;
                    }
                } else {
                    usage();
                }

                argc++;

            }

        } else {
            usage();
        }

        if (Verbose.level >= 2) {
            System.err.println("Listen on port: " + udpPort);
            System.err.println("Forward to host: " + forwardHost);
            System.err.println("Forward to port: " + forwardPort);
            System.err.println("HTTP listen port: " + httpPort);
            //System.err.println("Columns: " + columns);
            System.err.println("Bandwidth in bits: " + bandwidthBits);
            System.err.println("Packets per second: " + packetsPerSecond);
        }
        
        try {
            // Create the forwarder
            forwarder = new UDPForwarder(udpPort, forwardHost, forwardPort, bandwidthBits, packetsPerSecond);
            // Create ProcessExternal object.
            // It will callback to the UDPForwarder as a  ManagementListener
            ProcessExternal handler = new ProcessExternal(httpPort, forwarder);

            // go
            forwarder.processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("UDPForward [-b bandwidth] [-p listen_port] [-h forward_host] [-P forward_port] [-H http_port]");
        System.exit(1);
    }


}
