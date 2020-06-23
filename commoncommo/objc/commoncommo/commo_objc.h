//
//  commo_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 12/10/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef commo_objc_h
#define commo_objc_h

#import "commologger_objc.h"
#import "netinterface_objc.h"
#import "contactuid_objc.h"
#import "commoresult_objc.h"
#import "cotmessageio_objc.h"
#import "missionpackage_objc.h"
#import "simplefileio_objc.h"


/**
 * @brief Main interface to the Commo library
 * @discussion This class is the main interface into the Commo library.  An instance
 * of this class will provide most functionality of the library.
 * As a general rule, objects implementing any of the listener protocols 
 * must remain valid from the time they are added until either they are removed
 * or this Commo object is destroyed.
 *
 * Before creating the first instance of a Commo object, callers must externally
 * initialize libxml2, openssl, and libcurl. In particular, openssl must be configured to support
 * multiple threads with appropriate locking constructs configured.  See example apps.
 */
@interface Commo : NSObject {
}

/**
 * @brief constant to pass to setMissionPackageLocalPort to indicate that
 * the local mission package serving web server should be disabled
 */
extern const int MPIO_LOCAL_PORT_DISABLE;

/// @brief calls close to clean up native resources
-(void) dealloc;

/**
 * @brief cleans up all native resources associated with this Commo object.
 * @discussion Once this method is invoked, no other methods may be invoked on this object. As a side-effect
 *      all registered listeners are deallocated and the caller my safely discard them upon completion of
 *      this method.  It is safe to call this multiple times.
 */
-(void) close;

/** 
 * @brief Initialize this Commo object with the specified logger implementation, uid, and callsign (all required)
 * @param logger specifies the logging implementation to send log messages to
 * @param deviceUid specifies the uid of the device instantiating the Commo instance
 * @param callSign specifies the callsign of the device instantiating the Commo instance
 */
-(instancetype)initWithLogger:(id<CommoLogger>)logger
                    deviceUid:(NSString *) uid
                     callSign:(NSString *) callSign;

/// @brief Not currently implemented
-(instancetype)init;

/**
 * @brief Sets the global TTL used for broadcast messages
 * @discussion The value of this property will set the TTL used for all future
 *             broadcast messages send out all broadcast interfaces managed by this
 *             Commo instance. Value out of range will be clamped internally to a valid
 *             value.
 */
@property (nonatomic) int broadcastTTL;

/**
 * @brief Sets global UDP timeout value, in seconds.
 * @discussion if the given # of seconds
 * elapses without a socket receiving data, it will be destroyed
 * and rebuilt. If the timed-out socket was a multicast socket,
 * multicast joins will be re-issued.
 * 0 means never timeout. Default is 30 seconds.
 */
@property (nonatomic) int udpNoDataTimeoutSec;

/**
 * @brief Controls the TCP connection timeout used for all outgoing TCP connections
 * @discussion Controls the global timeout, in seconds, used for all outgoing
 * TCP connections (except those used to upload or download mission packages).
 * Default value is 20. Minimum value is 2.
 */
@property (nonatomic) int tcpConnTimeoutSec;

/**
 * @brief Controls if mission package sends via TAK servers may or may
 * not be used.
 * @discussion The default, once mission package IO has been setup
 * via setupMissionPackageIO(), is enabled.
 * Note carefully: Disabling this after it was previously enabled
 * may cause some existing outbound mission package transfers to be
 * aborted!
 */
@property (nonatomic) bool missionPackageViaServerEnabled;

/**
 * @brief Controls the number of tries used to download mission packages
 * @discussion Controls the number of attempts to receive a mission package
 * that a remote device has given us a request to download.
 * Once this number of attempts has been exceeded, the transfer
 * will be considered failed. Minimum value is 1.
 * The default value is 10.  Values out of range will result in the value
 * not being modified.
 */
@property (nonatomic) int missionPackageNumTries;

/**
 * @brief Controls the connection timeout used when attempting to transfer mission packages
 * @discussion Controls the timeout, in seconds, for initiating connections to remote
 * hosts when transferring mission packages.  This is used when
 * receiving mission packages from TAK servers and/or other devices,
 * as well as uploading mission packages to TAK servers.
 * The minimum value is five (5) seconds; default value is 90 seconds.
 * New settings influence sunsequent transfers; currently ongoing transfers
 * use settings in place at the time they began.
 * Out of range values result in the value not being modified.
 */
@property (nonatomic) int missionPackageConnTimeoutSec;

/**
 * @brief Controls the transfer timeout value used when attempting to transfer mission packages
 * @discussion Controls the timeout, in seconds, for data transfers. After this number
 * of seconds has elapsed with no data transfer progress taking place,
 * the transfer attempt is considered a failure.
 * This is used when receiving mission packages.
 * Minimum value is 15 seconds, default value is 120 seconds.
 * New settings influence sunsequent transfers; currently ongoing transfers
 * use settings in place at the time they began.
 * Out of range values result in the value not being modified.
 */
