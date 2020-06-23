package com.atakmap.commoncommo;

import java.io.File;

/**
 * A bundle of information about an ongoing or just completed
 * inbound Mission Package transfer.
 */
public class MissionPackageReceiveStatusUpdate {
    /**
     * The local file identifying which transfer the update pertains to.
     * Will match a File previously returned in a call to MissionPackageIO's
     * missionPackageReceiveInit()
     */
    public final File localFile;

    /**
     * The current status of the transfer. 
     * Can be one of:
     * FINISHED_SUCCESS
     * FINISHED_FAILED
     * ATTEMPT_IN_PROGRESS
     * ATTEMPT_FAILED
     * 
     * The FINISHED_* status codes indicate this will be the final update
     * for this MP transfer.
     * The ATTEMPT_* codes indicate that another status report will be
     * forthcoming.
     */
    public final MissionPackageTransferStatus status;
    
    /**
     * Total number of bytes received <b>in this attempt</b>.
     */
    public final long totalBytesReceived;

    /**
     * Total number of bytes expected to be received to complete the transfer.
     * Note: this number is provided by the sender and may not be accurate!
     * If the sender does not provide the information, it will be reported as
     * zero (0).  Because of these limitations, totalBytesReceived
     * could exceed this number!
     * This number will remain the same in all status updates for a
     * given transfer.
     */
    public final long totalBytesExpected;
    
    /**
     * The current download attempt, numbered stating at 1, and less than or
     * equal to "maxAttempts". For ATTEMPT_FAILED updates, this is the
     * attempt number that failed. For FINISHED_* updates, this is the attempt
     * number that caused the final status update.
     */
    public final int attempt;
    
    /**
     * The total number of attempts that will be made to download the 
     * Mission Package.  This will be constant for all updates for a given
     * transfer.
     */
    public final int maxAttempts;
    
    /**
     * a textual description of why the transfer failed.
     * This is only non-null when status is FINISHED_FAILED or ATTEMPT_FAILED
     */
    public final String errorDetail;



    MissionPackageReceiveStatusUpdate(File file,
                                      MissionPackageTransferStatus status,
                                      long bytesReceived,
                                      long bytesExpected,
                                      int attempt,
                                      int maxAttempts,
                                      String errorDetail)
    {
        this.localFile = file;
        this.status = status;
        this.totalBytesReceived = bytesReceived;
        this.totalBytesExpected = bytesExpected;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.errorDetail = errorDetail;
    }
    
}
