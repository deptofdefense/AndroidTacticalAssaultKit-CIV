//
//  netinterface_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 12/10/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef netinterface_objc_h
#define netinterface_objc_h


@protocol CommoNetInterface <NSObject>
@end

@protocol CommoPhysicalNetInterface <CommoNetInterface>
@property (readonly) NSData *hardwareAddress;
@end

@protocol CommoStreamingNetInterface <CommoNetInterface>
@property (readonly) NSString *streamId;
@end

@protocol CommoTcpInboundNetInterface <CommoNetInterface>
@property (readonly) int localPort;
@end

typedef NS_ENUM(NSInteger, CommoNetInterfaceErrorCode) {
    /** Name resolution for a hostname failed */
    CommoNetErrConnNameResFailed,
    /** Connection to remote host actively refused */
    CommoNetErrConnRefused,
    /** Connection remote host timed out */
    CommoNetErrConnTimeout,
    /** Remote host is known to be unreachable at this time */
    CommoNetErrConnHostUnreachable,
    /** Remote host was expected to present an SSL certificate but didn't */
    CommoNetErrConnSSLNoPeerCert,
    /** Remote host's SSL certificate was not trusted */
    CommoNetErrConnSSLPeerCertNotTrusted,
    /** SSL handshake with remote host encountered an error */
    CommoNetErrConnSSLHandshake,
    /** Some other, non-specific, error occurred during attempting
     *  to connect to a remote host
     */
    CommoNetErrConnOther,
    /** No data was received and the connection was considered
     *  in error/timed out and is being reset
     */
    CommoNetErrIORxDataTimout,
    /** A general IO error occurred */
    CommoNetErrIO,
    /** Some internal error occurred (out of memory, etc) */
    CommoNetErrInternal,
    /** Some unclassified error has occurred */
    CommoNetErrOther,
};

/**
 * @brief Protocol that an application can implement to receive notification about changes to network interface status
 * @description Protocol that can be implemented and registered with a Commo instance
 *              indicate interest in knowing about changes to the status of network
 *              interfaces.
 */
@protocol CommoInterfaceStatusListener

/**
 * @brief Invoked when an interface becomes active
 * @description Invoked when a CommoNetInterface is active and able to send or receive data.
 * For CommoStreamingNetInterfaces, this is when the server connection is made.
 * @param interface the CommoNetInterface whose status changed.
 */
-(void)interfaceUp:(id<CommoNetInterface>)interface;

/**
 * @brief Invoked when an interface is no longer usable
 * @description Invoked when a CommoNetInterface is no longer available and thus unable
 * to send or receive data. If it becomes available again in the future,
 * interfaceUp will be invoked with the same CommoNetInterface as an argument.
 * For CommoStreamingNetInterfaces, this is when the server connection is lost
 * or otherwise terminated.
 * @param interface the CommoNetInterface whose status changed.
 */
-(void)interfaceDown:(id<CommoNetInterface>)interface;

/**
 * @brief Invoked to notify of an error associated with an interface
 * @description Invoked when a CommoNetInterface makes an attempt to come online
 * but fails for any reason, or when an interface that is up is forced
 * UNEXPECTEDLY into a down state. The error code gives the reason
 * for the error.
 * A callback to this does not imply a permanent error; attempts
 * will continue to be made to bring up the interface unless it is
 * removed.
 * @param interface the CommoNetInterface on which the error occurred
 * @param errorCode indication of the error that occurred
 */
-(void) interfaceError:(id<CommoNetInterface>)interface errorCode:(CommoNetInterfaceErrorCode) errorCode;


@end



#endif /* netinterface_objc_h */
