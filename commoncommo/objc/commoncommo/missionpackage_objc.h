//
//  missionpackage_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 3/17/16.
//  Copyright © 2016 Jeff Downs. All rights reserved.
//

#ifndef missionpackage_objc_h
#define missionpackage_objc_h

#import "cotmessageio_objc.h"

typedef NS_ENUM(NSInteger, CommoMissionPackageTransferStatus) {
    /**
     * The transfer succeeded
     */
    CommoTransferFinishedSuccess,
    /**
     * The transfer timed out
     */
    CommoTransferFinishedTimedOut,
    /**
     * The contact disappeared from the network before the transfer
     * was completed.
     */
    CommoTransferFinishedContactGone,
    /**
     * The recipient reported an issue receiving the transfer. It is no
     * attempting to receive the transfer
     */
    CommoTransferFinishedFailed,
    /**
     * The file exists already and no transfer is needed.
     */
    CommoTransferFinishedFileExists,
    /**
     * Mission package transfers are disabled
     * locally.
     */
    CommoTransferFinishedDisabledLocally,
    /**
     * A transfer attempt is in progress
     */
    CommoTransferAttemptInProgress,
    /**
     * A transfer attempt failed,
     * but further attempts may be forthcoming
     */
    CommoTransferAttemptFailed,
    /**
     * A server upload is needed and will
     * be forthcoming
     */
    CommoTransferServerUploadPending,
    /**
     * A server upload is in progress
     */
    CommoTransferServerUploadInProgress,
    /**
     * A server upload completed successfully
     */
    CommoTransferServerUploadSuccess,
    /**
     * A server upload failed
     */
    CommoTransferServerUploadFailed
};


/**
 * @brief A bundle of information about an ongoing or just completed
 * inbound Mission Package transfer.
 */
@protocol CommoMissionPackageReceiveStatusUpdate <NSObject>

@required

/**
 * @brief The local file identifying which transfer the update pertains to.
 * @discussion Matches a file previously returned in a call to MissionPackage
 * missionPackageReceiveInit()
 */
@property (readonly) NSString *localFile;

/**
 * @brief The current status of the transfer.
 * @discussion Can be one of:
 * FinishedSuccess
 * FinishedFailed
 * AttemptInProgress
 * AttemptFailed
 *
 * The Finished* status codes indicate this will be the final update
 * for this MP transfer.
 * The Attempt* codes indicate that another status report will be
 * forthcoming.
 */
@property (readonly) CommoMissionPackageTransferStatus status;

/**
 * @brief Total number of bytes received in this attempt.
 */
@property (readonly) int64_t totalBytesReceived;

/**
 * @brief Total number of bytes expected to be received to complete the transfer.
 * Note: this number is provided by the sender and may not be accurate
 * If the sender does not provide the information, it will be reported as
 * zero (0).  Because of these limitations, totalBytesReceived
 * could exceed this number!
 * This number will remain the same in all status updates for a
 * given transfer.
 * </summary>
 */
@property (readonly) int64_t totalBytesExpected;

/**
 * @brief The current download attempt
 * @disuccsion Numbered stating at 1, and less than
 * equal to "maxAttempts". For AttemptFailed updates, this is the
 * attempt number that failed. For Finished* updates, this is the attempt
 * number that caused the final status update.
 */
@property (readonly) int attempt;

/**
 * @brief The total number of attempts that will be made to download the
 * Mission Package.
 * @discussion This will be constant for all updates for a given
 * transfer.
 */
@property (readonly) int maxAttempts;

/**
 * @brief A textual description of why the transfer failed.
 * @discussion This is only non-nil when status is FinishedFailed
 * or AttemptFailed
 * </summary>
 */
@property (readonly) NSString *errorDetail;


@end



/**
 * @brief Bundle of information about an ongoing transfer
 */
@protocol CommoMissionPackageSendStatusUpdate <NSObject>

@required
/**
 * @brief transfer id that uniquely identifies the transfer - matches the id returned from
 *        Commo::sendMissionPackageInitToContacts() or Commo::sendMissionPackageToServer()
 */
@property (readonly) int xferid;
/**
 * @brief Contact UID of the recipient to which this update pertains
 * @description Contact UID of the recipient of the mission package transfer
 *        for whom this update pertains.  Will be nil if the transfer
 *        identified by xferid was a server upload rather than a send to
 *        one or more contacts
 */
