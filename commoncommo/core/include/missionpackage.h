#ifndef MISSIONPACKAGE_H_
#define MISSIONPACKAGE_H_

#include "commoutils.h"
#include "contactuid.h"
#include "cotmessageio.h"
#include "commoresult.h"

#define COMMO_UUID_STRING_BUFSIZE 37

namespace atakmap {
namespace commoncommo {


// Used in Commo::setMissionPackageLocalPort() to disable local web server
static const int MP_LOCAL_PORT_DISABLE = -1;

enum MissionPackageTransferStatus {
    MP_TRANSFER_FINISHED_SUCCESS,
    MP_TRANSFER_FINISHED_TIMED_OUT,
    MP_TRANSFER_FINISHED_CONTACT_GONE,
    MP_TRANSFER_FINISHED_FAILED,
    MP_TRANSFER_FINISHED_FILE_EXISTS,
    MP_TRANSFER_FINISHED_DISABLED_LOCALLY,
    MP_TRANSFER_ATTEMPT_IN_PROGRESS,
    MP_TRANSFER_ATTEMPT_FAILED,
    MP_TRANSFER_SERVER_UPLOAD_PENDING,
    MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS,
    MP_TRANSFER_SERVER_UPLOAD_SUCCESS,
    MP_TRANSFER_SERVER_UPLOAD_FAILED
};

struct COMMONCOMMO_API MissionPackageReceiveStatusUpdate
{
public:
    // Local file path as previously specified via return of
    // missionPackageReceiveInit(); identifies the transfer
    const char * const localFile;
    // One of...
    // FINISHED_SUCCESS, FINISHED_FAILED, ATTEMPT_IN_PROGRESS,
    // ATTEMPT_FAILED
    // FINISHED_* indicates final status. ATTEMPT_* indicates
    // a progress update.
    const MissionPackageTransferStatus status;
    // #bytes received total so far in the current attempt
    const uint64_t totalBytesReceived;
    // #bytes expected total to complete transfer. Provided by
    // sender; may be inaccurate! If not reported, will be 0.
    // Constant for all updates for a given transfer.
    const uint64_t totalBytesExpected;
    // Current attempt number. Starts at 1.
    const int attempt;
    // Maximum attempts which will be made. Constant for all updates for
    // a given transfer.
    const int maxAttempts;
    // text description of why transfer failed; only used for
    // *_FAILED status codes.
    const char * const errorDetail;


protected:
    MissionPackageReceiveStatusUpdate(const char *localFile,
            const MissionPackageTransferStatus status,
            uint64_t totalBytesReceived,
            uint64_t totalBytesExpected,
            int attempt,
            int maxAttempts,
            const char *errorDetail) : localFile(localFile),
                                  status(status),
                                  totalBytesReceived(totalBytesReceived),
                                  totalBytesExpected(totalBytesExpected),
                                  attempt(attempt),
                                  maxAttempts(maxAttempts),
                                  errorDetail(errorDetail) {};
    virtual ~MissionPackageReceiveStatusUpdate() {};

private:
    COMMO_DISALLOW_COPY(MissionPackageReceiveStatusUpdate);
};

struct COMMONCOMMO_API MissionPackageSendStatusUpdate
{
    const int xferid;
    // In updates of a transfer to contacts:
    //    The recipient in the identified transfer for whom this
    //    update pertains.
    // In updates of a transfer to a server:
    //    NULL
    const ContactUID * const recipient;
    // Can be one of...
    // FINISHED_...
    //  SUCCESS
    //  TIMED_OUT
    //  FAILED
    //  DISABLED_LOCALLY (transfers disabled locally before send completed)
    //  CONTACT_GONE
    // SERVER_UPLOAD_...
    //  PENDING
    //  IN_PROGRESS
    //  SUCCESS
    //  FAILED
    // ATTEMPT_IN_PROGRESS
    const MissionPackageTransferStatus status;
    // "reason" string from (n)ack's for FINISHED_ codes if known;
    // NULL if FINISHED_ caused by things other than (n)ack reception.
    // For SERVER_UPLOAD_SUCCESS, contains URL
    // of file on server.  For SERVER_UPLOAD_FAILED, contains
    // error info on why upload failed.   NULL for other status codes.
    const char * const additionalDetail;
    // SERVER_UPLOAD_*:  # bytes uploaded so far
    // FINISHED_SUCCESS: # bytes remote end says it received, or 0 if unknown.
    //                   For server transfers, # of bytes we sent (may be 0
    //                   if file was already on server)
    // Other status codes: 0
    const uint64_t totalBytesTransferred;

protected:
    MissionPackageSendStatusUpdate(const int xferid,
            const ContactUID *contact,
            const MissionPackageTransferStatus status,
            const char *detail,
            uint64_t totalBytes) : xferid(xferid), recipient(contact),
                                   status(status), additionalDetail(detail),
                                   totalBytesTransferred(totalBytes) {};
    virtual ~MissionPackageSendStatusUpdate() {};

private:
    COMMO_DISALLOW_COPY(MissionPackageSendStatusUpdate);
};

class COMMONCOMMO_API MissionPackageIO
{
public:
    // Signals an incoming package is ready to be received.
    // destFile is initially populated with the remote sender's name for the file.
    // transferName is the remote senders "conceptual name" of the package. This
    //              is provided to help identify the transfer.
    // sha256hash is a hex string of the sha256 hash of the file to be received
    //            (as provided by the remote sender)
    // expectedByteSize is the total byte size of the transfer advertised by
    //            the sender; note that this is entirely based on data from
    //            sender and it may be wrong or bogus! 0 is used if sender
    //            did not include.
    // senderCallsign is the callsign of the remote sender
    // The implementation must fill in destFile,
    // which is a buffer of destFileSize bytes,
    // with a suitable file name where the received data may be written.
    // Absolute paths are strongly encouraged.
    // It is acceptible for this file to exist; if it does, however, the
    // file will be overwritten with the contents of the received mission
    // package.
    // On entry, this buffer contains a null terminated string indicating
    // the remote sender's preferred file name.
    // Implementation must return one of SUCCESS, FAILED, FILE_EXISTS,
    // or DISABLED_LOCALLY.
    // SUCCESS can be returned if destFile was filled with an
    // acceptable file name to use locally to write the received file to.  Note
    // that the system may write to this file even on failed transfers.
    // FILE_EXISTS indicates that the local system already has the file to be
    // transferred on local record, and thus does not need it. The system
    // will attempt to notify the remote system of this.
    // DISABLED_LOCALLY indicates that file transfers are disabled;
    // the transfer is aborted.
    // FAILED can be returned for any other reason that the system is not able
    // to receive the package at this time; the transfer is aborted.
    //
    // Upon SUCCESS return, the transfer is initiated and will continue
    // asynchronously until it is completed or an error occurs.  Once complete
    // (either successfully or unsuccessfully), missionPackageReceiveComplete()
    // is called with the same file name.
    // An implementation returning SUCCESS and filling in destFile with the
    // same file name of another transfer already in progress will result
    // in the transfer being aborted (in particular, no call to
    // ReceiveStatusUpdate() will be made for this newest transfer as it
    // would be indistinguishable from the already-progressing transfer.
    // It is guaranteed that this function is only called on one thread at a
    // time for any Commo implementation upon which it is installed.
    // If the controlling Commo instance is closed, outstanding transfers
    // are aborted (in particular, Complete() is never called for those
    // transfers).
    virtual MissionPackageTransferStatus missionPackageReceiveInit(
            char *destFile, size_t destFileSize,
            const char *transferName, const char *sha256hash,
            uint64_t expectedByteSize,
            const char *senderCallsign) = 0;