@property (nonatomic) int missionPackageTransferTimeoutSec;

/**
 * @brief Controls if streams should be monitored
 * @discussion Indicates if stream monitoring is to be performed or not.
 * If enabled, Commo will monitor incoming traffic on each
 * configured and active streaming interface.  If no traffic
 * arrives for a substantive period of time, commo will
 * send a "ping" CoT message to the streaming server and expect
 * a response. This will repeat for a short time, and, if
 * no reply is received, the streaming connection will be
 * shut down and an attempt to re-establish the connection
 * will be made a short time later.
 * Server "pong" responses, if they arrive, are never delivered
 * to the application's CoTMessageListener.
 * Default is enabled.
 */
@property (nonatomic) bool streamMonitorEnabled;

/**
 * @brief Sets up mission package send and receive functionality
 * @discussion Configures this Commo instance to use the given MissionPackageIO
 *             protocol implementation to assist in sending and receiving mission
 *             packages. The caller must provide
 *             an implementation of the CommoMissionPackageIO protocol that
 *             implements various functions related to sending and receiving
 *             packages.
 *             This function can only be successfully invoked once; once
 *             mission package IO is configured, it cannot be swapped out or removed.
 *             The implementation must remain valid and operable until close() is invoked.
 * @param mpio an implementation of the CommoMissionPackageIO protocol used when
 *             mission packages are sent and received
 * @return Success if IO is configured to use the given MissionPackageIO implementation,
 *         or IllegalArgument if missionPackageIO is nil,
 *         or if setupMissionPackageIO has previously been called successfully
 *         on this Commo instance.
 */
-(CommoResult) setupMissionPackageIO:(id<CommoMissionPackageIO>) mpio;

/**
 * @brief Enables simple file transfers for this Commo instance.
 * @discussion Simple file transfers are transfers of files via traditional
 * client-server protocols (where commo is the client)
 * and is not associated with CoT sending/receiving or
 * mission package management.
 * Once enabled, transfers cannot be disabled.
 * The caller must supply an implementation of the
 * SimpleFileIO interface that will remain operable
 * until shutdown() is invoked.
 * After a successful return, simpleFileTransferInit()
 * can be invoked to initiate file transfers with an external server.
 *
 * @param simpleIO reference to an implementation of
 *                         the SimpleFileIO interface that
 *                         this Commo instance can use to interface
 *                         with the application during simple file
 *                         transfers
 * @return IllegalArgument if simpleIO is null or if this method has
 *        previously been called successfully for this Commo instance, Success otherwise
 */
-(CommoResult) enableSimpleFileIO:(id<CommoSimpleFileIO>) fileio;

/**
 * @brief Sets the callsign of the local system.
 * @discussion this method may be used to change the local system's
 *             callsign after initialization.
 */
-(void) setCallsign:(NSString *) callSign;

/**
 * @brief Sets cryptographic keys to use for non-server CoT traffic
 * @discussion  Enables encryption/decryption for non-streaming 
 *              CoT interfaces using the specified keys, or 
 *              disables it entirely.* Arrays MUST be precisely
 *              32 bytes long and contain unique keys.
 *              Specify null for both to disable encryption/decryption
 *              (the default).
 * @return IllegalArgument if the keys are not the right length, are
 *         the same, or one is null and the other is not;  
 *         returns Success otherwise.
 * @param auth Authentication key
 * @param crypt Cryptographic key
 */
-(CommoResult) setCryptoKeysToAuth:(NSData *) auth crypt:(NSData *)crypt;

/**
 * @brief Sets the local web server port to use for outbound peer-to-peer
 * mission package transfers, or disables this functionality.
 * @discussion The local web port number must be a valid, free, local port
 * for TCP listening or the constant MPIO_LOCAL_PORT_DISABLE.
 * On successful return, the server will be active on the specified port
 * and new outbound transfers (sendMissionPackage()) will use
 * this to host outbound transfers.
 * If this call fails, transfers using the local web server will be
 * disabled until a future call completes successfully (in other words,
 * will act as if this had been called with MPIO_LOCAL_PORT_DISABLE).
 *
 * Note carefully: calling this to change the port or disable the local
 * web server will cause all in-progress mission package sends to
 * be aborted.
 *
 * The default state is to have the local web server disabled, as if
 * this had been called with MPIO_LOCAL_PORT_DISABLE.
 *
 * @param localWebPort TCP port number to have the local web server
 *                     listen on, or MPIO_LOCAL_PORT_DISABLE.
 *                     The port must be free at the time of invocation.
 * @return IllegalArgument if the web port is not valid/available or
 *     setupMissionPackageIO has not yet been successfully invoked. Success if
 *     the new port is successfully configured.
 */
-(CommoResult) setMissionPackageLocalPort:(int) localWebPort;

/**
 * Sets the TCP port number to use when contacting a TAK
 * Server's https web api. Default is port 8443.
 *
 * @param serverPort tcp port to use
 * @return IllegalArgument if the port number is out of range, Success if the new port
 *                         is accepted
 */