@property (readonly) NSString *recipient;

/**
 * @brief The status of the transfer to this recipient.
 * @discussion Can be one of:
 * FinishedSuccess
 * FinishedTimedOut
 * FinishedFailed
 * FinishedDisabledLocally
 * ServerUploadPending
 * ServerUploadInProgress
 * ServerUploadSuccess
 * ServerUploadFailed
 *
 * Additionally, for sends to one or more Contacts (not a
 * TAK server), can also be one of:
 * AttemptInProgress
 * FinishedContactGone
 *
 * FinishedContactGone indicates the contact disappeared between the
 * initiation of the transfer and when we could actually notify
 * the Contact that the file was ready for transfer.
 * FinishedDisabledLocally indicates that a transfer had been starte
 * but the local mission package setup was changed in a way that was
 * incompatible with the started transfer (server transfer and
 * server transfers were disabled, peer to peer transfer and the loc
 * web server port was changed or disabled).
 *
 * Any Finished* code should be considered the final update for this
 * transfer *to the specified recipient*. No further updates will
 * come for the recipient for this transfer.
 *
 * If the transfer is to a server, or if a destination Contact is
 * reachable on a server, the Server* status events may be seen to r
 * progress of making the MP available on the server.
 * ServerUploadPending will be seen shortly after the start of the
 * transfer if the upload is required; this will continue until
 * the upload actually begins.
 * SeverUploadInProgress status events will report
 * start and progress of the upload.
 * The upload ends with either ServerUploadSuccess or
 * ServerUploadFailed.
 * Successful uploads will shortly post a FinishedSuccess status
 * update for sends
 * to a server, or an AttemptInProgress (see below) for a send to
 * a server-reachable Contact.
 * Failed uploads will shortly post a Finished* status.
 *
 * For transfers to contacts not reachable via TAK servers,
 * the first status report will generally be AttemptInProgress which
 * indicates the Contact has been notified of the MP availability an
 * we are awaiting them to fetch it and send us an acknowledgement (
 * failure report).
 */
@property (readonly) CommoMissionPackageTransferStatus status;

/**
 * @brief Additional details about the status update.
 * @discussion For Finished* events, this is the "reason" string returned
 * from the recipient which gives more detail on the transfer
 * results. It is always non-null (but possibly empty) for
 * Finished* events generated by a (n)ack response from the
 * receiver.
 * For Finished* events caused by local error conditions, this
 * will be null.
 * For ServerUploadSuccess, this will contain the URL of the
 * file on the server.
 * For ServerUploadFailed, this will contain error details
 * about why the upload failed.
 * For all other status codes, this will be null
 */
@property (readonly) NSString *additionalDetail;

/**
 * @brief byte transfer count for server uploads
 * @discussion For status == ServerUpload*,
 * indicates the total number of bytes uploaded so far.
 * For status == FinishedSuccess, on a send to a contact
 * this contains the # of bytes the remote recipient says it receive
 * or 0 if unknown; on sends to a TAK server, contains the #
 * of bytes transferred (this will be 0 if the file was
 * already on the server).
 * Will be 0 for other status codes.
 *
 */
@property (readonly) int64_t totalBytesTransferred;

@end



/**
 * @brief Protocol for applications to interact with mission package transfer system
 * @description Applications wishing to use Commo to send and receive files (mission packages)
 *              should implement this protocol and pass the implementation to a call to Commo's
 *              enableMissionPackageIO function. This protocol provides various file management
 *              and status callbacks for the file transfer system.
 */
@protocol CommoMissionPackageIO <NSObject>

