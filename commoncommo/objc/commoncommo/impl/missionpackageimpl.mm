//
//  missionpackageimpl.mm
//  commoncommo
//
//  Created by Jeff Downs on 3/17/16.
//  Copyright © 2016 Jeff Downs. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "missionpackageimpl.h"

static CommoMissionPackageTransferStatus statusToObjc(atakmap::commoncommo::MissionPackageTransferStatus native) {
    
    CommoMissionPackageTransferStatus objcStatus = CommoTransferFinishedSuccess;
    switch (native) {
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_SUCCESS:
            objcStatus = CommoTransferFinishedSuccess;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_TIMED_OUT:
            objcStatus = CommoTransferFinishedTimedOut;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_CONTACT_GONE:
            objcStatus = CommoTransferFinishedContactGone;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_FAILED:
            objcStatus = CommoTransferFinishedFailed;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_FILE_EXISTS:
            objcStatus = CommoTransferFinishedFileExists;
            break;
        case atakmap::commoncommo::MP_TRANSFER_FINISHED_DISABLED_LOCALLY:
            objcStatus = CommoTransferFinishedDisabledLocally;
            break;
        case atakmap::commoncommo::MP_TRANSFER_ATTEMPT_IN_PROGRESS:
            objcStatus = CommoTransferAttemptInProgress;
            break;
        case atakmap::commoncommo::MP_TRANSFER_ATTEMPT_FAILED:
            objcStatus = CommoTransferAttemptFailed;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_PENDING:
            objcStatus = CommoTransferServerUploadPending;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS:
            objcStatus = CommoTransferServerUploadInProgress;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_SUCCESS:
            objcStatus = CommoTransferServerUploadSuccess;
            break;
        case atakmap::commoncommo::MP_TRANSFER_SERVER_UPLOAD_FAILED:
            objcStatus = CommoTransferServerUploadFailed;
            break;
    }
    return objcStatus;
}





@implementation CommoMissionPackageSendStatusUpdateImpl  {
    
}

-(instancetype)initWithNativeMissionPackageSendStatusUpdate:(const atakmap::commoncommo::MissionPackageSendStatusUpdate *)nativeImpl
{
    self = [super init];
    if (self) {
        _xferid = nativeImpl->xferid;
        _recipient = nativeImpl->recipient ? [[NSString alloc] initWithBytes:nativeImpl->recipient->contactUID length:nativeImpl->recipient->contactUIDLen                                                                                   encoding:NSUTF8StringEncoding] : nil;
        _status = statusToObjc(nativeImpl->status);
        _additionalDetail = nativeImpl->additionalDetail ? [[NSString alloc] initWithUTF8String:nativeImpl->additionalDetail] : nil;
        _totalBytesTransferred = nativeImpl->totalBytesTransferred;
    }
    return self;
}

-(instancetype)init
{
    return nil;
}

@end


@implementation CommoMissionPackageReceiveStatusUpdateImpl  {
    
}

-(instancetype)initWithNativeMissionPackageReceiveStatusUpdate:(const atakmap::commoncommo::MissionPackageReceiveStatusUpdate *)nativeImpl
{
    self = [super init];
    if (self) {
        _localFile = [[NSString alloc] initWithUTF8String:nativeImpl->localFile];
        _status = statusToObjc(nativeImpl->status);
        _totalBytesReceived = nativeImpl->totalBytesReceived;
        _totalBytesExpected = nativeImpl->totalBytesExpected;
        _attempt = nativeImpl->attempt;
        _maxAttempts = nativeImpl->maxAttempts;
        _errorDetail = nativeImpl->errorDetail ? [[NSString alloc] initWithUTF8String:nativeImpl->errorDetail] : nil;
    }
    return self;
}

-(instancetype)init
{
    return nil;
}

@end




using namespace atakmap::commoncommo;

objcimpl::MissionPackageIOImpl::MissionPackageIOImpl(id<CommoMissionPackageIO> objcImpl) :
        MissionPackageIO(), objcImpl(objcImpl)
{
    
}

objcimpl::MissionPackageIOImpl::~MissionPackageIOImpl()
{
    
}