-(CommoResult) setMissionPackageHttpsPort:(int) serverPort;

/**
 * @brief Gets the version of TAK protocol currently in use by
 * broadcasts.
 * @discussion Version 0 is legacy XML, versions > 0 are binary TAK protocol
 */
-(int) getBroadcastProto;

/**
 * @brief add a new outbound broadcasting interface
 * @discussion directs the Commo instance to monitor the local network interface with the 
 *             given name and, when the interface is active, send broadcast messages
 *             matching any of the given message types out said interface to the specific multicast
 *             address and port.  The returned interface is valid until removed via removeBroadcastInterface
 *             or this Commo object is closed.
 * @return an internal object implementing the CommoPhysicalNetInterface protocol, or nil if an error
 *             occurs.
 */
-(id<CommoPhysicalNetInterface>) addBroadcastInterfaceWithName:(NSString *) name
                                                        cotMessageTypes:(NSArray<NSNumber *> *) cotTypes
                                                           mcastAddress:(NSString *) mcast
                                                               destPort:(int) destPort;
/**
 * @brief add a new outbound broadcasting interface
 * @discussion directs the Commo instance to send broadcast messages
 *             matching any of the given message types out to the specified unicast
 *             address and port.  The returned interface is valid until removed via removeBroadcastInterface
 *             or this Commo object is closed.
 * @return an internal object implementing the CommoPhysicalNetInterface protocol, or nil if an error
 *             occurs.
 */
-(id<CommoPhysicalNetInterface>) addBroadcastInterfaceForMessageTypes:(NSArray<NSNumber *> *) cotTypes
                                                           unicastAddress:(NSString *) unicast
                                                               destPort:(int) destPort;
/**
 * @brief Removes a previously added broadcast interface.
 * @discussion Upon completion of this method, no further data will be broadcast to the specified 
 *             interface.
 * @return Success if the interface was successfully removed, or IllegalArgument if the argument
 *         is not a valid CommoPhysicalNetInterface obtained by previous call to addBroadcastInterface.
 */
-(CommoResult) removeBroadcastInterface:(id<CommoPhysicalNetInterface>) iface;
                                            
// nil on error

/**
 * @brief add a new listening interface
 * @discussion directs the Commo instance to monitor the local network interface with the
 *             given name and, when the interface is active, listen on the specified local
 *             port for CoT traffic. Additionally, subscribe to the multicast groups specified to receive
 *             traffic directed at those multicast addresses.
 *             The returned interface is valid until removed via removeInboundInterface
 *             or this Commo object is closed.
 * @return an internal object implementing the CommoPhysicalNetInterface protocol, or nil if an error
 *             occurs.
 */
-(id<CommoPhysicalNetInterface>) addInboundInterfaceWithName:(NSString *) ifaceName
                                                                   localPort:(int) port
                                                                  mcastAddrs:(NSArray<NSString *> *) mcastAddrs;

/**
 * @brief Removes a previously added inbound interface.
 * @discussion Upon completion of this method, no further data will be received from the specified
 *             interface.
 * @return Success if the interface was successfully removed, or IllegalArgument if the argument
 *         is not a valid CommoPhysicalNetInterface obtained by previous call to addInboundInterface.
 */
-(CommoResult) removeInboundInterface:(id<CommoPhysicalNetInterface>) iface;


/**
 * @brief Adds a new inbound connection-oriented TCP interface on the specified port
 * @discussion Adds a new inbound TCP-based listening interface on the specified port.
 * Listens on all network interfaces. The interface will generally
 * be up most of the time, but may go down if there is some low-level
 * system error. In this event, commo will attempt to re-establish
 * as a listener on the given port.
 *
 * @param port the local port number to listen on
 * @return CommoTcpInboundNetInterface object uniquely identifying the
 *         added interface.  Remains valid as long as it is
 *         not passed to removeTcpInboundInterface, or nil if an error occurs
 *         (including but not limited to port out of range or already in use)
 */

-(id<CommoTcpInboundNetInterface>) addTcpInboundInterfaceWithLocalPort:(int) port;

/**
 * @brief Removes a previously added tcp inbound interface
 * Remove the specified inbound tcp-based interface from being
 * used for message reception.
 * The iface can be removed regardless of if it is in the up or down state.
 * When this call completes, the interface will no longer be used.
 * However it is possible that previously received messages may be passed
 * to CoTMessageListeners during execution of this method.
 * After completion, the supplied CommoTcpInboundNetInterface is
 * considered invalid.
 *
 * @param iface previously added CommoTcpInboundNetInterface to remove from service
 * @return Success if the interface is successfully removed, or IllegalArgument
 *         if the supplied NetInterface was not created via addTcpInboundInterface() or
 *         was already removed
 */
