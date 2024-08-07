package netfn.bpp;

import netfn.mgmt.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.regex.*;

import cc.clayman.util.Verbose;

// A main() wrapper for BPPForwarder
public class BPPForward {

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
    static BPPForwarder forwarder = null;

    // The BPPFn
    static BPPFn bppFn = null;
    
    
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

                } else if (arg0.startsWith("-B")) {
                    // BPP Bandwidth evaluators
                    
                    if (arg0.equals("-Bb")) {
                        // Use Basic Bandwidth utilization evaluator
                        bppFn = new BPPBasicBandwidth(bandwidthBits);

                        System.err.println("BPPBasicBandwidth " + bandwidthBits);

                    } else if (arg0.startsWith("-Bn")) {
                        // Use No Trim Bandwidth utilization evaluator

                        if (arg0.equals("-Bn")) {
                            // no period passed in
                            bppFn = new BPPNoTrimBandwidth(bandwidthBits);

                            System.err.println("BPPNoTrimBandwidth " + bandwidthBits);
                        } else {
                            // check if arg looks like -Bn:period
                            
                            // we need to split the arg and grab the period
                            String regexp = "-Bn:(0.\\d+)";
                            Pattern pattern = Pattern.compile(regexp);
                            Matcher matcher = pattern.matcher(arg0);
                        
                            if (matcher.matches()) {
                                String group1 = matcher.group(1);

                                float period = Float.parseFloat(group1);

                                
                                bppFn = new BPPNoTrimBandwidth(bandwidthBits, period);

                                System.err.println("BPPNoTrimBandwidth " + bandwidthBits + " " + period);
                                

                            } else {
                                System.err.println("BPPNoTrimBandwidth: illegal arg. Expected -Bn:period e.g. -Bn:0.2");
                                System.exit(1);
                            }

                        }

                    } else if (arg0.startsWith("-Bo")) {
                        // Use Optimistic Bandwidth utilization evaluator

                        // check if arg looks like -Bo:period:extra
                        
                        // we need to split the arg and grab the period
                        String regexp = "-Bo:(0.\\d+):(0.\\d+)";
                        Pattern pattern = Pattern.compile(regexp);
                        Matcher matcher = pattern.matcher(arg0);
                        
                        if (matcher.matches()) {
                            String group1 = matcher.group(1);

                            float period = Float.parseFloat(group1);

                            String group2 = matcher.group(2);

                            float extra = Float.parseFloat(group2);

                                
                            bppFn = new BPPOptimisticBandwidth(bandwidthBits, period, extra);

                            System.err.println("BPPOptimisticBandwidth " + bandwidthBits + " " + period + " " + extra);
                                

                        } else {
                            System.err.println("BPPOptimisticBandwidth: illegal arg. Expected -Bo:period:extra e.g. -Bn:0.2:0.1");
                            System.exit(1);
                        }

                        

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
            forwarder = new BPPForwarder(udpPort, forwardHost, forwardPort, bandwidthBits);

            // Set the bandwidth of the BPPFn
            bppFn.setBandwidth(bandwidthBits);

            // Set BPPFn into Forwarder
            forwarder.setBPPFn(bppFn);


            // Create an HTTP hander object.
            // It will callback to the BPPForwarder as a  ManagementListener
            ProcessExternal handler = new ProcessExternal(httpPort, forwarder);

            // go
            forwarder.processTraffic();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static void usage() {
        System.err.println("BPPForward [-b bandwidth] [-p listen_port] [-h forward_host] [-P forward_port] [-H http_port]");
        System.exit(1);
    }


}
