package com.atakmap.commoncommo;

/**
 * Interface that can be implemented and registered with a Commo instance
 * to indicate interest in receiving any CoT messages received
 * on any non-generic inbound interfaces.
 */
public interface CoTMessageListener {
    /**
     * Invoked when a CoT Message has been received.  The message
     * is provided without modification. Some basic validity checking
     * is done prior to passing it off to listeners, but it is limited
     * and should not be relied upon for anything specific.
     * 
     * @param cotMessage the CoT message that was received
     * @param rxEndpointId identifier of NetworkInterface upon which
     *                     the message was received, if known, or null
     *                     if not known
     */
    public void cotMessageReceived(String cotMessage, String rxEndpointId);
}