-(CommoResult) removeTcpInboundInterface:(id<CommoTcpInboundNetInterface>) iface;


                                                                                      
/**
 * @brief add a new streaming interface to a remote TAK server
 * @discussion directs the Commo instance to attempt to establish and maintain a connection
 *             with a TAK server at the specified hostname on the specified remote port. The
 *             connection will be used to receive CoT messages of all types from the TAK server,
 *             for non-broadcast sending of all types of CoT messages to specific contacts, as well
 *             as for broadcasting CoTMessages of the types specified. Depending on the arguments given,
 *             attempts to connect will be made with plain TCP, SSL encryted TCP, or SSL encrypted with
 *             server authentication: specifying nil for clientCertBytes will result in a plain TCP connection,
 *             specifying non-nil for clientCertBytes requires non-nil for trustStoreBytes and will result in
 *             an SSL connection, and specifying non-nil clientCertBytes, trustStoreBytes, username, and password
 *             will result in an SSL connection that sends a TAK server authentication message when connecting.
 *             Regardless of the type of connection, the Commo instance will attempt to create and restore the
 *             server connection as needed until this interface is removed or this Commo object is closed.
 *             The returned interface is valid until removed via removeStreamingInterface
 *             or this Commo object is closed.
 * @param hostname hostname or string-form of IP address of the TAK server to connect to
 * @param port port number of the TAK server to connect to
 * @param cotTypes the types of CoT messages that can be broadcast out this interface. An empty array is acceptable
 * @param clientCert bytes comprising the client's certificate for use in SSL connection. If nil, plain TCP connections
 *                   are used.  If non-nil, trustStore MUST also be specified. The bytes must comprise a valid PKCS#12
 *                   certificate store.
 * @param trustStore bytes comprising the certificate(s) that the client will accept as trusted for signing the certificate
 *                   presented to the client by the server during connection. The bytes must comprise a valid PKCS#12
 *                   certificate store.
 * @oaram certPassword password to decrypt the client's certificate.  May be nil if no password is needed.
 * @oaram trustStorePassword password to decrypt the client's trust store.  May be nil if no password is needed.
 * @param username if non-nil, an authentication message with the username and password
 *                 specified will be sent to the TAK server upon connection.  if non-nil, password must also be non-nil
 * @param password the accompanying password to go with the username in the authentication message
 * @param errorCode if non-nil will be filled with result code indicating any error that occurs
 * @return an internal object implementing the CommoStreamingNetInterface protocol, or nil if an error
 *             occurs.
 */-(id<CommoStreamingNetInterface>) addStreamingInterfaceWithHostname:(NSString *)hostname
                                                    destPort:(int) port
                                             cotMessageTypes:(NSArray<NSNumber *> *) cotTypes
                                             clientCertBytes:(NSData *) clientCert
                                             trustStoreBytes:(NSData *) trustStore
                                                certPassword:(NSString *) certPassword
                                          trustStorePassword:(NSString *) trustStorePassword
                                                    username:(NSString *) username
                                                    password:(NSString *) password
                                                   errorCode:(CommoResult *) errorCode;

/**
 * @brief Removes a previously added streaming interface.
 * @discussion Upon completion of this method, no further data will be sent or received from the specified
 *             interface.
 * @return Success if the interface was successfully removed, or IllegalArgument if the argument
 *         is not a valid CommoStreamingNetInterface obtained by previous call to addStreamingInterface.
 */
-(CommoResult) removeStreamingInterface:(id<CommoStreamingNetInterface>) iface;
                                                                                                                                   
/**
 * @brief register a CommoInterfaceStatusListener to receive updates on interface status
 * @return Success if listener is added successfully, IllegalArgument if the listener
 *         is already registered or another error occurs.
 */
-(CommoResult) addInterfaceStatusListener:(id<CommoInterfaceStatusListener>) listener;

/**
 * @brief deregister a CommoInterfaceStatusListener; upon completion it will no longer receive
 *        interface status updates.
 * @return Success if listener is removed successfully, IllegalArgument if the listener
 *         was not previously registered or another error occurs.
 */
-(CommoResult) removeInterfaceStatusListener:(id<CommoInterfaceStatusListener>) listener;

/**
 * @brief register a CommoCoTMessageListener to receive CoT messages
 * @discussion The specified CommoCoTMessageListener will be registered to receive
 *             the CoT messages of all types which are received on any currently listening
 *             interfaces.
 * @return Success if listener is added successfully, IllegalArgument if the listener
 *         is already registered or another error occurs.
 */
-(CommoResult) addCoTMessageListener:(id<CommoCoTMessageListener>) listener;

/**
 * @brief deregister a CommoCoTMessageListener; upon completion it will no longer receive
 *        CoT messages.
 * @return Success if listener is removed successfully, IllegalArgument if the listener
 *         was not previously registered or another error occurs.
 */
-(CommoResult) removeCoTMessageListener:(id<CommoCoTMessageListener>) listener;

