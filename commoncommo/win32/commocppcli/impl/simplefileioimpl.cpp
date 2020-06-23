#include "simplefileioimpl.h"

using namespace TAK::Commo::impl;
using namespace System;


TAK::Commo::SimpleFileIOStatus SimpleFileIOImpl::nativeToCLI(atakmap::commoncommo::SimpleFileIOStatus status)
{
    TAK::Commo::SimpleFileIOStatus ret;
    switch (status) {
    case atakmap::commoncommo::FILEIO_INPROGRESS:
        ret = TAK::Commo::SimpleFileIOStatus::InProgress;
        break;
    case atakmap::commoncommo::FILEIO_SUCCESS:
        ret = TAK::Commo::SimpleFileIOStatus::Success;
        break;
    case atakmap::commoncommo::FILEIO_HOST_RESOLUTION_FAIL:
        ret = TAK::Commo::SimpleFileIOStatus::HostResolutionFail;
        break;
    case atakmap::commoncommo::FILEIO_CONNECT_FAIL:
        ret = TAK::Commo::SimpleFileIOStatus::ConnectFail;
        break;
    case atakmap::commoncommo::FILEIO_URL_INVALID:
        ret = TAK::Commo::SimpleFileIOStatus::UrlInvalid;
        break;
    case atakmap::commoncommo::FILEIO_URL_UNSUPPORTED:
        ret = TAK::Commo::SimpleFileIOStatus::UrlUnsupported;
        break;
    case atakmap::commoncommo::FILEIO_URL_NO_RESOURCE:
        ret = TAK::Commo::SimpleFileIOStatus::UrlNoResource;
        break;
    case atakmap::commoncommo::FILEIO_LOCAL_FILE_OPEN_FAILURE:
        ret = TAK::Commo::SimpleFileIOStatus::LocalFileOpenFailure;
        break;
    case atakmap::commoncommo::FILEIO_LOCAL_IO_ERROR:
        ret = TAK::Commo::SimpleFileIOStatus::LocalIOError;
        break;
    case atakmap::commoncommo::FILEIO_SSL_UNTRUSTED_SERVER:
        ret = TAK::Commo::SimpleFileIOStatus::SslUntrustedServer;
        break;
    case atakmap::commoncommo::FILEIO_SSL_OTHER_ERROR:
        ret = TAK::Commo::SimpleFileIOStatus::SslOtherError;
        break;
    case atakmap::commoncommo::FILEIO_AUTH_ERROR:
        ret = TAK::Commo::SimpleFileIOStatus::AuthError;
        break;
    case atakmap::commoncommo::FILEIO_ACCESS_DENIED:
        ret = TAK::Commo::SimpleFileIOStatus::AccessDenied;
        break;
    case atakmap::commoncommo::FILEIO_TRANSFER_TIMEOUT:
        ret = TAK::Commo::SimpleFileIOStatus::TransferTimeout;
        break;
    case atakmap::commoncommo::FILEIO_OTHER_ERROR:
        ret = TAK::Commo::SimpleFileIOStatus::OtherError;
        break;
    }
    return ret;
}


TAK::Commo::SimpleFileIOUpdate::
SimpleFileIOUpdate(int transferId, SimpleFileIOStatus status,
                   System::String ^info,
                   System::Int64 bytesTransferred,
                   System::Int64 expectedBytes) : transferId(transferId),
                                         status(status),
                                         additionalInfo(info),
                                         totalBytesTransferred(bytesTransferred),
                                         totalBytesToTransfer(expectedBytes)
{
}

TAK::Commo::SimpleFileIOUpdate::~SimpleFileIOUpdate()
{

}


SimpleFileIOImpl::SimpleFileIOImpl(TAK::Commo::ISimpleFileIO ^fio) : atakmap::commoncommo::SimpleFileIO(), fioCLI(fio)
{

}


SimpleFileIOImpl::~SimpleFileIOImpl()
{

}

void SimpleFileIOImpl::fileTransferUpdate(
    const atakmap::commoncommo::SimpleFileIOUpdate *update)
{
    String ^infoString = nullptr;
    if (update->additionalInfo)
        infoString = gcnew String(update->additionalInfo, 0, (int)strlen(update->additionalInfo), System::Text::Encoding::UTF8);
    int64_t bt = (int64_t)update->bytesTransferred;
    if (update->bytesTransferred > INT64_MAX)
        bt = INT64_MAX;
    int64_t tbt = (int64_t)update->totalBytesToTransfer;
    if (update->totalBytesToTransfer > INT64_MAX)
        tbt = INT64_MAX;
    
    TAK::Commo::SimpleFileIOUpdate ^updateCLI = 
        gcnew TAK::Commo::SimpleFileIOUpdate(
                update->xferid, nativeToCLI(update->status),
                infoString, bt, tbt);
    fioCLI->FileTransferUpdate(updateCLI);
}
