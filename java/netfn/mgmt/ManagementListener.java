package netfn.mgmt;


/**
 * The functions supported by a Management Listener
 */
public interface ManagementListener {
    // Get the bandwidth
    public int getBandwidth();

    // Adjust the bandwidth
    // Returns the old bandwidth
    public int adjustBandwidth(int bandwidth);
}