/**
 * @brief Signals an incoming package is ready to be received.
 * @description This function is invokes when an incoming file
 * request has been received and the system is ready to attempt
 * to download it.  Depending on the return value, the application
 * can decide to accept or reject the transfer, as well as provide
 * a file storage location where the file data should be transferred to.
 * destFile is initially populated with the remote sender's name for the file.
 *
 * Implementations must return one of FinishedSuccess, FinishedFailed,
 * FinishedFileExists, or FinishedDisabledLocally.
 *
 * Success can be returned if destFile was filled with an
 * acceptable file name to use locally to write the received file to.  Note
 * that the system may write to this file even on failed transfers. See more
 * below about how transfers proceed when Success is returned.
 *
 * FileExists indicates that the local system already has the file to be
 * transferred on local record, and thus does not need it. The system
 * will attempt to notify the remote system of this.
 *
 * Disabled indicates that file transfers are disabled; the transfer is
 * aborted.
 *
 * Failed can be returned for any other reason that the system is not able
 * to receive the package at this time; the transfer is aborted.
 *
 * If the application returns FinishedSuccess, the transfer is
 * initiated and will continue asynchronously until it is
 * completed or an error occurs.  Progress and completion
 * will be reported via calls to missionPackageReceiveStatusUpdate().
 * Completion is indicated with appropriate status codes in
 * these updates (see MissionPackageReceiveStatusUpdate).
 * All updates for this transfer will contain the same file as is
 * returned here in destFile.
 *
 * An implementation returning FinishedSuccess and filling in destFile with the
 * same file name of another transfer already in progress will result
 * in the transfer being aborted (in particular, no call to
 * MissionPackageReceiveStatusUpdate() will
 * be made for this newest transfer as it would be indistinguishable from
 * the already-progressing transfer.
 *
 * It is guaranteed that this function is only called on one thread at a
 * time for any Commo implementation upon which it is installed.
 * If the controlling Commo instance is closed, outstanding transfers
 * are aborted (in particular, missionPackageReceiveStatusUpdate() is no longer
 * called for those transfers).
 *
 * @param destFile On entry, this contains a string indicating the remote sender's
 *        preferred file name.  On a Success return, the implementation MUST have
 *        set this string to a suitable file name where the received data may be written.
 *        Absolute paths are strongly encouraged.
 *        It is acceptible for this file to exist; if it does, however, the
 *        file will be overwritten with the contents of the received mission
 *        package.
 * @param transferName is the remote senders "conceptual name" of the package. This
 *              is provided to help identify the transfer.
 * @param sha256hash is a hex string of the sha256 hash of the file to be received
 *            (as provided by the remote sender)
 * @param sizeInBytes expected total size of the file to be received, as
 *              provided by the sender.  Because it comes from the sender,
 *              it should not be considered to be definitive; it could be
 *              wrong or missing (0)
 * @param senderCallsign is the callsign of the remote sender
 */
-(CommoMissionPackageTransferStatus)missionPackageReceiveInitWithDestFile:(NSMutableString *) destFile
                                                             transferName:(NSString *) transferName
                                                               sha256hash:(NSString *) sha256hash
                                                              sizeInBytes:(int64_t) sizeInBytes
                                                           senderCallsign:(NSString *) senderCallsign;

/**
 * @brief Provides status updates on a previously initialized
 * mission package reception.
 * @param update contains information on the status of the ongoing or
 *              completed mission package reception
 */
-(void)missionPackageReceiveStatusUpdate:(id<CommoMissionPackageReceiveStatusUpdate>) update;

/**
 * @brief Signals an update on a previously initiated mission package send.
 * @discussion See the MissionPackageSendStatusUpdate class for
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
 * with all such updates having a nil recipient Contact.
 *
 * The entire transfer is completed when updates containing
 * Finished* status codes have been received for all accepted (not gone)
 * recipients (or, for MP sends to a server, when a single update is
 * received with a Finished* status code).
 * @param update a bundle of information providing status on the transfer
 */
-(void)missionPackageSendStatusUpdate:(id<CommoMissionPackageSendStatusUpdate>) update;

/**
 * @brief invoked to request the current location information
 * @description This callback is invoked by the system when it needs to obtain
 * current location information to use in messaging for file transfers.
 */
-(CommoCoTPointData *)getCurrentPoint;

/**
 * @brief invoked to request the creation of a unique ID
 * @description this callback is invoked by the system when it needs to obtain
 * a UUID.  Implementations should return the string form of a UUID (example:
 * 9811b9a7-71e6-462c-840e-dc1895830b7c). The result should be
 * exactly 36 characters long and in the format shown above.
 * The result MUST use all lowercase ASCII hex characters (plus hyphens)
 */
-(NSString *)createUUID;


@end


#endif /* missionpackage_objc_h */
