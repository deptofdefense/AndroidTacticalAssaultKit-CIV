//
//  contactimpl.mm
//  commoncommo
//
//  Created by Jeff Downs on 12/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "contactuidimpl.h"

using namespace atakmap::commoncommo;

objcimpl::CommoContactPresenceListenerImpl::CommoContactPresenceListenerImpl(id<CommoContactPresenceListener> objcImpl) :
                objcImpl(objcImpl)
{
}

objcimpl::CommoContactPresenceListenerImpl::~CommoContactPresenceListenerImpl()
{
    
}

void objcimpl::CommoContactPresenceListenerImpl::contactAdded(const atakmap::commoncommo::ContactUID *c)
{
    @autoreleasepool {
        NSString *objcStr = [[NSString alloc] initWithBytes:c->contactUID length:c->contactUIDLen
                                                   encoding:NSUTF8StringEncoding];
        [objcImpl contactAdded:objcStr];
    }
}

void objcimpl::CommoContactPresenceListenerImpl::contactRemoved(const atakmap::commoncommo::ContactUID *c)
{
    @autoreleasepool {
        NSString *objcStr = [[NSString alloc] initWithBytes:c->contactUID length:c->contactUIDLen
                                                   encoding:NSUTF8StringEncoding];
        [objcImpl contactRemoved:objcStr];
    }
}
