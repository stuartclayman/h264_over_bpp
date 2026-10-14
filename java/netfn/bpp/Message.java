package netfn.bpp;


public final class Message {
    // Level for status messages
    public static int status = 0;

    // Values for status
    public static  final int PROCESS_DATAGRAM = 0x001;      // For every call to datagramProcess()
    public static final int BPPFN_PROCESS = 0x002;          // For every call to BPPFn.process()
    public static final int BPPFN_SECOND = 0x004;           // A message only per second in BPPFn.process()
}
