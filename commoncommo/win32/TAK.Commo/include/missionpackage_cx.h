#pragma once

#include "commoresult_cx.h"
#include "cotmessageio_cx.h"

namespace TAK {
    namespace Commo {

        public enum class MissionPackageTransferStatus {
            TransferSuccess,
            TransferTimedOut,
            TransferContactGone,
            TransferFailed,
            TransferFileExists,
            TransferDisabled,
        };
    
        /**
        * <summary>
        * Bundle of information about an ongoing transfer
        * </summary>
        */
        public ref class MissionPackageTransferStatusUpdate sealed
        {
        public:
            /**
            * <summary>
            * transfer id that uniquely identifies the transfer -
            * matches the id returned from Commo::sendMissionPackage()
            * </summary>
            */
            property int TransferId { int get() { return _xferid; }};

            /**
            * <summary>
            * Callsign of the recipient to which this update pertains
            * </summary>
            */
            property Platform::String ^Recipient { Platform::String^ get() { return _recipient; }}
            /**
            * <summary>
            * The status of the transfer to this recipient.
            * Can be one of Success, TimedOut, Failed,
            * FileExists (file existed on remote end, which should
            * be considered a successful transmission), Disabled (transfers disabled
            * on remote end), or ContactGone (the contact disappeared between
            * the initiation of the transfer and when we could actually notify
            * them that the file was ready for transfer)
            * </summary>
            */
            property MissionPackageTransferStatus Status {MissionPackageTransferStatus get() { return _status; }}
            /**
            * <summary>
            * the "reason" string returned from the recipient;
            * gives more detail on the transfer results
            * </summary>
            */
            property Platform::String ^Reason {Platform::String^ get() { return _reason; }}

            virtual ~MissionPackageTransferStatusUpdate();
        internal:
            MissionPackageTransferStatusUpdate(const int xferid,
                Platform::String ^contact,
                const MissionPackageTransferStatus status,
                Platform::String ^reason);

        private:
            int _xferid;
            Platform::String^ _recipient;
            MissionPackageTransferStatus _status;
            Platform::String^ _reason;
        };


        /**
        * <summary>
        * Interface for applications to interact with mission package transfer system
        * Applications wishing to use Commo to send and receive files (mission packages)
        * should implement this interface and pass the implementation to a call to Commo's
        * enableMissionPackageIO function. This interface provides various file management
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
            * Implementations must return one of Success, Failed, FileExists,
            * or Disabled.
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
            * If the application returns Success, the transfer is initiated and will continue
            * asynchronously until it is completed or an error occurs.  Once complete
            * (either successfully or unsuccessfully), missionPackageReceiveComplete()
            * is called with the same file name.
            *
            * An implementation returning Success and filling in destFile with the
            * same file name of another transfer already in progress will result
            * in the transfer being aborted (in particular, no call to Complete() will
            * be made for this newest transfer as it would be indistinguishable from
            * the already-progressing transfer.
            *
            * It is guaranteed that this function is only called on one thread at a
            * time for any Commo implementation upon which it is installed.
            * If the controlling Commo instance is closed, outstanding transfers
            * are aborted (in particular, Complete() is never called for those
            * transfers).
            * </summary>
            *
            * <param name="destFile">
            * On entry, this contains a string indicating the remote sender's
            * preferred file name.  On a Success return, the implementation MUST have
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
            * <param name="senderCallsign">
            * the callsign of the remote sender
            * </param>
            */
            virtual MissionPackageTransferStatus missionPackageReceiveInit(
                Platform::String ^ destFileIn,
                Platform::String ^* destFileOut,
                Platform::String ^transferName, Platform::String ^sha256hash,
                Platform::String ^senderCallsign);

            /**
            * <summary>
            * Signals completion of a mission package being received.
            * Indicates a previously initialized mission package reception
            * has completed.
            * </summary>
            * <param name="destFile">
            * specifies the filename containing the received mission package
            * data (assuming the transfer was successful).
            * This matches a destFile provided previously
            * by the application in a call to missionPackageReceiveInit.
            * </param>
            * <param name="status">
            * Success for a fully and successfully completed transfer or
            * Failed for a failed transfer.
            * </param>
            * <param name="error">
            * a textual description of why the transfer failed; this
            * is only non-nil when status is Failed
            * </param>
            */
            virtual void missionPackageReceiveComplete(Platform::String ^destFile,
                MissionPackageTransferStatus status,
                Platform::String ^error);

            /**
            * <summary>
            * Signals an update on a previously initiated mission package send.
            * Generally this is invoked for each accepted (not gone) contact in the
            * original send request until all contacts in the request have received the
            * package.  However, if (for any number of reasons) the receiver never
            * receives the package, or fails to send a nack that it can't receive it,
            * the transfer may remain pending with those recipients outstanding.
            * (note: time out functionality may be added in the future)
            * </summary>
            * <param name="update">
            * a bundle of information providing status on the transfer
            * </param>
            */
            virtual void missionPackageSendStatus(MissionPackageTransferStatusUpdate ^update);

            /**
            * <summary>
            * Invoked to request the current location information.
            * </summary>
            * <param name="description">
            * This callback is invoked by the system when it needs to obtain
            * current location information to use in messaging for
            * file transfers.
            * </param>
            */
            virtual CoTPointData^ getCurrentPoint();
        };

    }
}