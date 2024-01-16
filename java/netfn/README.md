## Network Functions

This package provides 2 forwarders:

- the BPP forwarder, which inspects BPP packets, and might trim some content if the bandwidth is insufficient, and forwards the packets
- the UDP forwarder, which takes in UDP packets and drops some if the bandwidth is too small

## Useful interations


**BPPSend  -->  BPPForward --> BPPListen**   _Prints out BPP received info, factoring in any trimming by the BPP forward function._

**BPPSend  -->  BPPForward -->  H264Listen**  _Reconstructs H264 video from BPP packets,
and outputs to a file. This factors in any trimming by the BPP forward function. All chunks are post-processed, and any lost
chunks will cause the containing NAL to not be written out to the file._ 

**UDPSend  -->  UDPForward -->  UDPListen**   _Outputs data from UDP packets into a
file. All received packets are written directly to the file. Loss from the UDPForward function will
cause holes in the H264 stream._


## Usage

Here are the arguments for these functions.

### BPP Forwarder


`java netfn.bpp.BPPForward -b 1.8`

BPPForward [-b bandwidth] [-p listenPort] [-h forwardHost] [-P forwardPort] [-H httpPort]  
_Listen for BPP packets and forward them_  
**-b** bandwidth The bandwidth of the outbound connection (in Mbps). Default: 1  
**-p** port Listen port.  Default: 6799  
**-h** host Host to forward to.  Default: localhost  
**-P** port Port to forward to.  Default: 6798  
**-H** port HTTP listen port.  Default: 8080  





### UDP Forwarder


`java netfn.udp.UDPForward -b 1.8`

UDPForward [-b bandwidth] [-p listenPort] [-h forwardHost] [-P forwardPort] [-H httpPort]  
_Listen for BPP packets and forward them_  
**-b** bandwidth The bandwidth of the outbound connection (in Mbps). Default: 1  
**-p** port Listen port.  Default: 6799  
**-h** host Host to forward to.  Default: localhost  
**-P** port Port to forward to.  Default: 6798  
**-H** port HTTP listen port.  Default: 8080  



---

All commands support verbose output, at different levels:  
**-v** Verbose level 1  
**-vv** Verbose level 2  
**-vvv** Verbose level 3  

## Algorithm

The *Packet Trimming Algorithm* used in the BPP forwarder is described in the paper 
**Using packet trimming at the edge for in-network video quality
adaption** in
[Annals of Telecommunications](https://link.springer.com/article/10.1007/s12243-023-00981-8)


```js

// Set timeStart as current time in milliseconds
timeStart =  Clock() 
 
bytesRecvThisSecond = 0 ; bytesSentThisSecond = 0 
  

ForEach (packet received) {
  /* Timing */

  // Get current time in milliseconds
  now = Clock() 
  
  // Millisecond offset between now and timeStart 
  timeOffset = now - timeStart 
  
  // What is the offset in this second (as floating point)
  secondOffset = timeOffset /  1000 


  /*Pre processing of packet */

  packetLength = length(packet)

  bytesRecvThisSecond += packetLength
  
  // The ideal no of bytes to send at this offset into a second 
  idealSendThisSec = availableBandwidth * secondOffset

  // How far below the ideal are we
  below = idealSendThisSec - bytesSentThisSecond

  /* Check current second */
  
  if (timeOffset >= 1000) {
     // Crossed a second boundary. Reset variables for new second
     timeStart = now
     
     secondOffset = 0

     bytesRecvThisSecond = 0 ; bytesSentThisSecond = 0
  }

  /* Decision making and forwarding */
  
  if (below > 0) {
    // Below ideal so there is capacity so forward without trimming
    bytesSentThisSecond  += packetLength

    forward(packet)
  } else {
    // We need to drop something, so trim chunks from the content
    (newPacket, droppedAmount) = trim(packet, idealSendThisSec, below)

    // droppedAmount is how much content was actually trimmed
    bytesSentThisSecond += packetLength - droppedAmount

    forward(newPacket)
  }

  
}


```