MissionPackageTransferStatus objcimpl::MissionPackageIOImpl::missionPackageReceiveInit(
                                                               char *destFile, size_t destFileSize,
                                                               const char *transferName, const char *sha256hash,
                                                               uint64_t fileSize, const char *senderCallsign)
{
    @autoreleasepool {
        NSMutableString *objcDestFile = [[NSMutableString alloc] initWithUTF8String:destFile];
        NSString *objcTransferName = [[NSString alloc] initWithUTF8String:transferName];
        NSString *objcHash = [[NSString alloc] initWithUTF8String:sha256hash];
        NSString *objcCallsign = [[NSString alloc] initWithUTF8String:senderCallsign];
        
        
        CommoMissionPackageTransferStatus objcStatus = [objcImpl missionPackageReceiveInitWithDestFile:objcDestFile transferName:objcTransferName sha256hash:objcHash sizeInBytes:destFileSize senderCallsign:objcCallsign];
        
        MissionPackageTransferStatus nativeRet = commoTransferStatusFromObjc(objcStatus);
        if (objcStatus == CommoMissionPackageTransferStatus::CommoTransferFinishedSuccess) {
            // Copy back filename
            const char *nativeString = [objcDestFile UTF8String];
            size_t nativeLen = strlen(nativeString);
            if (destFileSize <= nativeLen)
                nativeRet = MP_TRANSFER_FINISHED_FAILED;
            else {
                strcpy(destFile, nativeString);
                destFileSize = nativeLen;
                nativeRet = MP_TRANSFER_FINISHED_SUCCESS;
            }
        }
        return nativeRet;
    }
}

void objcimpl::MissionPackageIOImpl::missionPackageReceiveStatusUpdate(const MissionPackageReceiveStatusUpdate *update)
{
    @autoreleasepool {
        CommoMissionPackageReceiveStatusUpdateImpl *impl = [[CommoMissionPackageReceiveStatusUpdateImpl alloc] initWithNativeMissionPackageReceiveStatusUpdate:update];
        [objcImpl missionPackageReceiveStatusUpdate:impl];
    }
}

void objcimpl::MissionPackageIOImpl::missionPackageSendStatusUpdate(const MissionPackageSendStatusUpdate *update)
{
    @autoreleasepool {
        CommoMissionPackageSendStatusUpdateImpl *impl = [[CommoMissionPackageSendStatusUpdateImpl alloc] initWithNativeMissionPackageSendStatusUpdate:update];
        [objcImpl missionPackageSendStatusUpdate:impl];
    }
}

CoTPointData objcimpl::MissionPackageIOImpl::getCurrentPoint()
{
    @autoreleasepool {
        CommoCoTPointData *objcPoint = [objcImpl getCurrentPoint];
        CoTPointData nativePt(objcPoint.lat, objcPoint.lon, objcPoint.hae, objcPoint.ce, objcPoint.le);
        return nativePt;
    }
}

void objcimpl::MissionPackageIOImpl::createUUID(char *uuidString)
{
    @autoreleasepool {
        NSString *objcVal = [objcImpl createUUID];
        const char *nativeVal = [objcVal UTF8String];
        strncpy(uuidString, nativeVal, COMMO_UUID_STRING_BUFSIZE-1);
        uuidString[COMMO_UUID_STRING_BUFSIZE - 1] = '\0';
    }
}


MissionPackageTransferStatus objcimpl::MissionPackageIOImpl::commoTransferStatusFromObjc(CommoMissionPackageTransferStatus objcStatus) {
    
    MissionPackageTransferStatus ret = MP_TRANSFER_FINISHED_FAILED;
    switch (objcStatus) {
        case CommoTransferFinishedSuccess:
            ret = MP_TRANSFER_FINISHED_SUCCESS;
            break;
        case CommoTransferFinishedTimedOut:
            ret = MP_TRANSFER_FINISHED_TIMED_OUT;
            break;
        case CommoTransferFinishedContactGone:
            ret = MP_TRANSFER_FINISHED_CONTACT_GONE;
            break;
        case CommoTransferFinishedFailed:
            ret = MP_TRANSFER_FINISHED_FAILED;
            break;
        case CommoTransferFinishedFileExists:
            ret = MP_TRANSFER_FINISHED_FILE_EXISTS;
            break;
        case CommoTransferFinishedDisabledLocally:
            ret = MP_TRANSFER_FINISHED_DISABLED_LOCALLY;
            break;
        case CommoTransferAttemptInProgress:
            ret = MP_TRANSFER_ATTEMPT_IN_PROGRESS;
            break;
        case CommoTransferAttemptFailed:
            ret = MP_TRANSFER_ATTEMPT_FAILED;
            break;
        case CommoTransferServerUploadPending:
            ret = MP_TRANSFER_SERVER_UPLOAD_PENDING;
            break;
        case CommoTransferServerUploadInProgress:
            ret = MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS;
            break;
        case CommoTransferServerUploadSuccess:
            ret = MP_TRANSFER_SERVER_UPLOAD_SUCCESS;
            break;
        case CommoTransferServerUploadFailed:
            ret = MP_TRANSFER_SERVER_UPLOAD_FAILED;
            break;
    }
    return ret;
}
