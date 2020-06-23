
package com.atakmap.comms.missionpackage;

import java.io.IOException;

public interface MPReceiveInitiator {

    /**
     * A new MP download request was received with the given details;  the 
     * ReceiveInitiator should set set the system up to receive the MP
     * and return an MPReceiver that will handle the download.
     * 
     * Return null if file already exists locally and system
     * does not want to receive again. Throwing IOException will
     * result in the transfer aborting.  Both of these results in a nack
     * being sent to the sender. 
     * 
     * @param fileName remote sender's preferred name for the file
     * @param transferName remote sender's logical name for the transfer
     * @param sha256hash sha256 hash of the file, as reported by the sender
     * @param expectedByteLen length of file, as reported by sender. 0 if 
     *                        not given by sender.
     * @param senderCallsign callsign of the sending device
     * @return MPReceiver instance that will handle the file receive,
     *         or null to indicate that this system already has the file
     *         and does not want to receive again.
     * @throws IOException if any error is encountered
     */
    MPReceiver initiateReceive(String fileName,
            String transferName,
            String sha256hash,
            long expectedByteLen,
            String senderCallsign) throws IOException;

}
