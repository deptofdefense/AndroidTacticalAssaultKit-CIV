#ifndef MISSIONPACKAGE_CLI_H_
#define MISSIONPACKAGE_CLI_H_

#include "commoresult_cli.h"
#include "cotmessageio_cli.h"

namespace TAK {
    namespace Commo {


        public enum class MissionPackageTransferStatus {
            /**
             * The transfer succeeded
             */
            TransferFinishedSuccess,
            /**
             * The transfer timed out
             */
            TransferFinishedTimedOut,
            /**
             * The contact disappeared from the network before the transfer
             * was completed. 
             */
            TransferFinishedContactGone,
            /**
             * The recipient reported an issue receiving the transfer. It is no longer
             * attempting to receive the transfer
             */
            TransferFinishedFailed,
            /**
             * The file exists already and no transfer is needed.
             */
            TransferFinishedFileExists,
            /**
             * Mission package transfers are disabled
             * locally.
             */
            TransferFinishedDisabledLocally,
            /**
             * A transfer attempt is in progress
             */
            TransferAttemptInProgress,
            /**
             * A transfer attempt failed, 
             * but further attempts may be forthcoming
             */
            TransferAttemptFailed,
            /**
             * A server upload is needed and will 
             * be forthcoming
             */
            TransferServerUploadPending,
            /**
             * A server upload is in progress
             */
            TransferServerUploadInProgress,
            /**
             * A server upload completed successfully
             */
            TransferServerUploadSuccess,
            /**
             * A server upload failed
             */
            TransferServerUploadFailed
        };
        
        
        /**
         * <summary>
         * A bundle of information about an ongoing or just completed
         * inbound Mission Package transfer.
         * </summary>
         */
        public ref class MissionPackageReceiveStatusUpdate
        {
        public:
            /**
             * <summary>
             * The local file identifying which transfer the update pertains to.
             * Will match a File previously returned in a call to MissionPackageIO's
             * missionPackageReceiveInit()
             * </summary>
             */
            initonly System::String ^localFile;

            /**
             * <summary>
             * The current status of the transfer. 
             * Can be one of:
             * FinishedSuccess
             * FinishedFailed
             * AttemptInProgress
             * AttemptFailed
             * 
             * The Finished* status codes indicate this will be the final update
             * for this MP transfer.
             * The Attempt* codes indicate that another status report will be
             * forthcoming.
             * </summary>
             */
            initonly MissionPackageTransferStatus status;

            /**
             * <summary>
             * Total number of bytes received in this attempt.
             * </summary>
             */
            initonly System::Int64 totalBytesReceived;

            /**
             * <summary>
             * Total number of bytes expected to be received to complete the transfer.
             * Note: this number is provided by the sender and may not be accurate!
             * If the sender does not provide the information, it will be reported as
             * zero (0).  Because of these limitations, totalBytesReceived
             * could exceed this number!
             * This number will remain the same in all status updates for a
             * given transfer.
             * </summary>
             */
            initonly System::Int64 totalBytesExpected;
            
            /**
             * <summary>
             * The current download attempt, numbered stating at 1, and less than or
             * equal to "maxAttempts". For AttemptFailed updates, this is the
             * attempt number that failed. For Finished* updates, this is the attempt
             * number that caused the final status update.
             * </summary>
             */
            initonly int attempt;

            /**
             * <summary>
             * The total number of attempts that will be made to download the 
             * Mission Package.  This will be constant for all updates for a given
             * transfer.
             * </summary>
             */
            initonly int maxAttempts;
            
            /**
             * <summary>
             * a textual description of why the transfer failed.
             * This is only non-null when status is FinishedFailed
             * or AttemptFailed
             * </summary>
             */
            initonly System::String ^errorDetail;



        internal:
            MissionPackageReceiveStatusUpdate(System::String ^file,
                                              MissionPackageTransferStatus status,
                                              System::Int64 bytesReceived,
                                              System::Int64 bytesExpected,
                                              int attempt,
                                              int maxAttempts,
                                              System::String ^errorDetail);
            ~MissionPackageReceiveStatusUpdate();
        };



        /**
        * <summary>
        * Bundle of information about an ongoing transfer
        * </summary>
        */
        public ref class MissionPackageSendStatusUpdate
        {
        public:
            /**
             * <summary>
             * transfer id that uniquely identifies the transfer - 
             * matches the id returned from Commo::SendMissionPackageInit()
             * </summary>
             */
            initonly int xferid;

            /**
             * <summary>
             * In updates of a transfer to contacts, this contains the
             * UID of the recipient contact to which this update pertains.
             * In updates of a transfer to a server, this is null.
             * </summary>
             */
            initonly System::String ^recipient;

            /**
             * <summary>
             * The status of the transfer to this recipient.
             * Can be one of:
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
             * FinishedDisabledLocally indicates that a transfer had been started,
             * but the local mission package setup was changed in a way that was
             * incompatible with the started transfer (server transfer and
             * server transfers were disabled, peer to peer transfer and the local
             * web server port was changed or disabled).
             *
             * Any Finished* code should be considered the final update for this
             * transfer *to the specified recipient*. No further updates will
             * come for the recipient for this transfer.
             * 
             * If the transfer is to a server, or if a destination Contact is
             * reachable on a server, the Server* status events may be seen to report
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
             * indicates the Contact has been notified of the MP availability and
             * we are awaiting them to fetch it and send us an acknowledgement (or
             * failure report).
             *
             * </summary>
             */
            initonly MissionPackageTransferStatus status;

            /**
             * <summary>
             * For Finished* events, this is the "reason" string returned
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
             * </summary>
             */
            initonly System::String ^additionalDetail;

            /**
             * <summary>
             * For status == ServerUpload*, 
             * indicates the total number of bytes uploaded so far.
             * For status == FinishedSuccess, on a send to a contact
             * this contains the # of bytes the remote recipient says it received
             * or 0 if unknown; on sends to a TAK server, contains the #
             * of bytes transferred (this will be 0 if the file was
             * already on the server).
             * Will be 0 for other status codes.
             * </summary>
             */
            initonly System::Int64 totalBytesTransferred;


        internal:
            MissionPackageSendStatusUpdate(const int xferid,
                                           System::String ^contact,
                                           const MissionPackageTransferStatus status,
                                           System::String ^detail,
                                           System::Int64 bytesTransferred);
            ~MissionPackageSendStatusUpdate();

        };


