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
            String value = params.getProperty("bw");


            if (Verbose.level >= 2) {
                System.err.println("Param value = " + value);
            }

            if (value == null || value.equals("")) {
                // no new arg 
                msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
            } else {
                // process arg
                float bandwidth = Float.parseFloat(value);

                int oldBW = listener.adjustBandwidth(bandwidth);

                msg = "Current bandwidth: " + listener.getBandwidth() + "\n";
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
}
