#import <Foundation/Foundation.h>
#import "simplefileioimpl.h"

static CommoSimpleFileIOStatus statusToObjc(atakmap::commoncommo::SimpleFileIOStatus native) {
    
    CommoSimpleFileIOStatus objcStatus = CommoSFIOSuccess;
    switch (native) {
        case atakmap::commoncommo::FILEIO_URL_UNSUPPORTED:
            objcStatus = CommoSFIOUrlUnsupported;
            break;
        case atakmap::commoncommo::FILEIO_URL_NO_RESOURCE:
            objcStatus = CommoSFIOUrlNoResource;
            break;
        case atakmap::commoncommo::FILEIO_SUCCESS:
            objcStatus = CommoSFIOSuccess;
            break;
        case atakmap::commoncommo::FILEIO_AUTH_ERROR:
            objcStatus = CommoSFIOAuthError;
            break;
        case atakmap::commoncommo::FILEIO_INPROGRESS:
            objcStatus = CommoSFIOInProgress;
            break;
        case atakmap::commoncommo::FILEIO_OTHER_ERROR:
            objcStatus = CommoSFIOOtherError;
            break;
        case atakmap::commoncommo::FILEIO_URL_INVALID:
            objcStatus = CommoSFIOUrlInvalid;
            break;
        case atakmap::commoncommo::FILEIO_CONNECT_FAIL:
            objcStatus = CommoSFIOConnectFail;
            break;
        case atakmap::commoncommo::FILEIO_ACCESS_DENIED:
            objcStatus = CommoSFIOAccessDenied;
            break;
        case atakmap::commoncommo::FILEIO_SSL_OTHER_ERROR:
            objcStatus = CommoSFIOSslOtherError;
            break;
        case atakmap::commoncommo::FILEIO_LOCAL_FILE_OPEN_FAILURE:
            objcStatus = CommoSFIOLocalFileOpenFailure;
            break;
        case atakmap::commoncommo::FILEIO_LOCAL_IO_ERROR:
            objcStatus = CommoSFIOLocalIOError;
            break;
        case atakmap::commoncommo::FILEIO_SSL_UNTRUSTED_SERVER:
            objcStatus = CommoSFIOSslUntrustedServer;
            break;
        case atakmap::commoncommo::FILEIO_TRANSFER_TIMEOUT:
            objcStatus = CommoSFIOTransferTimeout;
            break;
        case atakmap::commoncommo::FILEIO_HOST_RESOLUTION_FAIL:
            objcStatus = CommoSFIOHostResolutionFail;
            break;
    }
    return objcStatus;
}





@implementation CommoSimpleFileIOUpdateImpl  {
    
}

-(instancetype)initWithNativeSimpleFileIOUpdate:(const atakmap::commoncommo::SimpleFileIOUpdate *) nativeImpl
{
    self = [super init];
    if (self) {
        _transferId = nativeImpl->xferid;
        _status = statusToObjc(nativeImpl->status);
        _additionalInfo = nativeImpl->additionalInfo ? [NSString stringWithUTF8String:nativeImpl->additionalInfo] : nil;
        _totalBytesToTransfer = nativeImpl->totalBytesToTransfer;
        _totalBytesTransferred = nativeImpl->bytesTransferred;
    }
    return self;
}

-(instancetype)init
{
    return nil;
}

@end



using namespace atakmap::commoncommo;

objcimpl::SimpleFileIOImpl::SimpleFileIOImpl(id<CommoSimpleFileIO> objcImpl) :
        SimpleFileIO(), objcImpl(objcImpl)
{
    
}

objcimpl::SimpleFileIOImpl::~SimpleFileIOImpl()
{
    
}


void objcimpl::SimpleFileIOImpl::fileTransferUpdate(const SimpleFileIOUpdate *update)
{
    @autoreleasepool {
        CommoSimpleFileIOUpdateImpl *objcUpdate = [[CommoSimpleFileIOUpdateImpl alloc] initWithNativeSimpleFileIOUpdate:update];
        [objcImpl fileTransferUpdate:objcUpdate];
    }
}