/**
 * @brief register a CommoCoTSendFailureListener to receive advisory notification of detectable transmission errors
 * @discussion Adds an instance of CommoCoTSendFailureListener which desires to be notified
 * if a failure to send a CoT message to a specific contact or TCP
 * endpoint is detected. See CommoCoTSendFailureListener.
 *
 * @param listener the listener to add
 * @return Success if the listener is added succesffully, IllegalArgument
 *         if the specified listener was already added
 */
-(CommoResult) addCoTSendFailureListener:(id<CommoCoTSendFailureListener>) listener;

/**
 * @brief deregister a CommoCoTSendFailureListener
 * @discussion Removes a previously added instance of CommoCoTSendFailureListener;
 * upon completion of this method, the listener will no longer
 * receive any further failure notifications.  The listener may
 * receive events while this method is being executed.
 *
 * @param listener the listener to remove
 * @return Success if listener is removed successfully, IllegalArgument if the listener
 *         was not previously registered or another error occurs.
 */
-(CommoResult) removeCoTSendFailureListener:(id<CommoCoTSendFailureListener>) listener;

/**
 * @brief send the specified message to the contact(s) with the specified uids
 * @discussion Sends a CoT message to one or more contacts by specifying the contacts with their UIDs.
 *             Any known and available method is used to communicate with them.
 *             The message is queued for transmission immediately, but the
 *             actual transmission is done asynchronously. Because of the
 *             nature of CoT messaging, there may be no indication returned
 *             of the success or failure of the transmission. If the means
 *             by which the destination contact(s) is/are reachable allows for
 *             error detection, transmission errors will be posted to any
 *             CoTSendFailureListeners that are registered with this Commo instance.
 *             If one or more of the contacts is known to be unreachable at the time of this call, the
 *             message is sent to those contacts that are available and ContactGone is returned. In this
 *             situation, the destinations array is updated to include only those contacts which were
 *             known to be gone.
 * @param destinations one or more UIDs specifying the contacts to send to. On return, the array may be modified to
 *                     indicate gone contacts
 * @param cotMessage the CoT message to send
 * @return Success if the message was queued for send to all contacts successfully, ContactGone if one or more
 *         of the contacts are known to no longer be reachable, or IllegalArgument if one or more
 *         arguments are invalid (particularly if the cot message format is unknown)
 */
-(CommoResult) sendCoTToContactUIDs:(NSMutableArray<NSString *> *) destinations messageBytes:(NSString *) cotMessage;

/**
 * @brief send the specified message to the contact(s) with the specified uids
 * @discussion Sends a CoT message to one or more contacts by specifying the contacts with their UIDs.
 *             Transmission is restricted to the CommoCoTSendMethod specified.
 *             The message is queued for transmission immediately, but the
 *             actual transmission is done asynchronously. Because of the
 *             nature of CoT messaging, there may be no indication returned
 *             of the success or failure of the transmission. If the means
 *             by which the destination contact(s) is/are reachable allows for
 *             error detection, transmission errors will be posted to any
 *             CoTSendFailureListeners that are registered with this Commo instance.
 *             If one or more of the contacts is known to be unreachable at the time of this call, or
 *             if they are only reachable via a method that does not match the specified method, the
 *             message is sent to those contacts that are available reachable and ContactGone is returned. In this
 *             situation, the destinations array is updated to include only those contacts which were
 *             known to be gone or not reachable via the specified method.
 * @param destinations one or more UIDs specifying the contacts to send to. On return, the array may be modified to
 *                     indicate contacts that are gone or unreachable via the specified method
 * @param cotMessage the CoT message to send
 * @param method specifies to only send to contacts reachable via this method
 * @return Success if the message was queued for send to all contacts successfully, ContactGone if one or more
 *         of the contacts are known to no longer be reachable, or IllegalArgument if one or more
 *         arguments are invalid (particularly if the cot message format is unknown)
 */
-(CommoResult) sendCoTToContactUIDs:(NSMutableArray<NSString *> *) destinations messageBytes:(NSString *) cotMessage viaMethod:(CommoCoTSendMethod) method;

/**
 * @brief Broadcasts the given CoTMessage out all current outgoing broadcast interfaces
 *        and streaming interfaces configured to handle cot messages of the given type
 * @discussion The specified cotmessage is inspected and its CommoCoTMessageType is determined. 
 *        The message is then send out via all active broadcast interfaces and streaming interfaces
 *        which are configured for messages of that type.
 * @param cotMessage message to broadcast
 * @return Success if the message is accepted for transmission, IllegalArgument if the cot message is in an unrecognized format
 */
-(CommoResult) broadcastCoTWithMessageBytes:(NSString *) cotMessage;

/**
 * @brief Broadcasts the given CoTMessage out all current outgoing broadcast interfaces
 *        and/or streaming interfaces configured to handle cot messages of the given type
 * @discussion The specified cotmessage is inspected and its CommoCoTMessageType is determined.
 *        The message is then send out via all active broadcast interfaces and/or streaming interfaces
 *        which are configured for messages of that type and which match the specified transmission method.
 * @param cotMessage message to broadcast
 * @param method specifies what type(s) of interfaces should broadcast this message
 * @return Success if the message is accepted for transmission, IllegalArgument if the cot message is in an unrecognized format
 */
