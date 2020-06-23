package com.atakmap.commoncommo;

import java.io.File;

/**
 * Implemented by the application using the Commo API to handle various
 * facets of mission package transfers.
 * After implementing this interface, register it with a Commo interface and
 * enable mission package transfers by calling Commo.setupMissionPackageIO()
 */
public interface MissionPackageIO {

    /**
     * Signals an incoming package is ready to be received.
     * This function is invokes when an incoming file
     * request has been received and the system is ready to attempt
     * to download it.  Depending on the return status, the application
     * can decide to accept or reject the transfer, as well as provide
     * a file storage location where the file data should be transferred to.
     *
     * Implementations must either return a valid file location or
     * throw a MissionPackageTransferException to indicate what
     * alternate action is to be taken.
     *
     * Returning a valid File indicates the local system wants the file
     * to be received and written to the specified location.
     * Note that the system may write to this file even on failed transfers.
     * See more below about how transfers proceed when a File is returned
     * without exception.
     *
     * An Exception enclosing a FINISHED_FILE_EXISTS status indicates that the
     * local system already has the file to be
     * transferred on local record, and thus does not need it. 
     * This is not considered a failure per se; rather the local system
     * will attempt to notify the remote system that the file is already
     * present and a transfer is unnecessary.
     *
     * An Exception enclosing a FINISHED_DISABLED_LOCALLY status indicates
     * that file transfers are disabled locally; the transfer is  aborted.
     *
     * An Exception enclosing a FINISHED_FAILED status can be thrown for any
     * other reason that the system is not able
     * to receive the package at this time; the transfer is aborted.
     *
     * An Exception enclosing any other status is nonsensical for this
     * use and will be treated as though Failed was provided.
     *
     * If the application returns a valid File, the transfer is
     * initiated and will continue asynchronously until it is
     * completed or an error occurs.  Progress and completion
     * will be reported via calls to missionPackageReceiveStatusUpdate().
     * Completion is indicated with appropriate status codes in
     * these updates (see MissionPackageReceiveStatusUpdate).
     * All updates for this transfer will contain the same File as is
     * returned here.
     *
     * An implementation returning a File with the
     * same file path as another transfer already in progress will result
     * in the transfer being aborted (in particular, no call to
     * ReceiveStatusUpdate() will be made for this newest transfer
     * as it would be indistinguishable from the already-progressing transfer.
     *
     * It is guaranteed that this function is only called on one thread at a
     * time for any Commo implementation upon which it is installed.
     * If the controlling Commo instance is closed, outstanding transfers
     * are aborted (in particular, ReceiveStatusUpdate() is no longer
     * invoked for those transfers).
     * @param fileName this contains a string indicating the remote sender's
     *        preferred file name.  There is no requirement the implementer
     *        use this name in the derivation of any returned File.
     *        This is provided merely for informational purposes.
     * @param transferName is the remote senders "conceptual name" of the package.
     *              is provided to help identify the transfer.
     * @param sha256hash is a hex string of the sha256 hash of the file to be recei
     *            (as provided by the remote sender)
     * @param sizeInBytes expected total size of the file to be received, as
     *            provided by the sender.  Because it comes from the sender,
     *            it should not be considered to be definitive; it could be
     *            wrong or missing (0)
     * @param senderCallsign is the callsign of the remote sender
     * @throws MissionPackageTransferException to indicate a situation where
     *              the transfer is not desired or cannot be received (see
     *              above discussion)
     * @return a File indicating a file path where the received data may be
     *        written to.
     *        Absolute paths are strongly encouraged. The file path MUST
     *        indicate a location that is writeable by the application.
     *        It is acceptible for this file to exist; if it does, however, the
     *        file will be overwritten with the contents of the received mission
     *        package.
     */
    public File missionPackageReceiveInit(String fileName, String transferName,
                                          String sha256hash,
                                          long sizeInBytes,
                                          String senderCallsign) 
                                throws MissionPackageTransferException;

    /**
     * Provides status updates on a previously initialized
     * mission package reception.
     * @param update contains information on the status of the ongoing or
     *        completed mission package reception
     */
    public void missionPackageReceiveStatusUpdate(
                    MissionPackageReceiveStatusUpdate update);
    
    /**
     * Signals an update on a previously initiated mission package send.
     * See the MissionPackageSendStatusUpdate class for 
     * more detail on the series of events one may expect to see. 
     * Broadly speaking, for each accepted (not gone) contact in the
     * original send request, zero or more progress updates will be posted
     * via this method, followed by a final completion update. Each contact
     * in the original request will see its own series of events relating
     * the the send status to that specific recipient.
     * Note that if (for any number of reasons) the receiver
     * never receives the package, or fails to send a nack that it can't
     * receive it, the transfer may remain pending with those
     * recipients outstanding.
     * (note: time out functionality may be added in the future)
     * 
     * For sends to TAK servers, this method will be invoked similar to when
     * sending to Contact(s), but only one series of updates will be issued
     * with all such updates having a null recipient Contact.
     *
     * The entire transfer is completed when updates containing 
     * FINISHED_* status codes have been received for all accepted (not gone)
     * recipients (or, for MP sends to a server, when a single update is
     * received with a FINISHED_* status code).
     *
     * @param update a bundle of information providing status on the transfer
     */
    public void missionPackageSendStatusUpdate(
            MissionPackageSendStatusUpdate update);

    /**
     * Invoked to request the current location information. 
     * This callback is invoked by the system when it needs to obtain
     * current location information to use in messaging for file transfers.
     * @return a new CoTPointData instance indicating the current 
     *         location information for the local system
     */
    public CoTPointData getCurrentPoint();
    
    /**
     * Invoked to request the creation of a unique ID.
     * This callback is invoked by the system when it needs to obtain
     * a UUID.  Implementations should return the string form of a UUID (example:
     * 9811b9a7-71e6-462c-840e-dc1895830b7c). The result should be
     * exactly 36 characters long and in the format shown above.
     * The result MUST use all lowercase ASCII hex characters (plus hyphens).
     */
    public String createUUID();

}
