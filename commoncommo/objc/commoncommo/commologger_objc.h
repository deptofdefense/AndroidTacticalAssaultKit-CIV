//
//  commologger_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 12/10/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef commologger_objc_h
#define commologger_objc_h


typedef NS_ENUM(NSInteger, CommoLoggerLevel) {
    CommoLoggerLevelVerbose,
    CommoLoggerLevelDebug,
    CommoLoggerLevelWarning,
    CommoLoggerLevelInfo,
    CommoLoggerLevelError,
};

/**
 * Protocol used to log messages from the Commo library.
 */
@protocol CommoLogger <NSObject>

/**
 * log a message of the specified severity level.
 * This callback will be invoked from multiple different threads; an implementation
 * must be able to handle this properly.
 */
-(void)logMessage:(NSString *)message withLevel:(CommoLoggerLevel)level;

@end


#endif /* commologger_objc_h */
