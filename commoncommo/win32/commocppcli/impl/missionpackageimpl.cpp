#include "missionpackageimpl.h"

using namespace TAK::Commo::impl;
using namespace System;

namespace {
    atakmap::commoncommo::MissionPackageTransferStatus cliToNative(TAK::Commo::MissionPackageTransferStatus status)
    {
        atakmap::commoncommo::MissionPackageTransferStatus ret;
        switch (status) {
        case TAK::Commo::MissionPackageTransferStatus::TransferFinishedSuccess:
            ret = atakmap::commoncommo::MP_TRANSFER_FINISHED_SUCCESS;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferFinishedTimedOut:
            ret = atakmap::commoncommo::MP_TRANSFER_FINISHED_TIMED_OUT;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferFinishedContactGone:
            ret = atakmap::commoncommo::MP_TRANSFER_FINISHED_CONTACT_GONE;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferFinishedFailed:
            ret = atakmap::commoncommo::MP_TRANSFER_FINISHED_FAILED;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferFinishedFileExists:
            ret = atakmap::commoncommo::MP_TRANSFER_FINISHED_FILE_EXISTS;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferFinishedDisabledLocally:
            ret = atakmap::commoncommo::MP_TRANSFER_FINISHED_DISABLED_LOCALLY;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferAttemptInProgress:
            ret = atakmap::commoncommo::MP_TRANSFER_ATTEMPT_IN_PROGRESS;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferAttemptFailed:
            ret = atakmap::commoncommo::MP_TRANSFER_ATTEMPT_FAILED;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferServerUploadPending:
            ret = atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_PENDING;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferServerUploadInProgress:
            ret = atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferServerUploadSuccess:
            ret = atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_SUCCESS;
            break;
        case TAK::Commo::MissionPackageTransferStatus::TransferServerUploadFailed:
            ret = atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_FAILED;
            break;
        }
        return ret;
    }

    TAK::Commo::MissionPackageTransferStatus nativeToCLI(atakmap::commoncommo::MissionPackageTransferStatus status)
    {
        TAK::Commo::MissionPackageTransferStatus ret;
        switch (status) {
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_SUCCESS:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferFinishedSuccess;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_TIMED_OUT:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferFinishedTimedOut;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_CONTACT_GONE:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferFinishedContactGone;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_FAILED:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferFinishedFailed;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_FILE_EXISTS:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferFinishedFileExists;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_DISABLED_LOCALLY:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferFinishedDisabledLocally;
            break;
        case atakmap::commoncommo::MP_TRANSFER_ATTEMPT_IN_PROGRESS:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferAttemptInProgress;
            break;
        case atakmap::commoncommo::MP_TRANSFER_ATTEMPT_FAILED:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferAttemptFailed;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_PENDING:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferServerUploadPending;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferServerUploadInProgress;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_SUCCESS:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferServerUploadSuccess;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_FAILED:
            ret = TAK::Commo::MissionPackageTransferStatus::TransferServerUploadFailed;
            break;
        }
        return ret;
    }
}


TAK::Commo::MissionPackageReceiveStatusUpdate::
MissionPackageReceiveStatusUpdate(System::String ^file,
                                   MissionPackageTransferStatus status,
                                   System::Int64 bytesReceived,
                                   System::Int64 bytesExpected,
                                   int attempt,
                                   int maxAttempts,
                                   System::String ^errorDetail) : 
                localFile(file), 
                status(status),
                totalBytesReceived(bytesReceived),
                totalBytesExpected(bytesExpected),
                attempt(attempt),
                maxAttempts(maxAttempts),
                errorDetail(errorDetail)
{

}
TAK::Commo::MissionPackageReceiveStatusUpdate::~MissionPackageReceiveStatusUpdate()
{

}


TAK::Commo::MissionPackageSendStatusUpdate::
MissionPackageSendStatusUpdate(const int xferid,
                                   System::String ^contact,
                                   const MissionPackageTransferStatus status,
                                   System::String ^detail,
                                   System::Int64 bytesTransferred) : xferid(xferid), recipient(contact), status(status), additionalDetail(detail), totalBytesTransferred(bytesTransferred)
{

}
TAK::Commo::MissionPackageSendStatusUpdate::~MissionPackageSendStatusUpdate()
{

}


MissionPackageIOImpl::MissionPackageIOImpl(TAK::Commo::IMissionPackageIO ^mpio) : atakmap::commoncommo::MissionPackageIO(), mpioCLI(mpio)
{

}


MissionPackageIOImpl::~MissionPackageIOImpl()
{

}

