package com.atakmap.commoncommo;

/**
 * A bundle of information updating status of an ongoing simple file transfer.
 * See the SimpleFileIO interface and Commo's simpleFileTransferInit() function.
 */
public class SimpleFileIOUpdate {
    /**
     * identifier that uniquely identifies the transfer - matches
     * the id returned from Commo's simpleFileTransferInit() function.
     */
    public final int transferId;
    /**
     * The status of the transfer. If the status is INPROGRESS, future
     * updates will be forthcoming.  Any other status code indicates
     * (failed or successful) completion of the requested transfer.
     */
    public final SimpleFileIOStatus status;
    /**
     * Additional information about the status, suitable for logging
     * or detailed error display to the user. This may be null if no
     * additional information is available.
     */
    public final String additionalInfo;
    /**
     * The number of bytes transferred so far.
     */
    public final long bytesTransferred;
    /**
     * The total number of bytes expected over the course of the entire
     * transfer.  NOTE: May be zero for download transfers if the server
     * does not provide this information or if otherwise unknown at the time
     * of this status update!!
     */
    public final long totalBytesToTransfer;
    

    SimpleFileIOUpdate(int transferId, SimpleFileIOStatus status,
                                       String info,
                                       long bytesTransferred,
                                       long totalBytes)
    {
        this.transferId = transferId;
        this.status = status;
        this.additionalInfo = info;
        this.bytesTransferred = bytesTransferred;
        this.totalBytesToTransfer = totalBytes;
    }
    
}
