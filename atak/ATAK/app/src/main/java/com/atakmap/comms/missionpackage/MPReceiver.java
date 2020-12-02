
package com.atakmap.comms.missionpackage;

import java.io.File;
import java.io.IOException;

public interface MPReceiver {

    /**
     * Returns a valid, writable file to where the MP being received should be
     * written.  This must return the same value for every call made against a
     * a particular MPReceiver instance. The File will be overwritten if it
     * exists.
     * Throwing an IOException from this causes the transfer to be aborted; no
     * progress callbacks will be made, nor will receiveComplete() be called.
     * 
     * @return File to write the received MP out to
     * @throws IOException if an error occurs
     */
    File getDestinationFile() throws IOException;

    /**
     * Called by the transfer engine to indicate progress on receiving the MP.
     * 
     * @param bytesTransferred total number of bytes transferred in this download attempt
     * @param totalBytesExpected total number of bytes expected in the transfer; 
     *                           0 if unknown
     * @param attemptNum attempt number this progress is for (starting from 1) 
     * @param maxAttempts total number of attempts that may be made 
     *                    (constant for any given transfer)
     */
    void receiveProgress(long bytesTransferred, long totalBytesExpected,
            int attemptNum, int maxAttempts);

    /**
     * Called by the transfer engine to indicate a download attempt failed.
     * 
     * @param reason a description of why the transfer failed, if known, or null
     * @param attemptNum attempt number that failed (starting from 1)
     * @param maxAttempts total number of attempts that will be made
     *                    (constant for any given transfer)
     */
    void attemptFailed(String reason, int attemptNum, int maxAttempts);

    /**
     * Called by the transfer engine to indicate a download is finished and
     * no additional attempts will be made; this is called for successful
     * and unsuccessful completions, indicating no additional attempts will
     * be made!
     * For failed transfers, failReason may give more info
     * as to why, but generally speaking the per-attempt
     * error detail from attemptFailed() will be more informative.
     * 
     * @param success true if the transfer was successful, false if failed
     * @param failReason reason why the download failed, if known, else
     *                   null
     * @param attempt the attempt the download completed on
     */
    void receiveComplete(boolean success, String failReason,
            int attempt);

}
