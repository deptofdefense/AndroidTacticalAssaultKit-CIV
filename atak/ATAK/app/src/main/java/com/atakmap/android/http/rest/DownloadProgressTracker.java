
package com.atakmap.android.http.rest;

/**
 * Track ongoing download progress including percentage, smoothed average throughput, and estimated
 * time remaining Assume that invokers periodically notify the user somehow, which starts a new
 * "block" for tracking purposes
 * 
 * 
 */
public class DownloadProgressTracker {

    private static final String TAG = "DownloadProgressTracker";

    /**
     * Amount of change in progress percent before notifying user
     */
    private static final int PROGRESS_NOTIFICATION_DELTA = 7;

    /**
     * Max amount of time before notifying to user: 3 seconds
     */
    private static final int TIME_NOTIFICATION_DELTA = 3000;

    /**
     * Valid range [0,1] Exponential moving average for download speed. Use a high smoothing factor
     * to weight most recent updates most b/c we only update periodically and network may be very
     * dynamic. Lower number for smoother time estimates
     */
    private static final double SMOOTHING_FACTOR = 0.6;

    /**
     * Current and last reported progress Percentage
     */
    int currentProgress = 0, lastProgress = 0;

    /**
     * Content length received so far, length received in the current block Number of bytes
     */
    long currentLength = 0, currentBlockLength = 0;

    /**
     * Estimated millis remaining in this download, time last notified user Millis
     */
    long timeRemainingMillis = 0, lastUpdateMillis;

    /**
     * Speed of the current block, smoothed average speed of the entire download bytes per millis
     */
    double lastSpeedBPM = -1, averageSpeedBPM = -1;

    /**
     * Total size of download Number of bytes
     */
    double totalLength;

    /**
     * Flag that at least one byte was successfully downloaded
     */
    boolean bProgressMade = false;

    /**
     * @param totalLength the total length expected
     */
    public DownloadProgressTracker(double totalLength) {
        this.totalLength = totalLength > 0 ? totalLength : 1D; // don't divide by 0
        this.lastUpdateMillis = System.currentTimeMillis();
    }

    /**
     * @return true if we should update/notify user
     */
    public boolean contentReceived(long totalBytesSoFar, long totalLen,
            long currentTime) {
        this.totalLength = totalLen > 0 ? totalLen : 1D;
        int delta = (int) (totalBytesSoFar - currentLength);
        return contentReceived(delta, currentTime);
    }

    /**
     * Update internal state for amount of data received If conditions are met for starting a new
     * block, then additional internal state is updated
     * 
     * @param len the length of the content received
     * @param currentTime the current time in milliseconds
     * @return true if we should update/notify user
     */
    public boolean contentReceived(int len, long currentTime) {
        // update length and percentage
        currentLength += len;
        currentBlockLength += len;
        currentProgress = (int) Math.round((double) currentLength / totalLength
                * 100D);

        // we got some data, set the flag so retry count can be reset
        if (!bProgressMade) {
            // Log.d(TAG, "Setting progress flag");
            bProgressMade = true;
        }

        // do we know total size? required for full progress reporting...
        if (totalLength <= 1) {
            // just notify based on time...
            return currentTime - lastUpdateMillis > TIME_NOTIFICATION_DELTA;
        }

        // see if time to notify user and start new block
        if (currentProgress - lastProgress >= PROGRESS_NOTIFICATION_DELTA
                || currentTime - lastUpdateMillis > TIME_NOTIFICATION_DELTA) {

            // get speed in bytes per millis for last block
            lastSpeedBPM = currentBlockLength
                    / (float) Math.max(1L, (currentTime - lastUpdateMillis));

            // calculate average speed
            if (averageSpeedBPM < 0)
                averageSpeedBPM = lastSpeedBPM;
            else {
                // use exponential moving average
                averageSpeedBPM = SMOOTHING_FACTOR * lastSpeedBPM
                        + (1 - SMOOTHING_FACTOR)
                                * averageSpeedBPM;
            }

            // estimate time remaining
            timeRemainingMillis = Math.round((totalLength - currentLength)
                    / averageSpeedBPM);

            return true;
        }

        // not starting a new block
        return false;
    }

    /**
     * User has been notified, finish updating internal state to start new block
     * 
     * @param currentTime the current time for the notification in millis since epoch
     */
    public void notified(long currentTime) {
        lastProgress = currentProgress;
        currentBlockLength = 0;
        lastUpdateMillis = currentTime;
    }

    public boolean isProgressMade() {
        return bProgressMade;
    }

    /**
     * Upon error, clear progress flag (e.g. retry counter should not restart)
     */
    public void error() {
        bProgressMade = false;
    }

    /**
     * Percentage
     * 
     * @return the percentage as an integer between 0 and 100.
     */
    public int getCurrentProgress() {
        return currentProgress;
    }

    /**
     * Bytes per Millisecond
     * 
     * @return the bytes per millisecond
     */
    public double getAverageSpeed() {
        return averageSpeedBPM;
    }

    /**
     * Milliseconds (estimated)
     * 
     * @return the number of milliseconds left in the transfer
     */
    public long getTimeRemaining() {
        return timeRemainingMillis;
    }

    /**
     * Sets the current length of the download
     * @param length the current length in bytes
     */
    public void setCurrentLength(long length) {
        currentLength = length;
    }

    /**
     * Returns the current set length of the download
     * @return the length in bytes
     */
    public long getCurrentLength() {
        return currentLength;
    }
}