-(CommoResult) broadcastCoTWithMessageBytes:(NSString *) cotMessage viaMethod:(CommoCoTSendMethod) method;

/**
 * @brief send the specified message directly to a TCP host and port
 * @discussion Attempt to send a CoT-formatted message to the specified host
 * on the specified TCP port number.
 * The message is queued for transmission; the actual transmission happens
 * asynchronously.
 * A connection is made to the host on the specified port, the message
 * is sent, and the connection is closed.
 * If the transmission fails, a failure will be posted to any
 * registered CoTSendFailureListeners.
 * NOTE: Use of this method is deprecated in favor of sendCoT().
 *
 * @param hostname host or ip number in string form designating the remote
 *                 system to send to
 * @param port destination TCP port number
 * @param cotMessage CoT message to send
 * @return Success if message is queued for transmission successfully, IllegalArgument
 *         if cotMessage is not valid or some other error occurs queuing the transmission
 */
-(CommoResult) sendCoTTcpDirectToHostname:(NSString *) hostname port:(int) port messageBytes:(NSString *) cotMessage;

/**
 * @brief send a cot message to tak servers as a server processing/control message
 * @discussion Attempts to send a CoT-formatted message to all connected
 * streaming interfaces with a special destination tag
 * that indicates to TAK server that this message is intended
 * as a control message for the server itself.
 * The message is queued for transmission; the actual transmission
 * happens asynchronously.
 * @param streamId the streamId of a currently valid
 *      CommoStreamingNetInterface indicating the TAK server to send to, or
 *      nil to send to all active servers
 * @param cotMessage CoT message to send
 * @return Success if message is queued for transmission successfully, IllegalArgument
 *         if cotMessage is not valid or some other error occurs queuing the transmission
 */
-(CommoResult) sendCoTControlToServer:(NSString *) server message:(NSString *) cotMessage;

/**
 * @brief send a cot message to tak servers, specifying destination by mission identifier
 * Attempts to send a CoT-formatted message to a mission-id
 * destination on all connected streaming interfaces.
 * The message is queued for transmission; the actual transmission happens
 * asynchronously.
 * @param streamId the streamId of a currently valid
 *      CommoStreamingNetInterface indicating the TAK server to send to, or
 *      nil to send to all active servers
 * @param mission the mission identifier indicating the mission
 *                to deliver to
 * @param cotMessage CoT message to send
 * @return Success if message is queued for transmission successfully, IllegalArgument
 *         if cotMessage is not valid or some other error occurs queuing the transmission
 */
-(CommoResult) sendCoTToMissionDestOnServer:(NSString *)streamingRemoteId mission:(NSString *) mission messageBytes:(NSString *) cotMessage;

/**
 * @brief Initiate a simple file transfer.
 * @discussion Simple file transfers are transfers of files via traditional
 * client-server protocols (where commo is the client).
 * Transfer can be an upload from this device to the server
 * (forUpload = true) or a download from the server to this device
 * (forUpload = false).  Server file location is specified using
 * remoteURI while local file location is specified via localFile.
 * localFile must be able to be accessed (read/written) for the duration
 * of the transfer and assumes the transfer is the only thing accessing
 * the file. For downloads, localFile's parent directory must already
 * exist and be writable - the library will not create directories for you.
 * Existing files will be overwritten without warning; it is the calling
 * application's responsibility to verify localFile is the user's
 * intended location, including checking for existing files!
 * Protocols currently supported are: ftp, ftps (ssl based ftp).
 * Other protocol support may be added in the future.
 * caCert is optional - if given (non-null), it is used in ssl-based
 * protocols to verify the server's certificate is signed by
 * the CA in the given cert. It must be a valid cert in PKCS#12 format.
 * User and password are optional; if given (non-null), they will be used
 * to attempt to login on the remote server. Do NOT put the
 * username/password in the URI!
 * A non-null password must be accompanied by a non-null user.
 * Upon successful return here, the transfer is configured and placed
 * in a waiting state.  The caller my commence the transfer by
 * invoking simpleTransferStart(), supplying the returned transfer id.
 *
 * @param tranferId receives transfer id if Success is returned
 * @param forUpload true to initiate upload, false for download
 * @param remoteURI URI of the resource on the server
 * @param caCert optional CA certificate to check server cert against
 * @param caCertPassword password for the CA cert data
 * @param user optional username to log into remote server with
 * @param password optional password to log into the remote server with
 * @param localFile local file to upload/local file location to write
 *        remote file to
 * @return InvalidCACert if CA cert format is invalid.
 *        InvalidCACertPass if CA cert password is incorrect.
 *        IllegalArgument if an error occurs if the URI contains an unsupported
 *        protocol, this Commo instance has not yet had
 *        SimpleFileIO enabled by calling enableSimpleFileIO(),
 *        or any other error occurs. Success if the transfer is initialized
 *        successfully.
 */
