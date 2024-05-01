package netfn.mgmt;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import netfn.httpd.NanoHTTPD;
import cc.clayman.util.Verbose;

/**
 * An example of subclassing NanoHTTPD to make a custom HTTP server.
 * Usage: curl http://localhost:9090/BW?bw=1.2
 * or curl http://localhost:9090/BW
 * or curl http://localhost:9090/BW?bits=1258290
 * or curl http://localhost:9090/BW?bytes=157286
 */

// This will process external calls via REST
public class ProcessExternal extends NanoHTTPD {

    // The HTTP port
    int httpPort = 0;

    // The ManagementListener
    ManagementListener listener = null;

    public ProcessExternal(int httpPort, ManagementListener listener)  throws IOException  {
        super(httpPort);
        this.httpPort = httpPort;
        this.listener = listener;
    }

    /**
     * Serve up a URL.
     */
    @Override
    public Response serve(String uri, String method, Properties header, Properties params, Properties files) {
        if (Verbose.level >= 2) {
            System.err.println(method + " '" + uri + "' " + params);
        }

        Response response = null;

        if (uri.equals("/BW")) {
            // Bandwidth handler
            String msg = "";

            // get param "bw"
            String value_bw = params.getProperty("bw");
            String value_bits = params.getProperty("bits");
            String value_bytes = params.getProperty("bytes");


            if (Verbose.level >= 2) {
                if (! no_value(value_bw)) {
                    System.err.println("Param value bw = " + value_bw);
                }
                if (! no_value(value_bits)) {
                    System.err.println("Param value bits = " + value_bits);
                }
                if (! no_value(value_bytes)) {
                    System.err.println("Param value bytes = " + value_bytes);
                }
            }

            if (no_value(value_bw) && no_value(value_bits) && no_value(value_bytes)) {
                // no arg with new value
                msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
            } else {

                // process arg
                // try value_bw first

                if (! no_value(value_bw)) {
                    float bandwidth = Float.parseFloat(value_bw);

                    int oldBW = listener.adjustBandwidth(convMbpsToBps(bandwidth));

                    msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
                } else if (! no_value(value_bits)) {
                    int bandwidth = Integer.parseInt(value_bits);

                    int oldBW = listener.adjustBandwidth(bandwidth);

                    msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
                } else if (! no_value(value_bytes)) {
                    int bandwidth = Integer.parseInt(value_bytes);

                    int oldBW = listener.adjustBandwidth(convBytesSecToBps(bandwidth));

                    msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
                }
                    
            }

            response = new NanoHTTPD.Response(HTTP_OK, MIME_HTML, msg);

            return response;
    
        } else if (uri.equals("/TH")) {
            // Threshold handler
            String msg = "";

            // get param "th"
            String value = params.getProperty("th");


            if (Verbose.level >= 2) {
                System.err.println("Param value = " + value);
            }

            if (value == null || value.equals("")) {
                // no new arg 
                msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
            } else {
                // process arg
                int threshold = Integer.parseInt(value);

                //int oldTH = listener.adjustThreshold(threshold);

                //msg = "Current threshold: " + listener.getThreshold() + "\n";
            }

            response = new NanoHTTPD.Response(HTTP_OK, MIME_HTML, msg);

            return response;
    
        } else {
            return super.serve(uri, method, header, params, files);
        }
    }


    private boolean no_value(String val) {
        if (val == null || val.equals("")) {
            return true;
        } else {
            return false;
        }
    }


    // set the bandwidthBits
    // passed in as Mbps
    //
    // convert float 0.8 Mbps -> 838860 bits
    private int convMbpsToBps(float mbps) {
        int bandwidthBits = (int)(mbps * 1024 * 1024);

        return bandwidthBits;
    }

    // set the bandwidthBits
    // passed in as bytes / sec
    //
    // convert int 157286 to int 1258290
    private int convBytesSecToBps(int bytes_sec) {
        int bandwidthBits = bytes_sec * 8;

        return bandwidthBits;
    }
}
