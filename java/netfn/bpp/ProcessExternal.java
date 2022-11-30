package netfn.bpp;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import netfn.httpd.NanoHTTPD;

/**
 * An example of subclassing NanoHTTPD to make a custom HTTP server.
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
        System.err.println(method + " '" + uri + "' " + params);

        Response response = null;

        if (uri.equals("/BW")) {
            String msg = null;

            // get param "bw"
            String value = params.getProperty("bw");


            System.err.println("Param value = " + value);

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
    
        } else {
            return super.serve(uri, method, header, params, files);
        }
    }
}
