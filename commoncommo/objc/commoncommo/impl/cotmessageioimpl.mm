//
//  cotmessageioimpl.m
//  commoncommo
//
//  Created by Jeff Downs on 12/11/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "cotmessageioimpl.h"

using namespace atakmap::commoncommo;

objcimpl::CommoCoTMessageListenerImpl::CommoCoTMessageListenerImpl(id<CommoCoTMessageListener> objcImpl) :
                    objcImpl(objcImpl)
{
}

objcimpl::CommoCoTMessageListenerImpl::~CommoCoTMessageListenerImpl()
{
    
}

void objcimpl::CommoCoTMessageListenerImpl::cotMessageReceived(const char *cotMessage, const char *rxEndpointId)
{
    @autoreleasepool {
        NSString *objcStr = [[NSString alloc] initWithUTF8String:cotMessage];
        [objcImpl cotMessageReceived:objcStr];
    }
}

objcimpl::CommoCoTSendFailureListenerImpl::CommoCoTSendFailureListenerImpl(id<CommoCoTSendFailureListener> objcImpl) :
                    objcImpl(objcImpl)
{

}

objcimpl::CommoCoTSendFailureListenerImpl::~CommoCoTSendFailureListenerImpl()
{
    
}

void objcimpl::CommoCoTSendFailureListenerImpl::sendCoTFailure(const char *host, int port, const char *errorReason)
{
    @autoreleasepool {
        NSString *objcHost = [[NSString alloc] initWithUTF8String:host];
        NSString *objcErrorReason = [[NSString alloc] initWithUTF8String:errorReason];
        [objcImpl sendCoTFailedToHost:objcHost port:port errorReason:objcErrorReason];
    }
}




static const double PointNoValue = COMMO_COT_POINT_NO_VALUE;
@implementation CommoCoTPointData {
}

-(instancetype)initWithLat:(double) lat
                       lon:(double) lon
                       hae:(double) hae
                        ce:(double) ce
                        le:(double) le
{
    self = [super init];
    if (self) {
        _lat = lat;
        _lon = lon;
        _hae = hae;
        _ce = ce;
        _le = le;
    }
    return self;

}

/**
 * @brief initializes lat/lon to 0 and others to "no value"
 */
-(instancetype)init
{
    self = [super init];
    if (self) {
        _lat = 0;
        _lon = 0;
        _hae = PointNoValue;
        _ce = PointNoValue;
        _le = PointNoValue;
    }
    
    return self;
}

+(double) getNoValue
{
    return PointNoValue;
}

@end