-(CommoResult) simpleFileTransferInit:(NSString *) localFile
                            forUpload:(bool) forUpload
                            remoteURI:(NSString *) remoteURI
                               caCert:(NSData *) caCert
                       caCertPassword:(NSString *) caCertPassword
                                 user:(NSString *) user
                             password:(NSString *) password
                           transferId:(int *) transferId;

/**
 * @brief Commences a simple file transfer
 * @discussion Commences a simple file transfer previously initialized
 * via a call to simpleFileTransferInit().
 * @param xferId id of the previously initialized transfer
 * @return IllegalArgument if this if this Commo instance has not yet had
 *        SimpleFileIO enabled by calling enableSimpleFileIO or
 *        the supplied xferId does not identify an initialized (but not
 *        already started) transfer. Success if the transfer starts successfully.
 */
-(CommoResult) simpleFileTransferStart:(int) xferId;

/**
 * @brief Initialize a Mission Package transfer to one or more contacts.
 * @discussion Initialize a Mission Package transfer to the
 * specified contact(s).  The list of contact(s) is updated
 * to indicate which contact(s) could not be sent to (considered "gone").
 * This call sets up the transfer and places
 * it into a pending state, returning an identifier that can be used
 * to correlate transfer status information back to this
 * request (it uniquely identifies this transfer).
 * To commence the actual transfer, call startMissionPackageSend() with
 * the returned id. It is guaranteed that no status info
 * will be fed back for the transfer until it has been started.
 * Once the send has been started, transmission will be initiated as
 * soon as possible, but the actual processing by and transfer to
 * the receiver will take place asynchronously in the background.
 * The file being transferred must remain available at the
 * specified location until the asynchronous transaction is
 * entirely complete.
 *
 * @param transferId identifier that uniquely identifies this transaction.
 *                   This is only assigned a valid value when ContactGone or
 *                   Success is returned
 * @param destinations List of UIDs for Contacts who are to receive the specified file.
 *                   This list may be modified to reflect "gone" contacts at the
 *                   instant this call is made if ContactGone is returned. Note that
 *                   other contacts may be considered "gone" as asynchronous
 *                   processing continues; this is specified in progress updates
 *                   to the MissionPackageIO callback protocol.
 * @param filePath the path to the Mission Package file to send
 * @param fileName the name by which the receiving system should refer
 *                     to the file (no path, file name only). This name
 *                     does NOT have to be the same as the local file name.
 * @param transferName the name for the transfer, used to generically refer
 *                 to the transfer both locally and remotely
 * @return Success if the transfer is queued to all specified contacts,
 *                  ContactGone if one or more of the specified contacts are
 *                  "gone" while some of the contacts are queued successfully,
 *                  IllegalArgument if this Commo does not have file
 *                  transfers setup via successful SetupMissionPackageIO call,
 *                  if all specified contacts are "gone",
 *                  or the local file could not be read.
 */
-(CommoResult) sendMissionPackageInitToContacts:(NSMutableArray<NSString *> *) destinations
                             filePath:(NSString *) filePath
                             fileName:(NSString *) fileName
                         transferName:(NSString *) transferName
                         transferId:(int *) transferId;

/**
 * @brief Requests a mission package file be sent to a TAK server
 * @discussion Send a Mission Package file to the specified TAK server.
 * The server is first queried to see if the file exists on server a
 * If not, the file is transmitted to the server.
 * The query and the transmission (if needed) are
 * initiated as soon as possible, but this takes place asynchronousl
 * in the background.  The returned ID can be used to correlate
 * transfer status information back to this request (it
 * uniquely identifies this transfer).
 * The file being transfered must remain available at the specified
 * until the asynchronous transaction is entirely complete.
 *
 * @param transferId Assigned an integer that uniquely identifies this transfer;
 *      this is assigned to only Success is returned.
 * @param streamId the streamId of a currently valid
 *      CommoStreamingNetInterface indicating the TAK server to send to.
 * @param filePath Full (absolute) path to the file to send
 * @param fileName The name by which the remote system should refer to the file;
 *      file name (no path) only.  This name does NOT have to be the
 *      same as the local system's name of the file.
 * @return  Success if the transfer is queued to the specified server.
 *      ContactGone if the streamId does not represent a currently valid
 *      CommoStreamingNetInterface. IllegalArgument if this Commo does
 *      not have file transfers setup via setupMissionPackageIO
 *      or the local file could not be read.
 */
-(CommoResult) sendMissionPackageInitToServer:(NSString *) streamId
                                 filePath:(NSString *) filePath
                                 fileName:(NSString *) fileName
                               transferId:(int *) transferId;

/**
 * @brief Begins the transfer process for a pending mission package send.
 * @discussion Actually commences the transfer of a pending mission package
 * send initialized by a prior call to sendMissionPackageInitToXXX().
 * The transmission is initiated as soon as possible, but the
 * actual processing by and transfer to the receiver will take
 * place asynchronously in the background as described in
 * sendMissionPackageInitToXXX().
 *
 * @param transferId the identifier of the mission package send to start, returned
 * by prior successful call to sendMissionPackageInitToXXX()
 * @return IllegalArgument if the specified id is not a valid
 * id for a mission package send. Success if the transfer begins successfully
 */
