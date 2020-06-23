
package com.atakmap.comms;

import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Comms logger allows for the logging of the low level cursor on target messages within 
 * the system.   Please be warned that the implementation of this logger will cause performance
 * degradation within the system.   This should not be used to intercept message for purposes
 * other than metrics and debugging.
 * Usage in any other way is not RECOMMENDED
 */
public interface CommsLogger {

    /**
     * Logs a message being sent to a specific destination.  
     * @param msg the message to be sent
     * @param destination the destination address of the message for messages not sent to a 
     * contact.
     */
    void logSend(CotEvent msg, String destination);

    /**
     * Logs a message being sent to one or more contacts
     * @param msg the message to be sent
     * @param toUIDs the destination the message for contacts in the system.
     */
    void logSend(CotEvent msg, String[] toUIDs);

    /**
     * Logs a message comming in from a rxid (uid) and optionally from a 
     * specific tak server.
     * @param msg the message that was received.
     * @param rxid the receive id, can be null.
     * @param server the server information, can be null.
     */
    void logReceive(CotEvent msg, String rxid, String server);

    /**
     * Signals the logger to clean up. 
     */
    void dispose();

}