atakmap::commoncommo::MissionPackageTransferStatus MissionPackageIOImpl::missionPackageReceiveInit(
    char *destFile, size_t destFileSize,
    const char *transferName, const char *sha256hash,
    uint64_t expectedByteSize,
    const char *senderCallsign)
{
    String ^destFileString = gcnew String(destFile);
    TAK::Commo::MissionPackageTransferStatus retcli;
    
    int64_t nBytes = (int64_t)expectedByteSize;
    if (expectedByteSize > INT64_MAX)
        nBytes = INT64_MAX;
    
    retcli = mpioCLI->MissionPackageReceiveInit(destFileString, 
                                                gcnew String(transferName, 0, strlen(transferName), System::Text::Encoding::UTF8),
                                                gcnew String(sha256hash), 
                                                nBytes,
                                                gcnew String(senderCallsign, 0, strlen(senderCallsign), System::Text::Encoding::UTF8));
    switch (retcli) {
    case MissionPackageTransferStatus::TransferFinishedSuccess:
        // Proceed
        break;
    case MissionPackageTransferStatus::TransferFinishedFileExists:
        return atakmap::commoncommo::MP_TRANSFER_FINISHED_FILE_EXISTS;
    case MissionPackageTransferStatus::TransferFinishedDisabledLocally:
        return atakmap::commoncommo::MP_TRANSFER_FINISHED_DISABLED_LOCALLY;
    case MissionPackageTransferStatus::TransferFinishedFailed:
    default:
        return atakmap::commoncommo::MP_TRANSFER_FINISHED_FAILED;
    }

    if (destFileString->Length < 0 || (size_t)destFileString->Length >= destFileSize)
        return atakmap::commoncommo::MP_TRANSFER_FINISHED_FAILED;
    msclr::interop::marshal_context mctx;
    const char *destFileOut = mctx.marshal_as<const char *>(destFileString);
    strcpy(destFile, destFileOut);

        return atakmap::commoncommo::MP_TRANSFER_FINISHED_SUCCESS;
}

void MissionPackageIOImpl::missionPackageReceiveStatusUpdate(const atakmap::commoncommo::MissionPackageReceiveStatusUpdate *update)
{
    int64_t br = update->totalBytesReceived;
    int64_t be = update->totalBytesExpected;
    if (update->totalBytesExpected > INT64_MAX)
        be = INT64_MAX;
    if (update->totalBytesReceived > INT64_MAX)
        br = INT64_MAX;

    MissionPackageReceiveStatusUpdate ^upCli = 
        gcnew MissionPackageReceiveStatusUpdate(gcnew String(update->localFile),
                                              nativeToCLI(update->status),
                                              br, be,
                                              update->attempt,
                                              update->maxAttempts,
                                              update->errorDetail ? gcnew String(update->errorDetail) : nullptr);

    mpioCLI->MissionPackageReceiveStatusUpdate(upCli);
}

void MissionPackageIOImpl::missionPackageSendStatusUpdate(const atakmap::commoncommo::MissionPackageSendStatusUpdate *update)
{
    String ^contactString = nullptr;
    if (update->recipient)
        contactString = gcnew String((const char *)update->recipient->contactUID, 0, update->recipient->contactUIDLen, System::Text::Encoding::UTF8);
    String ^detailString = nullptr;
    if (update->additionalDetail)
        detailString = gcnew String(update->additionalDetail);
    int64_t bt = (int64_t)update->totalBytesTransferred;
    if (update->totalBytesTransferred > INT64_MAX)
        bt = INT64_MAX;
    TAK::Commo::MissionPackageSendStatusUpdate ^statusCLI = 
        gcnew TAK::Commo::MissionPackageSendStatusUpdate(
                update->xferid, contactString, 
                nativeToCLI(update->status), detailString, bt);
    mpioCLI->MissionPackageSendStatusUpdate(statusCLI);
}


atakmap::commoncommo::CoTPointData MissionPackageIOImpl::getCurrentPoint()
{
    TAK::Commo::CoTPointData pointCLI = mpioCLI->GetCurrentPoint();
    atakmap::commoncommo::CoTPointData pointNative(pointCLI.lat,
                                                   pointCLI.lon,
                                                   pointCLI.hae,
                                                   pointCLI.ce,
                                                   pointCLI.le);
    return pointNative;
}

void MissionPackageIOImpl::createUUID(char *uuidString)
{
    Guid g = System::Guid::NewGuid();
    String ^s = g.ToString("D");
    msclr::interop::marshal_context mctx;
    const char *uuidNative = mctx.marshal_as<const char *>(s);
    strncpy(uuidString, uuidNative, COMMO_UUID_STRING_BUFSIZE);
}