    // Provides status updates or completion of a previously initiated
    // mission package reception. See MissionPackageReceiveStatusUpdate class
    virtual void missionPackageReceiveStatusUpdate(
            const MissionPackageReceiveStatusUpdate *update) = 0;


    // Signals an update on a previously initiated mission package send.
    // See MissionPackageSendStatusUpdate class for more info.
    // Generally this is invoked zero or more times with progress updates
    // followed by one completion update for each accepted (not gone) contact
    // in the original request until all contacts in the request have
    // received the package. However, if (for any number of reasons) 
    // the receiver never receives the package, or fails to send a
    // nack that it can't receive it, the transfer may remain pending 
    // with those recipients outstanding.
    // (note: time out functionality may be added in the future)
    //
    // For transfers to servers, this is invoked similarly to for contact(s),
    // but only one series of updates and completion will be seen
    // all with the update's recipient contact pointer being NULL.
    virtual void missionPackageSendStatusUpdate(
            const MissionPackageSendStatusUpdate *update) = 0;

    // This may be called by the transfer system to obtain a CoTPointData
    // structure describing what location information should be used in CoT
    // events sent as part of the file transfer actions.
    // Note that this may be called by multiple threads simultaneously
    virtual CoTPointData getCurrentPoint() = 0;

    // Create string form of a UUID (example:
    // 9811b9a7-71e6-462c-840e-dc1895830b7c).
    // The supplied buffer is ALWAYS COMMO_UUID_STRING_BUFSIZE characters, which
    // is precisely the size needed for the canonical UUID string format
    // plus one byte for convenience inserting a trailing NULL byte.
    // The result MUST use all lowercase ASCII hex characters (plus hyphen)
    virtual void createUUID(char *uuidString) = 0;

protected:
    MissionPackageIO() {};
    virtual ~MissionPackageIO() {};

private:
    COMMO_DISALLOW_COPY(MissionPackageIO);
};



}
}


#endif /* MISSIONPACKAGE_H_ */
