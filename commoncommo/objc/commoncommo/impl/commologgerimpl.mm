//
//  commologgerimpl.m
//  commoncommo
//
//  Created by Jeff Downs on 12/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "commologgerimpl.h"

using namespace atakmap::commoncommo;

objcimpl::CommoLoggerImpl::CommoLoggerImpl(id<CommoLogger> objcImpl) :
                objcImpl(objcImpl)
{
}

objcimpl::CommoLoggerImpl::~CommoLoggerImpl()
{
    
}

void objcimpl::CommoLoggerImpl::log(Level level, const char *message)
{
    @autoreleasepool {
        NSString *objcStr = [[NSString alloc] initWithUTF8String:message];
        CommoLoggerLevel objcLevel = CommoLoggerLevelInfo;
        switch (level) {
            case LEVEL_VERBOSE:
                objcLevel = CommoLoggerLevelVerbose;
                break;
            case LEVEL_DEBUG:
                objcLevel = CommoLoggerLevelDebug;
                break;
            case LEVEL_WARNING:
                objcLevel = CommoLoggerLevelWarning;
                break;
            case LEVEL_INFO:
                objcLevel = CommoLoggerLevelInfo;
                break;
            case LEVEL_ERROR:
                objcLevel = CommoLoggerLevelError;
                break;
        }
        
        [objcImpl logMessage:objcStr withLevel:objcLevel];
    }
}

