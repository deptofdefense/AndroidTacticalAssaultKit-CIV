package com.atakmap.commoncommo;

/**
 * Interface that can be implemented and registered with a Commo instance
 * indicate interest in knowing about changes to the status of network
 * interfaces.
 */
public interface InterfaceStatusListener {

    /**
     * Invoked when a NetInterface is active and able to send or receive data.
     * For StreamingNetInterfaces, this is when the server connection is made.
     * @param iface the NetInterface whose status changed.
     */
    public void interfaceUp(NetInterface iface);

    /**
     * Invoked when a NetInterface is no longer available and thus unable
     * to send or receive data. If it becomes available again in the future,
     * interfaceUp will be invoked with the same NetInterface as an argument.
     * For StreamingNetInterfaces, this is when the server connection is lost
     * or otherwise terminated.
     * @param iface the NetInterface whose status changed.
     */
    public void interfaceDown(NetInterface iface);
    
    /**
     * Invoked when a NetInterface makes an attempt to come online
     * but fails for any reason, or when an interface that is up is forced
     * UNEXPECTEDLY into a down state. The error code gives the reason
     * for the error.
     * A callback to this does not imply a permanent error; attempts
     * will continue to be made to bring up the interface unless it is
     * removed.
     * @param iface the NetInterface on which the error occurred
     * @param code indication of the error that occurred
     */
    public void interfaceError(NetInterface iface, NetInterfaceErrorCode code);
    
}
