#ifndef COMMO_CLI_H_
#define COMMO_CLI_H_


#include "commoresult_cli.h"
#include "commologger_cli.h"
#include "netinterface_cli.h"
#include "missionpackage_cli.h"
#include "simplefileio_cli.h"
#include "cloudio_cli.h"
#include "contactuid_cli.h"
#include "cotmessageio_cli.h"
#include "commo_cli.h"

namespace TAK {
    namespace Commo {
        namespace impl {
            ref class CommoImpl;
        }


        /**
            * <summary>Main interface to the Commo library.
            * This class is the main interface into the Commo library.  An instance
            * of this class will provide most functionality of the library.
            * As a general rule, objects implementing any of the listener 
            * interfaces must remain valid from the time they are added until 
            * either they are removed or this Commo object is destroyed.
            *
            * Before creating the first instance of a Commo object, callers must
            * initialize libcurl, libxml2, and openssl. In particular, 
            * openssl must be configured to support multiple threads with 
            * appropriate locking constructs configured.  See example apps.
            * </summary>
            */
        public ref class Commo {
        public:
            /**
             * Use in a call to SetMissionPackageLocalPort to disable
             * local web server used for sending mission packages.
             */
            literal int MP_LOCAL_PORT_DISABLE = -1;
                
            Commo(ICommoLogger ^logger, System::String ^ourUID,
                    System::String ^ourCallsign);
            ~Commo();
            !Commo();

            /**
                * <summary>
                * Configure client side helper interface for mission package transfers
                * on this Commo instance.
                * The caller must supply an implementation of the 
                * IMissionPackageIO interface that will remain operable
                * until Shutdown() is invoked; once an implementation is setup
                * via this call, it cannot be changed/swapped out.
                * After a successful return, the mission package family of functions
                * may be invoked.
                * Returns IllegalArgument if missionPackageIO is null, or
                * if this method has previously been called successfully for this
                * Commo instance. Otherwise returns Success.
                * </summary>
                * <param name="missionPackageIO">
                * implements mission package IO integration with client application
                * </param>
                */
            CommoResult SetupMissionPackageIO(
                IMissionPackageIO ^missionPackageIO);
            
            /**
             * <summary>
             * Enables simple file transfers for this Commo instance.
             * Simple file transfers are transfers of files via traditional
             * client-server protocols (where commo is the client)
             * and is not associated with CoT sending/receiving or
             * mission package management.
             * Once enabled, transfers cannot be disabled.
             * The caller must supply an implementation of the 
             * ISimpleFileIO interface that will remain operable
             * until Shutdown() is invoked.
             * After a successful return, SimpleFileTransferInit()
             * can be invoked to initiate file transfers with an external server.
             * Returns IllegalArgument if simpleIO is null, or
             * if this method has previously been called successfully for this
             * Commo instance. Otherwise returns Success.
             * </summary>
             * <param name="simpleIO">
             * reference to an implementation of
             * the ISimpleFileIO interface that
             * this Commo instance can use to interface
             * with the application during simple file
             * transfers
             * </param>
             */
            CommoResult EnableSimpleFileIO(ISimpleFileIO ^simpleIO);

            /**
                * <summary>
                * Cleans up all native resources associated with this Commo object.
                * Once this method is invoked, no other methods may be invoked on
                * this object. As a side-effect all registered listeners are
                * deallocated and the caller my safely discard them upon
                * completion of this method.  It is safe to call this
                * multiple times.
                * </summary>
                */
            void Shutdown();

            /**
                * <summary>
                * Sets the callsign of the local system.
                * This method may be used to change the local system's
                * callsign after initialization.
                * </summary>
                * <param name="callsign">callsign to set</param>
                */
            void SetCallsign(System::String ^callsign);

            /**
             * <summary>
             *  Enables encryption/decryption for non-streaming CoT interfaces
             * using the specified keys, or disables it entirely.
             * Arrays MUST be precisely 32 bytes long and contain unique keys.
             * Specify null for both to disable encryption/decryption (the default).
             * Returns IllegalArgument if the keys are not the right length, are the same, or
             * one is null and the other is not;  returns Success otherwise.
             * </summary>
             * <param name="auth">Authentication key</param>
             * <param name="crypt">Cryptographic key</param>
             */
            CommoResult SetCryptoKeys(array<System::Byte> ^auth, array<System::Byte> ^crypt);

            /**
                * <summary>
                * Enables or disables the ability of datagram sockets created by
                * the system to share their local address with other sockets on the system
                * created by other applications.  Note that this option is unreliable
                * when unicast datagrams are sent to the local system, as it is indeterminate
                * which local socket will receive the unicast datagrams.  Use this option
                * with caution if you expect to be able to receive unicasted datagrams.
                * </summary>
                * <param name="en">true to allow address reuse, false otherwise</param>
                */
            void SetEnableAddressReuse(bool en);

            /**
                * <summary>
                * Sets the global TTL used for broadcast messages.
                * The provided TTL will be used for all future
                * broadcast messages send out over all broadcast interfaces 
                * managed by this Commo instance. Values out of range will 
                * be clamped internally to a valid value.
                * </summary>
                * <param name="ttl">TTL value to use</param>
                */
            void SetTTL(int ttl);

            /**
             * <summary>
             * Sets global UDP timeout value, in seconds; if the given # of seconds
             * elapses without a socket receiving data, it will be destroyed
             * and rebuilt. If the timed-out socket was a multicast socket,
             * multicast joins will be re-issued.
             * 0 means never timeout. Default is 30 seconds.
             * </summary>
             *
             * <param name="seconds">
             * timeout value in seconds - use 0 for "never"
             * </param>
             */
            void SetUdpNoDataTimeout(int seconds);

            /**
                * <summary>
                * Sets global TCP connection timeout for all connections except for
                * mission package uploads/downloads
                * </summary>
                * <param name="seconds">Timeout value in seconds</param>
                */
            void SetTcpConnTimeout(int seconds);

            /**
             * <summary>
             * Sets the local web server port to use for outbound peer-to-peer
             * mission package transfers, or disables this functionality.
             * The local web port number must be a valid, free, local port
             * for TCP listening or the constant MP_LOCAL_PORT_DISABLE. 
             * On successful return, the server will be active on the specified
             * port and new outbound transfers (SendMissionPackage()) will use
             * this to host outbound transfers.
             * If this call fails, transfers using the local web server will be
             * disabled until a future call completes successfully (in other words,
             * will act as if this had been called with MP_LOCAL_PORT_DISABLE).
             *
             * Note carefully: calling this to change the port or disable the local
             * web server will cause all in-progress mission package sends to
             * be aborted.
             *
             * The default state is to have the local web server disabled, as if
             * this had been called with MP_LOCAL_PORT_DISABLE.
             *
             * Returns IllegalArgument if the specified port is invalid or
             * unavailable for use, or if setupMissionPackageIO has not yet
             * been successfully invoked; if this is returned, the server will
             * be disabled regardless of the state before the call.
             * </summary>
             *
             * <param name="localWebPort">
             * TCP port number to have the local web server
             * listen on, or MP_LOCAL_PORT_DISABLE.
             * The port must be free at the time of invocation.
             * </param>
             */
            CommoResult SetMissionPackageLocalPort(int localWebPort);

           /**
            * <summary>
            * Sets the local https web server paramerters to use with the local
            * https server for outbound peer-to-peer
            * mission package transfers, or disables this functionality.
            * The local web port number must be a valid, free, local port
            * for TCP listening or the constant MP_LOCAL_PORT_DISABLE.
            * The certificate data and password must be non-null except in
            * the specific case that localWebPort is MP_LOCAL_PORT_DISABLE.
            * Certificate data must be in pkcs#12 format and should represent
            * the complete certificate, key, and supporting certificates that
            * the server should use when negotiating with the client.
            * On successful return, the https server will be configured to use the
            * specified port and new outbound transfers (sendMissionPackage())
            * will use this to host outbound transfers.
            * Note that the https server also requires the http port to be enabled
            * and configured on a different port as the https function utilizes
            * the http server internally. If the http server is not enabled
            * (see SetMissionPackageLocalPort()) the https server will remain
            * in a configured but disabled state until the http server is activated.
            * If this call fails, transfers using the local https server will be
            * disabled until a future call completes successfully (in other words,
            * will act as if this had been called with MP_LOCAL_PORT_DISABLE).
            *
            * Note carefully: calling this to change the port or disable the local
            * https web server will cause all in-progress mission package sends to
            * be aborted.
            *
            * The default state is to have the local https web server disabled, as if
            * this had been called with MP_LOCAL_PORT_DISABLE.
            *
            * Returns Success if the provided TCP port number is successfully
            * opened and usable for the local web server (or if MP_LOCAL_PORT_DISABLE
            * is passed), IllegalArgument if the passed port cannot be used
            * or if setupMissionPackageIO() has not previously been invoked
            * successfully, INVALID_CERT if certificate could not be read,
            * or INVALID_CERT_PASSWORD if cert password is incorrect.
            * </summary>
            *
            * <param name="localWebPort">
            * TCP port number to have the local https web server
            * listen on, or MP_LOCAL_PORT_DISABLE.
            * The port must be free at the time of invocation.
            * </param>
            */
            CommoResult SetMissionPackageLocalHttpsParams(int localWebPort,
                array<System::Byte> ^certificate, System::String ^certPass);
            
            /**
             * <summary>
             * Controls if mission package sends via TAK servers may or may
             * not be used.  The default, once mission package IO has been setup
             * via SetupMissionPackageIO(), is enabled.
             * Note carefully: Disabling this after it was previously enabled
             * may cause some existing outbound mission package transfers to be
             * aborted!
             * </summary>
             * <param name="enabled">
             * true to enable server-based sends, false to disable
             * </param>
             */
            void SetMissionPackageViaServerEnabled(bool enabled);
            
            /**
             * <summary>
             * Sets the TCP port number to use when contacting a TAK
             * Server's http web api. Default is port 8080.
             *
             * Returns IllegalArgument if the port number is out of range
             * </summary>
             *
             * <param name="serverPort">
             * tcp port to use
             * </param>
             */
            CommoResult SetMissionPackageHttpPort(int serverPort);

            /**
             * <summary>
             * Sets the TCP port number to use when contacting a TAK
             * Server's https web api. Default is port 8443.
             *
             * Returns IllegalArgument if the port number is out of range
             * </summary>
             *
             * <param name="serverPort">
             * tcp port to use
             * </param>
             */
            CommoResult SetMissionPackageHttpsPort(int serverPort);

            /**
                * <summary>
                * Controls if streams should be monitored or not
                * </summary>
                * <param name="enable">Enable stream monitoring value</param>
                */
            void SetStreamMonitorEnabled(bool enable);

            /**
             * <summary>
             * Gets the version of TAK protocol currently being used for broadcasts.
             * 0 means legacy XML format, >0 is a binary version of TAK protocol.
             * </summary>
             */
            int GetBroadcastProto();

            /**
             * <summary>
             * Set number of attempts to receive a mission package.
             * Minimum value is one (1), default value is 10.
             * New settings influence subsequent transfers;
             * currently ongoing transfers use settings in place at the time they
             * began.
             * Returns SUCCESS if argument is legitimate value, ILLEGAL_ARGUMENT
             * if it is out of legitimate range.
             * </summary>
             * <param name="nTries">Number of attempts to make</param>
             */
            CommoResult SetMissionPackageNumTries(int nTries);

            /**
             * <summary>
             * Set timeout, in seconds, for initiating connections to remote
             * hosts when transferring mission packages.  This is used when receiving
             * mission packages from TAK servers and/or other devices, as well 
             * as uploading mission packages to TAK servers.
             * Minimum value is five (5) seconds, default value is 90 seconds.
             * New settings influence subsequent transfers;
             * currently ongoing transfers use settings in place at the time they
             * began.
             * Returns SUCCESS if argument is legitimate value, ILLEGAL_ARGUMENT
             * if it is out of legitimate range.
             * </summary>
             * <param name="seconds">Number of seconds to wait for connection attampts to succeed</param>
             */
            CommoResult SetMissionPackageConnTimeout(int seconds);

            /*
             * <summary>
             * Set timeout, in seconds, for data transfers.  After this number
             * of seconds has elapsed with no data transfer progress taking place,
             * the transfer attempt is considered a failure.
             * This is used when receiving mission packages.
             * Minimum value is 15 seconds, default value is 120 seconds.
             * New settings influence subsequent transfers;
             * currently ongoing transfers use settings in place at the time they
             * began.
             * Returns SUCCESS if argument is legitimate value, ILLEGAL_ARGUMENT
             * if it is out of legitimate range.
             * </summary>
             * <param name="seconds">Number of seconds to wait for a stalled transfer</param>
             */
            CommoResult SetMissionPackageTransferTimeout(int seconds);

            /**
                * <summary>
                * Add a new outbound broadcasting interface.
                * Directs the Commo instance to monitor the local network
                * interface with the given name and, when the
                * interface is active, send broadcast messages matching any of 
                * the given message types out said interface to the specific
                * multicast address and port.  The returned interface is valid
                * until removed via removeBroadcastInterface or this Commo
                * object is closed.
                * The ifaceName corresponds to the Adapter's unique name (aka ID),
                * which for most adapters, looks like a stringified GUID.
                * </summary>
                * <returns>
                * an internal object implementing the PhysicalNetInterface
                * protocol, or nullptr if an error occurs.
                * </returns>
                */
            PhysicalNetInterface ^AddBroadcastInterface(
                System::String ^ifaceName,
                array<CoTMessageType> ^types, 
                System::String ^mcastAddr, 
                int destPort);
               
                
            /**
            * <summary>
            * Add a new outbound broadcasting interface.
            * Directs the Commo instance to send broadcast messages matching any of
            * the given message types out to the specified
            * unicast address and port.  The returned interface is valid
            * until removed via removeBroadcastInterface or this Commo
            * object is closed.
            * </summary>
            * <returns>
            * an internal object implementing the PhysicalNetInterface
            * protocol, or nullptr if an error occurs.
            * </returns>
            */
            PhysicalNetInterface ^AddBroadcastInterface(
                array<CoTMessageType> ^types,
                System::String ^unicastAddr,
                int destPort);


            /**
                * <summary>
                * Removes a previously added broadcast interface.
                * Upon completion of this method, no further data will be
                * broadcast to the specified interface.
                * </summary>
                * <returns>
                * Success if the interface was successfully removed, or 
                * IllegalArgument if the argument is not a valid 
                * PhysicalNetInterface obtained by previous call to
                * addBroadcastInterface.
                * </returns>
                */
            CommoResult RemoveBroadcastInterface(PhysicalNetInterface ^iface);

            /**
                * <summary>Add a new listening interface.
                * Directs the Commo instance to monitor the local network 
                * interface with the given name and, when the 
                * interface is active, listen on the specified local port for
                * traffic. Additionally, subscribe to the multicast groups 
                * specified to receive traffic directed at those multicast 
                * addresses.
                * If forGenericData is true, the interface will be used simply to receive
                * network data on the specified port and forward to any registered
                * IGenericDataListener instances.  If forGenericData is false, the
                * interface will be used specifically for CoT 
                * traffic, with received messages posted to registered
                * ICoTMessageListeners.
                * NOTE: Generic and non-generic interfaces cannot be configured for different
                * ifaceName's on the same port number.
                * The ifaceName corresponds to the Adapter's unique name (aka ID),
                * which for most adapters, looks like a stringified GUID.
                * The returned interface is valid until removed via 
                * removeInboundInterface or this Commo object is closed.
                * </summary>
                * <returns>
                * an internal object implementing the PhysicalNetInterface
                * protocol, or nullptr if an error occurs.
                * </returns>
                */
            PhysicalNetInterface ^AddInboundInterface(
                System::String ^ifaceName, 
                int port,  
                array<System::String ^> ^mcastAddrs,
                bool forGenericData);

            /**
                * <summary>
                * Removes a previously added inbound interface.
                * Upon completion of this method, no further data will be received
                * from the specified interface.
                * </summary>
                * <returns>
                * Success if the interface was successfully removed, or 
                * IllegalArgument if the argument is not a valid 
                * PhysicalNetInterface obtained by previous call to 
                * addInboundInterface.
                * </returns>
                */
            CommoResult RemoveInboundInterface(PhysicalNetInterface ^iface);

            /**
                * <summary>Add a new tcp listening interface.
                * Directs the Commo instance to monitor the local network
                * interface with the given hardware address and, when the
                * interface is active, listen on the specified local port for CoT
                * traffic. Additionally, subscribe to the multicast groups
                * specified to receive traffic directed at those multicast
                * addresses.
                * The returned interface is valid until removed via
                * removeInboundInterface or this Commo object is closed.
                * </summary>
                * <returns>
                * an internal object implementing the TcpInboundNetInterface
                * protocol, or nullptr if an error occurs.
                * </returns>
                */
            TcpInboundNetInterface ^AddTcpInboundInterface(int port);

            /**
                * <summary>
                * Removes a previously added tcp inbound interface.
                * Upon completion of this method, no further data will be received
                * from the specified interface.
                * </summary>
                * <returns>
                * Success if the interface was successfully removed, or
                * IllegalArgument if the argument is not a valid
                * TcpInboundNetInterface obtained by previous call to
                * addInboundInterface.
                * </returns>
                */
            CommoResult RemoveTcpInboundInterface(TcpInboundNetInterface ^iface);

            /**
                * <summary>
                * Add a new streaming interface to a remote TAK server.
                * Directs the Commo instance to attempt to establish and maintain
                * a connection with a TAK server at the specified hostname on the
                * specified remote port. The connection will be used to receive
                * CoT messages of all types from the TAK server, for non-broadcast 
                * sending of all types of CoT messages to specific contacts, 
                * as well as for broadcasting CoTMessages of the types specified.
                * Depending on the arguments given, attempts to connect will be
                * made with plain TCP, SSL encryted TCP, or SSL encrypted with
                * server authentication: specifying nullptr for clientCertBytes
                * will result in a plain TCP connection, specifying non-nullptr
                * for clientCertBytes requires non-nullptr for trustStoreBytes
                * and will result in an SSL connection, and specifying non-nullptr
                * clientCertBytes, trustStoreBytes, username, and password
                * will result in an SSL connection that sends a TAK server
                * authentication message when connecting.
                * Regardless of the type of connection, the Commo instance will
                * attempt to create and restore the server connection as needed 
                * until this interface is removed or this Commo object is closed.
                * The returned interface is valid until removed via
                * removeStreamingInterface or this Commo object is closed.
                * </summary>
                * <param name="hostname">
                * hostname or string-form of IP address of the TAK server to
                * connect to
                * </param>
                * <param name="port">
                * port number of the TAK server to connect to
                * </param>
                * <param name="types">
                * the types of CoT messages that can be broadcast out this
                * interface. An empty array is acceptable
                * </param>
                * <param name="clientCert">
                * bytes comprising the client's certificate for use in SSL
                * connection. If nullptr, plain TCP connections
                * are used.  If non-nullptr, trustStore MUST also be specified.
                * The bytes must comprise a valid PKCS#12 certificate store.
                * </param>
                * <param name="trustStore">
                * bytes comprising the certificate(s) that the client will accept
                * as trusted for signing the certificate presented to the client
                * by the server during connection. The bytes must comprise a 
                * valid PKCS#12 certificate store.
                * </param>
                * <param name="certPassword">
                * password to decrypt the client's certificate. 
                * May be nullptr if no password is needed.
                * </param>
                * <param name="trustStorePassword">
                * password to decrypt the client's trust store.
                * May be nullptr if no password is needed.
                * </param>
                * <param name="username">
                * if non-nullptr, an authentication message with the username 
                * and password specified will be sent to the TAK server upon
                * connection.  if non-nullptr, password must also be non-nullptr
                * </param>
                * <param name="password">
                * the accompanying password to go with the username in the
                * authentication message
                * </param>
                * <param name="errorCode">
                * Set to indicate detailed error information if the addition of the interface
                * fails.
                * </param>
                * <returns>
                * an internal object implementing the StreamingNetInterface
                * protocol, or nullptr if an error occurs.
                * </returns>
                */
            StreamingNetInterface ^AddStreamingInterface(
                System::String ^hostname,
                int port, 
                array<CoTMessageType> ^types,
                array<System::Byte> ^clientCert,
                array<System::Byte> ^trustStore,
                System::String ^certPassword,
                System::String ^trustStorePassword,
                System::String ^username,
                System::String ^password,
                CommoResult %errorCode);
                
            /**
                * <summary>
                * Removes a previously added streaming interface.
                * Upon completion of this method, no further data will be sent or
                * received from the specified interface.
                * </summary>
                * <returns>
                * Success if the interface was successfully removed, or
                * IllegalArgument if the argument is not a valid 
                * StreamingNetInterface obtained by previous call to 
                * addStreamingInterface.
                * </returns>
                */
            CommoResult RemoveStreamingInterface(StreamingNetInterface ^iface);

            /**
                * <summary>
                * Register a IInterfaceStatusListener to receive
                * updates on interface status
                * </summary>
                * <returns>
                * Success if listener is added successfully, IllegalArgument if
                * the listener is already registered or another error occurs.
                * </returns>
                */
            CommoResult AddInterfaceStatusListener(
                IInterfaceStatusListener ^listener);

            /**
                * <summary>
                * Deregistesr a IInterfaceStatusListener; upon completion it will no longer receive
                * interface status updates.
                * </summary>
                * <returns>
                * Success if listener is removed successfully, IllegalArgument if
                * the listener was not previously registered or another
                * error occurs.
                * </returns>
                */
            CommoResult RemoveInterfaceStatusListener(
                IInterfaceStatusListener ^listener);

            /**
                * <summary>
                * Register a ICoTMessageListener to receive CoT messages.
                * The specified ICoTMessageListener will be registered to receive
                * the CoT messages of all types which are received on any
                * currently listening interfaces.
                * </summary>
                * <returns>
                * Success if listener is added successfully, IllegalArgument if 
                * the listener is already registered or another error occurs.
                * </returns>
                */
            CommoResult AddCoTMessageListener(ICoTMessageListener ^listener);
            /**
                * <summary>
                * Deregister a ICoTMessageListener; upon completion it will 
                * no longer receive CoT messages.
                * </summary>
                * <returns>
                * Success if listener is removed successfully,
                * IllegalArgument if the listener was not previously registered 
                * or another error occurs.
                * </returns>
                */
            CommoResult RemoveCoTMessageListener(ICoTMessageListener ^listener);

            /**
            * <summary>
            * Register an IGenericDataListener to receive data from generic inbound
            * interfaces.
            * </summary>
            * <returns>
            * Success if listener is added successfully, IllegalArgument if
            * the listener is already registered or another error occurs.
            * </returns>
            */
            CommoResult AddGenericDataListener(IGenericDataListener ^listener);
            /**
            * <summary>
            * Deregister an IGenericDataListener; upon completion it will
            * no longer receive generic data.
            * </summary>
            * <returns>
            * Success if listener is removed successfully,
            * IllegalArgument if the listener was not previously registered
            * or another error occurs.
            * </returns>
            */
            CommoResult RemoveGenericDataListener(IGenericDataListener ^listener);

            /**
            * <summary>
            * Register a ICoTSendFailureListener to receive send failure information
            * The specified ICoTSendFailureListener will be registered to receive.
            * </summary>
            * <returns>
            * Success if listener is added successfully, IllegalArgument if
            * the listener is already registered or another error occurs.
            * </returns>
            */
            CommoResult AddCoTSendFailureListener(ICoTSendFailureListener ^listener);
            /**
            * <summary>
            * Deregister a ICoTSendFailureListener; upon completion it will
            * no longer receive failure information
            * </summary>
            * <returns>
            * Success if listener is removed successfully,
            * IllegalArgument if the listener was not previously registered
            * or another error occurs.
            * </returns>
            */
            CommoResult RemoveCoTSendFailureListener(ICoTSendFailureListener ^listener);

            /**
                * <summary>
                * Send the specified message to the contact(s) with the 
                * specified uids.
                * Sends a CoT message to one or more contacts by specifying the 
                * contacts with their UIDs.
                * If one or more of the contacts is known to be unreachable at the
                * time of this call, the message is sent to those contacts that
                * are available and ContactGone is returned. In this
                * situation, the destinations array is updated to include only
                * those contacts which were known to be gone.
                * The system will use any available method to reach the destination contacts.
                * </summary>
                * <param name="destinations">
                * one or more UIDs specifying the contacts to send to. 
                * On return, the array may be modified to
                * indicate gone contacts
                * </param>
                * <param name="cotMessage">
                * the CoT message to send
                * </param>
                * <returns>
                * Success if the message was sent to all contacts successfully,
                * ContactGone if one or more of the contacts are known to no
                * longer be reachable, or IllegalArgument if one or more
                * arguments are invalid (particularly if the cot message format
                * is unknown)
                * </returns>
                */
            CommoResult SendCoT(
                System::Collections::Generic::List<System::String ^> ^destinations,
                System::String ^cotMessage);
                
            /**
            * <summary>
            * Send the specified message to the contact(s) with the
            * specified uids.
            * Sends a CoT message to one or more contacts by specifying the
            * contacts with their UIDs.
            * Only the specified send method is used to reach the contact.
            * If one or more of the contacts is known to be unreachable via the send
            * method specified at the
            * time of this call, the message is sent to those contacts that
            * are available and ContactGone is returned. In this
            * situation, the destinations array is updated to include only
            * those contacts which were known to be unreachable via the method
            * specified.
            * </summary>
            * <param name="destinations">
            * one or more UIDs specifying the contacts to send to.
            * On return, the array may be modified to
            * indicate gone contacts
            * </param>
            * <param name="cotMessage">
            * the CoT message to send
            * </param>
            * <param name="sendMethod">
            * the method to be used in the sending of the message
            * </param>
            * <returns>
            * Success if the message was sent to all contacts successfully,
            * ContactGone if one or more of the contacts are known to no
            * longer be reachable, or IllegalArgument if one or more
            * arguments are invalid (particularly if the cot message format
            * is unknown)
            * </returns>
            */
            CommoResult SendCoT(
                System::Collections::Generic::List<System::String ^> ^destinations,
                System::String ^cotMessage,
                CoTSendMethod sendMethod);

            /**
                * <summary>
                * Broadcasts the given CoTMessage out all current outgoing
                * broadcast interfaces handling cot messages of the given type.
                * The specified cotmessage is inspected and its
                * CoTMessageType is determined.
                * The message is then send out via all active broadcast
                * interfaces which are configured for messages of that type.
                * </summary>
                * <param name="cotMessage">message to broadcast</param>
                * <returns>
                * Success if the message is accepted for transmission,
                * IllegalArgument if the cot message is in an unrecognized format
                * </returns>
                */
            CommoResult BroadcastCoT(System::String ^cotMessage);

            /**
            * <summary>
            * Broadcasts the given CoTMessage out all current outgoing
            * broadcast interfaces handling cot messages of the given type
            * that also match the given send method.
            * The specified cotmessage is inspected and its
            * CoTMessageType is determined.
            * The message is then send out via all active broadcast
            * interfaces which are configured for messages of that type and
            * which match the specified send method.
            * </summary>
            * <param name="cotMessage">message to broadcast</param>
            * <param name="sendMethod">
            * the method to be used in the sending of the message
            * </param>
            * <returns>
            * Success if the message is accepted for transmission,
            * IllegalArgument if the cot message is in an unrecognized format
            * </returns>
            */
            CommoResult BroadcastCoT(System::String ^cotMessage,
                CoTSendMethod sendMethod);

            /**
                * <summary>
                * Sends the given CoTMessage to a specific host at a defined port.
                * The specified cotmessage is inspected and its
                * CoTMessageType is determined.
                * </summary>
                * <param name="host">host address</param>
                * <param name="port">port number</param>
                * <param name="cotMessage">message to broadcast</param>
                * <returns>
                * Success if the message was sent successfully,
                * IllegalArgument if the cot message is in an unrecognized format
                * </returns>
                */
            CommoResult SendCoTTcpDirect(System::String ^host, int port, System::String ^cotMessage);

            /**
             * <summary>
             * Attempts to send a CoT-formatted message to all connected
             * streaming interfaces with a special destination tag
             * that indicates to TAK server that this message is intended
             * as a control message for the server itself.
             * The message is queued for transmission; the actual transmission
             * </summary>
             * <param name="streamingRemoteId">
             * The remoteEndpointId from a currently valid
             * StreamingNetInterface, identifying the server to send the
             * CoT "server control" message to.  null indicates
             * to send to all connected StreamingNetInterfaces.
             * </param>
             * <param name="cotMessage">message to send</param>
             * <returns>
             * Success if the message was sent successfully,
             * IllegalArgument if the cot message is in an unrecognized format
             * </returns>
             */
            CommoResult SendCoTServerControl(
                System::String ^streamingRemoteId,
                System::String ^cotMessage);

            /**
             * <summary>
             * Attempts to send a CoT-formatted message to a mission-id
             * destination on all connected streaming interfaces.
             * The message is queued for transmission; the actual transmission happens
             * asynchronously.
             * </summary>
             * <param name="streamingRemoteId">
             * The remoteEndpointId from a currently valid
             * StreamingNetInterface, identifying the server to send the
             * CoT mission-bound message to.  null indicates
             * to send to all connected StreamingNetInterfaces.
             * </param>
             * <param name="mission">
             * mission the mission identifier indicating the mission to deliver to
             * </param>
             * <param name="cotMessage">CoT message to send</param>
             * <returns>
             * Success if the message was sent successfully,
             * IllegalArgument if the cot message is in an unrecognized format or
             * </returns>
             */
            CommoResult SendCoTToServerMissionDest(
                System::String ^streamingRemoteId,
                System::String ^mission,
                System::String ^cotMessage);

            /**
             * <summary>
             * Initiate a simple file transfer.  
             * Simple file transfers are transfers of files via traditional
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
             * invoking simpleTransferStart(), supplying the returned
             * transfer id.
             * 
             * Returns IllegalArgument if URL has unsupported protocol,
             *    a password was given without a username, or this Commo 
             *    instance as not yet had
             *    SimpleFileIO enabled by calling enableSimpleFileIO().
             * Returns CommoInvalidTruststore if the caCert is invalid.
             * Returns CommoInvalidTruststorePassword if caCertPassword
             *    is incorrect.
             * Returns Success if the transfer was setup successfully. This
             *    is the only return value where xferId is set to a valid
             *    transfer id.
             * </summary>
             *
             * <param name"xferId">
             * populated with the transfer id of the initialized transfer
             * if return value is Success
             * </param>
             * <param name="forUpload">
             * true to initiate upload, false for download
             * </param>
             * <param name="remoteURI">
             * URI of the resource on the server. It should be escaped/quoted
             * properly for a valid URI
             * </param>
             * <param name="caCert">
             * optional CA certificate to check server cert against
             * </param>
             * <param name="caCertPassword">
             * password for the CA cert data
             * </param>
             * <param name="user">
             * optional username to log into remote server with
             * </param>
             * <param name="password">
             * optional password to log into the remote server with
             * </param>
             * <param name="localFile">
             * path to local file to upload/local file location to write
             *        remote file to
             * </param>
             */
            CommoResult SimpleFileTransferInit(int % xferId,
                                       bool forUpload,
                                       System::String ^remoteURI,
                                       array<System::Byte> ^caCert,
                                       System::String ^caCertPassword,
                                       System::String ^user,
                                       System::String ^password,
                                       System::String ^localFile);

            /**
             * <summary>
             * Commences a simple file transfer previously initialized 
             * via a call to SimpleFileTransferInit().
             *
             * Returns IllegalArgument if the specified id is invalid
             * </summary>
             *
             * <param name="xferId">
             * id of the previously initialized transfer
             * </param>
             */
            CommoResult SimpleFileTransferStart(int xferId);

            /**
            * <summary>
            * Create a CloudClient to interact with a remote cloud server.
            * The client will remain valid until destroyed using destroyCloudClient()
            * or this Commo instance is shutdown. NOTE: it is invalid to let a
            * CloudClient reference be lost before being destroyed via one of the two
            * above methods!
            * The basePath is the path to cloud application on the server.
            * If your cloud service is at the root of the server on the remote host
            * simply pass the empty string or "/" for the basePath.
            * The client uses the provided parameters to interact with the server
            * and the provided callback interface to report progress on operations
            * initiated using the Client's methods.
            * Any number of clients may be active at a given time.
            * caCerts is optional - if given (non-null), it is used in ssl-based
            * protocols to verify the server's certificate. It must be a valid
            * set of certs in PKCS#12 format.
            * If not provided (null) and an SSL protocol is in use, all remote
            * certificates will be accepted.
            * The user and password are optional; if given (non-null),
            * they will be used to attempt to login on the remote server.
            * A non-null password must be accompanied by a non-null user.
            * Upon successful return here, the transfer is configured and placed
            * in a waiting state.  The caller my commence the transfer by
            * invoking simpleTransferStart(), supplying the returned transfer id.
            * </summary>
            *
            * <param name="client">
            * Output populated with newly created client if Success is returned.
            * </param>
            * <param name="io">
            * CloudIO callback interface instance to report progress
            * of all client operations to
            * </param>
            * <param name="proto">
            * Protocol used to interact with server
            * </param>
            * <param name="host">
            * hostname or IP of remote server
            * </param>
            * <param name="port">
            * port of remote server
            * </param>
            * <param name="basePath">
            * base path to remote cloud server on the given host.
            * MUST be properly URL encoded. Cannot be null!
            * </param>
            * <param name="user">
            * optional username to log into remote server with
            * </param>
            * <param name="password">
            * optional password to log into the remote server with
            * </param>
            * <param name="caCerts">
            * optional CA certificates to check server cert against
            * </param>
            * <param name="caCertsPassword">
            * password for the CA cert data
            * </param>
            * <returns>
            * Success if the client is created using the specified parameters,
            * IllegalArgument for other invalid arguments, including a 
            * a password was given without a username.
            * CommoInvalidTruststore if the caCert is invalid.
            * CommoInvalidTruststorePassword if caCertPassword
            * is incorrect.
            * </returns>
            */
            CommoResult CreateCloudClient(CloudClient ^%client,
                ICloudIO ^io,
                CloudIOProtocol proto,
                System::String ^host,
                int port,
                System::String ^basePath,
                System::String ^user,
                System::String ^password,
                array<System::Byte> ^caCerts,
                System::String ^caCertsPassword);

            /**
            * Destroy a CloudClient created by an earlier call to createCloudClient().
            * This will terminate all client operations, including ongoing transfers,
            * before completion.  Existing operations may or may not receive callbacks
            * during the destruction.
            *
            * <param name="client">
            * the client to destroy
            * </param>
            * <returns>
            * Success if the client is destroyed
            * </returns>
            */
            CommoResult DestroyCloudClient(CloudClient ^client);

            /**
             * <summary>
             * Initialize a Mission Package transfer of a file to the 
             * specified contact(s).  The list of contact(s) is updated
             * to indicate which contact(s) could not be sent to (considered "gone").
             * This call sets up the transfer and places
             * it into a pending state, returning an identifier that can be used
             * to correlate transfer status information back to this
             * request (it uniquely identifies this transfer).
             * To commence the actual transfer, call StartMissionPackageSend() with
             * the returned id. It is guaranteed that no status info
             * will be fed back for the transfer until it has been started.
             * Once the send has been started, transmission will be initiated as
             * soon as possible, but the actual processing by and transfer to
             * the receiver will take place asynchronously in the background.
             * The file being transferred must remain available at the
             * specified location until the asynchronous transaction is
             * entirely complete.
             * </summary>
             * <param name="xferId">
             * Assigned an integer that uniquely identifies this transfer;
             * this is assigned to only when ContactGone or Success is returned.
             * </param>
             * <param name="destinations">
             * List of UIDs for Contacts who are to receive the specified file.
             * This list may be modified to reflect "gone" contacts at the 
             * instant this call is made if ContactGone is returned. Note that
             * other contacts may be considered "gone" as asynchronous 
             * processing continues; this is specified in progress updates
             * to the IMissionPackageIO callback interface.
             * </param>
             * <param name="filePath">
             * Full (absolute) path to the file to send
             * </param>
             * <param name="fileName">
             * The name by which the remote system should refer to the file;
             * file name (no path) only.  This name does NOT have to be the
             * same as the local system's name of the file.
             * </param>
             * <param name="name">
             * A name for the transfer, used to generically refer to the
             * transfer locally and remotely.
             * </param>
             * <returns>
             * Success if the transfer is queued to all specified contacts,
             * ContactGone if one or more of the specified contacts are
             * "gone" while some of the contacts are queued successfully,
             * IllegalArgument if this Commo does not have file 
             * transfers setup via successful SetupMissionPackageIO call,
             * if all specified contacts are "gone",
             * or the local file could not be read.
             * </returns>
             */
            CommoResult SendMissionPackageInit(
                int % xferId,
                System::Collections::Generic::List<System::String ^> ^destinations,
                System::String ^filePath,
                System::String ^fileName,
                System::String ^name);


            /**
             * <summary>
             * Send a Mission Package file to the specified TAK server.  
             * The server is first queried to see if the file exists on server already.
             * If not, the file is transmitted to the server.
             * The query and the transmission (if needed) are
             * initiated as soon as possible, but this takes place asynchronously 
             * in the background.  The returned ID can be used to correlate 
             * transfer status information back to this request (it
             * uniquely identifies this transfer).
             * The file being transfered must remain available at the specified location
             * until the asynchronous transaction is entirely complete.
             * </summary>
             * <param name="xferId">
             * Assigned an integer that uniquely identifies this transfer;
             * this is assigned to only when Success is returned.
             * </param>
             * <param name="streamingRemoteId">
             * The remoteEndpointId from a currently valid
             * StreamingNetInterface, identifying the server to upload the
             * file to. If this is invalid, ContactGone is returned.
             * </param>
             * <param name="filePath">
             * Full (absolute) path to the file to send
             * </param>
             * <param name="fileName">
             * The name by which the remote system should refer to the file;
             * file name (no path) only.  This name does NOT have to be the
             * same as the local system's name of the file.
             * </param>
             * <returns>
             * Success if the transfer is queued to send to the specified
             * TAK server, ContactGone if streamingRemoteId does not
             * correlate to a currently valid StreamingNetInterface,
             * or IllegalArgument if this Commo does not have file 
             * transfers configured via SetupMissionPackageIO()
             * or the local file could not be read.
             * </returns>
             */
            CommoResult SendMissionPackageInit(int % xferId,
                System::String ^streamingRemoteId,
                System::String ^filePath,
                System::String ^fileName);
            
            /**
             * <summary>
             * Begins the transfer process for a pending mission package send
             * initialized by a prior call to SendMissionPackageInit().
             * The transmission is initiated as soon as possible, but the
             * actual processing by and transfer to the receiver will take
             * place asynchronously in the background as described in
             * SendMissionPackageInit().
             *
             * Returns IllegalArgument if the specified id is not a valid
             * id for a mission package send.
             * </summary>
             * <param name="id">
             * The identifier of the mission package send to start, returned
             * by prior successful call to SendMissionPackageInit()
             * </param>
             */
            CommoResult StartMissionPackageSend(int id);

            /**
                * <summary>
                * Obtains the UIDs of all contacts known via all active interfaces
                * </summary>
                * <returns>An array of UID strings for known contacts</returns>
                */
            array<System::String ^> ^GetContactList();
                
            /**
             * <summary>
             * Configure a "known endpoint" contact. This is 
             * a non-discoverable contact for whom we already know an endpoint.
             * The IP address is specified in "dotted decimal" string form; hostnames
             * are not allowed, only IP addresses.
             * If the address is a multicast address, messages sent to the created
             * Contact will be multicasted to all configured broadcast interfaces,
             * regardless of message type.
             * The port specifies a UDP port endpoint on the specified host.
             * The UID must be unique from existing known UIDs and should be chosen
             * to be unique from other potentially self-discovered contacts;
             * any message-based discovery for a contact with the specified UID
             * will be ignored; the endpoint remains fixed to the specified one.
             * Specifying a UID of an already configured known endpoint contact
             * allows it to be reconfigured to a new endpoint or callsign;
             * specify nullptr for callsign and ipAddr (and any port number)
             * to delete.
             * Specifying nullptr for either callsign or ipAddr but not both results
             * in IllegalArgument.
             * Specifying a ContactUID for an already known via discovery endpoint,
             * or nullptr ipAddr for a contact that was not previously configured
             * via this call, or an unparsable ipAddr 
             * results in IllegalArgument. Success is returned
             * if the contact is added, changed, or removed as specified.
             * </summary>
             */
            CommoResult ConfigKnownEndpointContact(System::String ^contact,
                                                   System::String ^callsign,
                                                   System::String ^ipAddr,
                                                   int port);
                
            /**
                * <summary>
                * Register a IContactPresenceListener to receive notification of
                * contact presence changes
                * </summary>
                * <returns>
                * Success if listener is added successfully, IllegalArgument
                * if the listener is already registered or another error occurs.
                * </returns>
                */
            CommoResult AddContactPresenceListener(
                IContactPresenceListener ^listener);

            /**
                * <summary>
                * Deregister a IContactPresenceListener; upon completion it will
                * no longer receive contact presence updates.
                * </summary>
                * <returns>
                * Success if listener is removed successfully, IllegalArgument
                * if the listener was not previously registered or another
                * error occurs.
                * </returns>
                */
            CommoResult RemoveContactPresenceListener(
                IContactPresenceListener ^listener);

            /**
             * <summary>
             * Generate a private key and return as a string in PEM format,
             * encoded with the given password.
             * </summary>
             * <param name="password">
             * Password to use when encoding the private key.
             * </param>
             * <param name="keyLength">
             * Length of key to generate, in bits.
             * </param>
             * <returns>
             * String representation of the PEM formatted private key, or null
             * on error. 
             * </returns>
             */
            System::String ^GenerateKeyCryptoString(System::String ^password,
                                                    int keyLength);

            /**
             * <summary>
             * Generate a CSR and return as a string in PEM format.
             * CSR will be signed by the given key, which uses the given password.
             * CSR will contain the entries specified.
             * </summary>
             * <param name="csrDnEntries">
             * Map of key/value pairs of strings that will be included in the CSR 
             * to form the DN.   The keys must be valid for CSR DN entries.
             * </param>
             * <param name="pkeyPem">
             * Private key to use for the CSR, in PEM format
             * </param>
             * <param name="password">
             * Password to used to decode the private key.
             * </param>
             * <returns>
             * String representation of the PEM formatted CSR, or null
             * on error. 
             * </returns> 
             */
            System::String ^GenerateCSRCryptoString(
                System::Collections::Generic::IDictionary<System::String ^, System::String ^>
                    ^csrDnEntries,
                System::String ^pkeyPem, System::String ^password);
                    
            /**
            * <summary>
            * Generate a keystore from the given constituent elements.
            * The keystore is returned as a pkcs#12 keystore encoded as a string in 
            * base64 format.
            * </summary>
            * <param name="certPem">
            * The certificate as a string in PEM format
            * </param>
            * <param name="caPem">
            * Array of ca certificates, in order, each as a string in PEM format.
            * </param>
            * <param name="pkeyPem">
            * Private key as a string in PEM format
            * </param>
            * <param name="password">
            * Password used to decode the private key pkeyPem
            * </param>
            * <param name="friendlyName">
            * Friendly name to use in the keystore
            * </param>
            * <returns>
            * String representation of the pkcs#12 keystore, encoded using base64,
            * or null on error.
            * </returns>
            */
            System::String ^GenerateKeystoreCryptoString(System::String ^certPem,
                array<System::String ^> ^caPem,
                System::String ^pkeyPem, System::String ^password,
                System::String ^friendlyName);

            /**
            * <summary>
            * Generate a self-signed certificate with private key.
            * Values in the cert are filled with nonsense data.
            * Certificates from this cannot be used to interoperate with commerical
            * tools due to lack of signature by trusted CAs.
            * The returned data is in pkcs12 format protected by the supplied
            * password, which cannot be null.
            * </summary>
            */
            array<System::Byte> ^GenerateSelfSignedCert(System::String ^password);


        private:
            impl::CommoImpl ^impl;

            Object ^m_InboundInterfaceLock;
        };


    }
}

#endif /* COMMO_H_ */
