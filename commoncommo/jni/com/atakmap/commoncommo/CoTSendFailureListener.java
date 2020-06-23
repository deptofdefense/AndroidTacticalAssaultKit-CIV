package com.atakmap.commoncommo;



/**
 * Interface that can be implemented and registered with a Commo instance
 * to indicate interest in receiving advisory notification when CoT messages
 * failed to send.  Note that only messages sent to destination contacts that 
 * use point-to-point TCP-based communication are able to detect failure
 * to send.
 */
public interface CoTSendFailureListener {
    /**
     * Invoked when a CoT Message sent to a known contact was unable
     * to be sent for some reason. This callback is advisory in nature;
     * it is not intended to be used to definitively track the delivery status
     * of all messages in all cases as some contacts use network technologies
     * that do not allow for detection of errors.
     * 
     * @param host the contact's known hostname to where the message could
     *             not be delivered.
     * @param port the contact's known port to where the message could not be
     *             delivered
     * @param errorReason an advisory error or reason message as to what 
     *             happened to cause the delivery to fail 
     */
    public void sendCoTFailure(String host, int port, String errorReason);
}


