//
//  cotmessageio_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 12/10/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef cotmessageio_objc_h
#define cotmessageio_objc_h


typedef NS_ENUM(NSInteger, CommoCoTMessageType) {
    CommoCoTMessageTypeSituationalAwareness,
    CommoCoTMessageTypeChat,
};

typedef NS_ENUM(NSInteger, CommoCoTSendMethod) {
    CommoCoTSendMethodTakServer = 1,
    CommoCoTSendMethodPointToPoint = 2,
    CommoCoTSendMethodAny = 3
};


@protocol CommoCoTMessageListener <NSObject>

-(void)cotMessageReceived:(NSString *) message;

@end


@protocol CommoCoTSendFailureListener <NSObject>

-(void)sendCoTFailedToHost:(NSString *)host port:(int) port errorReason:(NSString *) errorReason;

@end


@interface CommoCoTPointData : NSObject {
}

/**
 * @brief obtain value used to indicate "no value" for the HAE, CE, and LE properties
 */
+(double) getNoValue;
@property double lat;
@property double lon;
@property double hae;
@property double ce;
@property double le;

/**
 * @brief Initialize with specified values
 */
-(instancetype)initWithLat:(double) lat
                       lon:(double) lon
                       hae:(double) hae
                        ce:(double) ce
                        le:(double) le;

/**
 * @brief initializes lat/lon to 0 and others to "no value"
 */
-(instancetype)init;


@end


#endif /* cotmessageio_objc_h */
