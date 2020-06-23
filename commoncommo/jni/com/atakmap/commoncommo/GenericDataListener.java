package com.atakmap.commoncommo;

/**
 * Interface that can be implemented and registered with a Commo instance
 * to indicate interest in receiving any messages received
 * on inbound interfaces flagged for generic data.
 */
public interface GenericDataListener {
    /**
     * Invoked when data has been received on a generic inbound interface. 
     * The data is provided as-is, no modification or validity checking
     * is performed.
     * 
     * @param data the data that was received
     * @param rxEndpointId identifier of NetworkInterface upon which
     *                     the message was received, if known, or null
     *                     if not known
     */
    public void genericDataReceived(byte[] data, String rxEndpointId);
}
