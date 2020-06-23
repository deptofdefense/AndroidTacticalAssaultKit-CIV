//
//  missionpackageimpl.h
//  commoncommo
//
//  Created by Jeff Downs on 3/17/16.
//  Copyright © 2016 Jeff Downs. All rights reserved.
//

#ifndef missionpackageimpl_h
#define missionpackageimpl_h

#import "missionpackage_objc.h"
#include <missionpackage.h>


namespace atakmap {
    namespace commoncommo {
        namespace objcimpl {
            class MissionPackageIOImpl : public atakmap::commoncommo::MissionPackageIO
            {
            public:
                MissionPackageIOImpl(id<CommoMissionPackageIO> objcImpl);
                virtual ~MissionPackageIOImpl();
                
                
                virtual MissionPackageTransferStatus missionPackageReceiveInit(
                                                                               char *destFile, size_t destFileSize,
                                                                               const char *transferName,
                                                                               const char *sha256hash,
                                                                               uint64_t fileSize,
                                                                               const char *senderCallsign);
                virtual void missionPackageReceiveStatusUpdate(const MissionPackageReceiveStatusUpdate *update);
                virtual void missionPackageSendStatusUpdate(const MissionPackageSendStatusUpdate *update);
                virtual CoTPointData getCurrentPoint();
                virtual void createUUID(char *uuidString);
                static MissionPackageTransferStatus commoTransferStatusFromObjc(CommoMissionPackageTransferStatus objcStatus);
                
            private:
                id<CommoMissionPackageIO> objcImpl;
            };
        }
    }
}


@interface CommoMissionPackageReceiveStatusUpdateImpl : NSObject <CommoMissionPackageReceiveStatusUpdate> {
}
@property (readonly) NSString *localFile;
@property (readonly) CommoMissionPackageTransferStatus status;
@property (readonly) int64_t totalBytesReceived;
@property (readonly) int64_t totalBytesExpected;
@property (readonly) int attempt;
@property (readonly) int maxAttempts;
@property (readonly) NSString *errorDetail;

-(instancetype)initWithNativeMissionPackageReceiveStatusUpdate:(const atakmap::commoncommo::MissionPackageReceiveStatusUpdate *) nativeImpl;
-(instancetype)init;

@end


@interface CommoMissionPackageSendStatusUpdateImpl : NSObject <CommoMissionPackageSendStatusUpdate> {
}
@property (readonly) int xferid;
@property (readonly) NSString *recipient;
@property (readonly) CommoMissionPackageTransferStatus status;
@property (readonly) NSString *additionalDetail;
@property (readonly) int64_t totalBytesTransferred;

-(instancetype)initWithNativeMissionPackageSendStatusUpdate:(const atakmap::commoncommo::MissionPackageSendStatusUpdate *) nativeImpl;
-(instancetype)init;

@end


#endif /*  */
