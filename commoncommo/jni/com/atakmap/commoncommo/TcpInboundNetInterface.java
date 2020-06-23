package com.atakmap.commoncommo;

/**
 * Class representing a tcp-based listening NetInterface.
 * TCP interfaces listen on all local physical devices and are
 * unique by local listening port number.
 */
public class TcpInboundNetInterface extends NetInterface {
    private final int localPort;

    TcpInboundNetInterface(long nativePtr, int localPort)
    {
        super(nativePtr);
        this.localPort = localPort;
    }

    /**
     * Returns the local port in use by this TcpInboundNetInterface
     */    
    public int getPort()
    {
        return localPort;
    }
}