        /**
         * <summary>
         * Interface for applications to interact with mission package transfer system
         * Applications wishing to use Commo to send and receive files (mission packages)
         * should implement this interface and pass the implementation to a call to Commo's
         * SetupMissionPackageIO function. This interface provides various file management
         * and status callbacks for the file transfer system.
         * </summary>
         */
        public interface class IMissionPackageIO
        {
        public:
            /**
             * <summary>
             * Signals an incoming package is ready to be received.
             * This function is invokes when an incoming file
             * request has been received and the system is ready to attempt
             * to download it.  Depending on the return value, the application
             * can decide to accept or reject the transfer, as well as provide
             * a file storage location where the file data should be transferred to.
             * destFile is initially populated with the remote sender's name for the file.
             *
             * Implementations must return one of FinishedSuccess, 
             * FinishedFailed, FinishedFileExists, or FinishedDisabledLocally.
             *
             * FinishedSuccess can be returned if destFile was filled with an
             * acceptable file name to use locally to write the received file to.  Note
             * that the system may write to this file even on failed transfers. See more
             * below about how transfers proceed when FinishedSuccess is returned.
             *
             * FinishedFileExists indicates that the local system already has the file to be
             * transferred on local record, and thus does not need it. 
             * This is not considered a failure per se; rather the local system
             * will attempt to notify the remote system that the file is already
             * present and a transfer is unnecessary.
             *
             * FinishedDisabledLocally indicates that file transfers are
             * disabled; the transfer is aborted and sender is notified.
             *
             * FinishedFailed can be returned for any other reason that
             * the system is not able to receive the package at this time;
             * the transfer is aborted and sender is notified. 
             *
             * Any other return
             * value will be treated as though FinishedFailed were returned.
             *
             * If the application returns FinishedSuccess, the transfer is
             * initiated and will continue asynchronously until it is
             * completed or an error occurs.  Progress and completion
             * will be reported via calls to MissionPackageReceiveStatusUpdate().
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
             * are aborted (in particular, ReceiveStatusUpdate() is no longer
             * called for those transfers).
             * </summary>
             *
             * <param name="destFile">
             * On entry, this contains a string indicating the remote sender's
             * preferred file name.  On a FinishedSuccess return, the implementation MUST have
             * set this string to a suitable file name where the received data may be written.
             * Absolute paths are strongly encouraged.
             * It is acceptible for this file to exist; if it does, however, the
             * file will be overwritten with the contents of the received mission
             * package.
             * </param>
             * <param name="transferName">
             * the remote senders "conceptual name" of the package. This
             * provided to help identify the transfer.
             * </param>
             * <param name="sha256hash">
             * a hex string of the sha256 hash of the file to be received
             * (as provided by the remote sender)
             * </param>
             * <param name="sizeInBytes">
             * expected total size of the file to be received, as
             * provided by the sender.  Because it comes from the sender,
             * it should not be considered to be definitive; it could be
             * wrong or missing (0)
             * </param>
             * <param name="senderCallsign">
             * the callsign of the remote sender
             * </param>
             */
            virtual MissionPackageTransferStatus MissionPackageReceiveInit(
                System::String ^% destFile,
                System::String ^transferName, System::String ^sha256hash,
                System::Int64 sizeInBytes,
                System::String ^senderCallsign);

            /**
             * <summary>
             * Provides status updates on a previously initialized
             * mission package reception.
             * </summary>
             * <param name="update">
             * update contains information on the status of the ongoing or
             * completed mission package reception
             * </param>
             */
            virtual void MissionPackageReceiveStatusUpdate(
                MissionPackageReceiveStatusUpdate ^update);

            /**
             * <summary>
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
             * Finished* status codes have been received for all accepted (not gone)
             * recipients (or, for MP sends to a server, when a single update is
             * received with a Finished* status code).
             * </summary>
             * <param name="update">
             * a bundle of information providing status on the transfer
             * </param>
             */
            virtual void MissionPackageSendStatusUpdate(MissionPackageSendStatusUpdate ^update);

            /**
             * <summary>
             * Invoked to request the current location information.
             * This callback is invoked by the system when it needs to obtain
             * current location information to use in messaging for
             * file transfers.
             * </summary>
             */
            virtual CoTPointData GetCurrentPoint();
        };

    }
}


#endif /* MISSIONPACKAGE_H_ */