-(CommoResult) startMissionPackageSend:(int) transferId;


/**
 * @brief obtains the UIDs of all contacts known via all active interfaces
 * @return array of UID strings for known contacts
 */
-(NSArray<NSString *> *) getContactList;

/**
 * @brief Create/configure a contact with a known endpoint
 * Configure and create a "known endpoint" contact.
 * This is a non-discoverable contact for whom we already know
 * an endpoint and want to be able to communicate with them, possibly
 * unidirectionally.  The destination address must be specified in
 * "dotted decimal" IP address form; hostnames are now accepted.
 * If the address is a multicast address, messages sent to the created
 * Contact will be multicasted to all configured broadcast interfaces,
 * regardless of message type.
 * The port specifies a UDP port endpoint on the specified remote host.
 * The UID must be unique from existing known UIDs and should be chosen
 * to be unique from other potentially self-discovered contacts;
 * any message-based discovery for a contact with the same UID as the one
 * specified will be ignored - the endpoint remains fixed to the specified
 * one.
 * Specifying a UID of an already configured, known endpoint contact
 * allows it to be reconfigured to a new endpoint or callsign; specify
 * nil for callsign and ipAddr (and any port number) to remove this
 * "contact". Specifying nil for either callsign or ipAddr (but not both)
 * results in an error.  Specifying a UID for an already known (via
 * discovery) contact, a nil UID, nil ipAddr for a contact not previously
 * configured via this call, or an unparsable ipAddr will also yield an
 * error.
 * Upon success configuring the contact, Success is returned. Errors 
 * result in a return of IllegalArgument
 * @param uid uid for the contact
 * @param callsign callsign for the contact
 * @param ipAddr ip address in "dotted decimal" form
 * @param destPort the port to send to
 * @return Success if contact was added, removed, or reconfigured successfully;
 *         otherwise IllegalArgument is returned
 */
-(CommoResult) configKnownEndpointContactWithUid:(NSString *) uid callsign:(NSString *) callsign ipAddr:(NSString *) ipAddr port:(int) destPort;

/**
 * @brief register a CommoContactPresenceListener to receive notification of contact presence changes
 * @return Success if listener is added successfully, IllegalArgument if the listener
 *         is already registered or another error occurs.
 */
-(CommoResult) addContactPresenceListener:(id<CommoContactPresenceListener>) listener;


/**
 * @brief deregister a CommoContactPresenceListener; upon completion it will no longer receive
 *        contact presence updates.
 * @return Success if listener is removed successfully, IllegalArgument if the listener
 *         was not previously registered or another error occurs.
 */
-(CommoResult) removeContactPresenceListener:(id<CommoContactPresenceListener>) listener;


/**
 * Generate a private key and return as a string in PEM format,
 * encoded with the given password.
 * @param password Password to use when encoding the private key
 * @keyLen key length to generate, in bits
 * @return String representation of the PEM formatted private key, 
 *         or nil on error. 
 */
-(NSString *)generateKeyCryptoStringWithPassword:(NSString *) password keyLen:(int) keyLen;

/**
 * Generate a CSR and return as a string in PEM format.
 * CSR will be signed by the given key, which uses the given password.
 * CSR will contain the entries specified.
 * @param dnEntries Dictionary of key/value pairs of strings that will
 * be included in the CS to form the DN. The keys must be valid 
 * for CSR DN entries.
 * @param pkeyPEM Private key to use for the CSR, in PEM format
 * @param password Password to used to decode the private key.
 * @return String representation of the PEM formatted CSR, or nil
 * on error. 
 */
-(NSString *)generateCSRCryptoStringWithDnEntries:(NSDictionary<NSString *, NSString *> *) dnEntries pkeyPEM:(NSString *) pkeyPEM password:(NSString *) password;

/**
 * @brief Generate a keystore from the given constituent elements.
 *        The keystore is returned as a pkcs#12 keystore encoded as a string
 *        base64 format.
 * @param certPEM The certificate as a string in PEM format
 * @param caCertsPEM An ordered list of ca certificates, each
 *              as a string in PEM format. This allows intermediate CAs
 *              up to a rootCA.
 * @param privKeyPEM Private key as a string in PEM format
 * @param password Password used to decode the private key pkeyPem
 * @param friendlyName Friendly name to use in the keystore
 * @return String representation of the pkcs#12 keystore,
 *         encoded using base64 or nil on error.
 */
-(NSString *)generateKeystoreCryptoStringWithPEMCert:(NSString *) certPEM caCertsPEM:(NSArray<NSString *> *) caCertsPEM privKeyPEM:(NSString *) privKeyPEM password:(NSString *) password friendlyName:(NSString *) friendlyName;


@end


#endif /* commo_objc_h */
