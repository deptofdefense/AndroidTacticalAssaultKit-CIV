package com.atakmap.commoncommo;


/**
 * Enumeration for status values associated with mission package
 * transfers. See MissionPackage{Send,Receive}StatusUpdate classes
 * for meanings of the codes in send and receive contexts.
 */
public enum MissionPackageTransferStatus {
    /**
     * The transfer succeeded
     */
    FINISHED_SUCCESS(0),
    /**
     * The transfer timed out
     */
    FINISHED_TIMED_OUT(1),
    /**
     * The contact disappeared from the network before the transfer
     * was completed. 
     */
    FINISHED_CONTACT_GONE(2),
    /**
     * The recipient reported an issue receiving the transfer. It is no longer
     * attempting to receive the transfer
     */
    FINISHED_FAILED(3),
    /**
     * The file exists already and no transfer is needed.
     */
    FINISHED_FILE_EXISTS(4),
    /**
     * Mission package transfers are disabled
     * locally.
     */
    FINISHED_DISABLED_LOCALLY(5),
    /**
     * A transfer attempt is in progress
     */
    ATTEMPT_IN_PROGRESS(6),
    /**
     * A transfer attempt failed, 
     * but further attempts may be forthcoming
     */
    ATTEMPT_FAILED(7),
    /**
     * A server upload is needed and will 
     * be forthcoming
     */
    SERVER_UPLOAD_PENDING(8),
    /**
     * A server upload is in progress
     */
    SERVER_UPLOAD_IN_PROGRESS(9),
    /**
     * A server upload completed successfully
     */
    SERVER_UPLOAD_SUCCESS(10),
    /**
     * A server upload failed
     */
    SERVER_UPLOAD_FAILED(11);
;
    
    private final int id;
    
    private MissionPackageTransferStatus(int id) {
        this.id = id;
    }
    
    int getNativeVal() {
        return id;
    }
}
