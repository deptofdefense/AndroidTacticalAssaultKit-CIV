#include "pch.h"
#include "missionpackageimpl.h"

#include <string>

using namespace TAK::Commo::impl;
using namespace Platform;

namespace {
atakmap::commoncommo::MissionPackageTransferStatus cxToNative(TAK::Commo::MissionPackageTransferStatus status)
{
    atakmap::commoncommo::MissionPackageTransferStatus ret;
    switch (status) {
    case TAK::Commo::MissionPackageTransferStatus::TransferSuccess:
        ret = atakmap::commoncommo::MP_TRANSFER_SUCCESS;
        break;
    case TAK::Commo::MissionPackageTransferStatus::TransferTimedOut:
        ret = atakmap::commoncommo::MP_TRANSFER_TIMED_OUT;
        break;
    case TAK::Commo::MissionPackageTransferStatus::TransferContactGone:
        ret = atakmap::commoncommo::MP_TRANSFER_CONTACT_GONE;
        break;
    case TAK::Commo::MissionPackageTransferStatus::TransferFailed:
        ret = atakmap::commoncommo::MP_TRANSFER_FAILED;
        break;
    case TAK::Commo::MissionPackageTransferStatus::TransferFileExists:
        ret = atakmap::commoncommo::MP_TRANSFER_FILE_EXISTS;
        break;
    case TAK::Commo::MissionPackageTransferStatus::TransferDisabled:
        ret = atakmap::commoncommo::MP_TRANSFER_DISABLED;
        break;
    }
    return ret;
}

TAK::Commo::MissionPackageTransferStatus nativeToCx(atakmap::commoncommo::MissionPackageTransferStatus status)
{
    TAK::Commo::MissionPackageTransferStatus ret;
    switch (status) {
    case atakmap::commoncommo::MP_TRANSFER_SUCCESS:
        ret = TAK::Commo::MissionPackageTransferStatus::TransferSuccess;
        break;
    case atakmap::commoncommo::MP_TRANSFER_TIMED_OUT:
        ret = TAK::Commo::MissionPackageTransferStatus::TransferTimedOut;
        break;
    case atakmap::commoncommo::MP_TRANSFER_CONTACT_GONE:
        ret = TAK::Commo::MissionPackageTransferStatus::TransferContactGone;
        break;
    case atakmap::commoncommo::MP_TRANSFER_FAILED:
        ret = TAK::Commo::MissionPackageTransferStatus::TransferFailed;
        break;
    case atakmap::commoncommo::MP_TRANSFER_FILE_EXISTS:
        ret = TAK::Commo::MissionPackageTransferStatus::TransferFileExists;
        break;
    case atakmap::commoncommo::MP_TRANSFER_DISABLED:
        ret = TAK::Commo::MissionPackageTransferStatus::TransferDisabled;
        break;
    }
    return ret;
}

Platform::String^ nativeToCx(const char* cstr)
{
    auto str = std::string(cstr);
    auto wstr = std::wstring(str.begin(), str.end());
    return ref new Platform::String(wstr.c_str());
}
}

TAK::Commo::MissionPackageTransferStatusUpdate::
MissionPackageTransferStatusUpdate(const int xferid,
    Platform::String ^contact,
    const MissionPackageTransferStatus status,
    Platform::String ^reason) : _xferid(xferid), _recipient(contact), _status(status), _reason(reason)
{
}
TAK::Commo::MissionPackageTransferStatusUpdate::~MissionPackageTransferStatusUpdate()
{

}


MissionPackageIOImpl::MissionPackageIOImpl(TAK::Commo::IMissionPackageIO ^mpio) : atakmap::commoncommo::MissionPackageIO(), _mpioCLI(mpio)
{

}


MissionPackageIOImpl::~MissionPackageIOImpl()
{

}

atakmap::commoncommo::MissionPackageTransferStatus MissionPackageIOImpl::missionPackageReceiveInit(
    char *destFile, size_t destFileSize,
    const char *transferName, const char *sha256hash,
    const char *senderCallsign)
{
    String ^destFileIn = nativeToCx(destFile);
    String ^destFileOut = ref new String();
    TAK::Commo::MissionPackageTransferStatus retcli;
    retcli = _mpioCLI->missionPackageReceiveInit(destFileIn, &destFileOut, nativeToCx(transferName),
        nativeToCx(sha256hash),
        nativeToCx(senderCallsign));

    if (destFileOut->Length() >= destFileSize)
        return atakmap::commoncommo::MP_TRANSFER_FAILED;
    auto wstr = std::wstring(destFileOut->Data());
    auto str = std::string(wstr.begin(), wstr.end());
    const char *destFileCz = str.c_str();
    strcpy(destFile, destFileCz);

    return cxToNative(retcli);
}

void MissionPackageIOImpl::missionPackageReceiveComplete(const char *destFile,
    atakmap::commoncommo::MissionPackageTransferStatus status,
    const char *error)
{
    _mpioCLI->missionPackageReceiveComplete(nativeToCx(destFile),
        nativeToCx(status),
        nativeToCx(error));
}

void MissionPackageIOImpl::missionPackageSendStatus(const atakmap::commoncommo::MissionPackageTransferStatusUpdate *update)
{
    String ^contactString = nativeToCx((const char *)update->recipient->contactUID);
    String ^reasonString = nativeToCx(update->reason);
    TAK::Commo::MissionPackageTransferStatusUpdate ^statusCLI =
        ref new TAK::Commo::MissionPackageTransferStatusUpdate(
            update->xferid, contactString,
            nativeToCx(update->status), reasonString);
    _mpioCLI->missionPackageSendStatus(statusCLI);
}


atakmap::commoncommo::CoTPointData MissionPackageIOImpl::getCurrentPoint()
{
    auto pointCx = _mpioCLI->getCurrentPoint();
    atakmap::commoncommo::CoTPointData pointNative(pointCx->Lat,
        pointCx->Lon,
        pointCx->Hae,
        pointCx->Ce,
        pointCx->Le);
    return pointNative;
}

void MissionPackageIOImpl::createUUID(char *uuidString)
{
    auto g = ref new Platform::Guid();
    auto s = g->ToString();
    auto wstr = std::wstring(s->Data());
    auto str = std::string(wstr.begin(), wstr.end());
    const char *uuidNative = str.c_str();
    strncpy(uuidString, uuidNative, COMMO_UUID_STRING_BUFSIZE);
}
