#pragma once

#include "commoresult_cx.h"
#include "commologger_cx.h"
#include "netinterface_cx.h"
#include "missionpackage_cx.h"
#include "contactuid_cx.h"
#include "cotmessageio_cx.h"

#include <collection.h>

namespace TAK
{
    namespace Commo
    {
        namespace impl 
        {
            class CommoImpl;
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
        public ref class Commo sealed
        {
        public:
            Commo(ICommoLogger ^logger, Platform::String ^ourUID, Platform::String ^ourCallsign);
            virtual ~Commo();

            /**
            * <summary>
            * Enables mission package sending and receiving
            * functionality for this Commo instance. The caller must provide
            * an implementation of the IMissionPackageIO interface that
            * implements various functions related to sending and receiving
            * packages, as well as an available, open TCP port number to use
            * for the local web server used to share local mission packages
            * with remote users.
            * This function can only be successfully invoked once; once
            * mission package IO is enabled, it cannot be disabled.
            * </summary>
            * <param name="missionPackageIO">
            * implements mission package IO integration with client application
            * </param>
            * <param name="localWebPort">
            * local TCP port number to use for the file transfer web server
            * </param>
            */
            CommoResult enableMissionPackageIO(
                IMissionPackageIO ^missionPackageIO,
                int localWebPort);

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
            void shutdown();

            /**
            * <summary>
            * Sets the callsign of the local system.
            * This method may be used to change the local system's
            * callsign after initialization.
            * </summary>
            * <param name="callsign">callsign to set</param>
            */
            void setCallsign(Platform::String ^callsign);

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
            void setTTL(int ttl);

            /**
            * <summary>
            * Add a new outbound broadcasting interface.
            * Directs the Commo instance to monitor the local network
            * interface with the given hardware address and, when the
            * interface is active, send broadcast messages matching any of
            * the given message types out said interface to the specific
            * multicast address and port.  The returned interface is valid
            * until removed via removeBroadcastInterface or this Commo
            * object is closed.
            * </summary>
            * <returns>
            * an internal object implementing the PhysicalNetInterface
            * protocol, or nullptr if an error occurs.
            * </returns>
            */
            PhysicalNetInterface ^addBroadcastInterface(
                const Platform::Array<uint8> ^hwAddress,
                const Platform::Array<CoTMessageType> ^types,
                Platform::String ^mcastAddr,
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
            CommoResult removeBroadcastInterface(PhysicalNetInterface ^iface);

            /**
            * <summary>Add a new listening interface.
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
            * an internal object implementing the PhysicalNetInterface
            * protocol, or nullptr if an error occurs.
            * </returns>
            */
            PhysicalNetInterface ^addInboundInterface(
                const Platform::Array<uint8> ^hwAddress,
                int port,
                const Platform::Array<Platform::String ^> ^mcastAddrs);

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
            CommoResult removeInboundInterface(PhysicalNetInterface ^iface);

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
            * password to decrypt the client's certificate and trust store.
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
            * <returns>
            * an internal object implementing the StreamingNetInterface
            * protocol, or nullptr if an error occurs.
            * </returns>
            */
            StreamingNetInterface ^addStreamingInterface(
                Platform::String ^hostname,
                int port,
                const Platform::Array<CoTMessageType> ^types,
                const Platform::Array<uint8> ^clientCert,
                const Platform::Array<uint8> ^trustStore,
                Platform::String ^certPassword,
                Platform::String ^username,
                Platform::String ^password);

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
            CommoResult removeStreamingInterface(StreamingNetInterface ^iface);

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
            CommoResult addInterfaceStatusListener(
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
            CommoResult removeInterfaceStatusListener(
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
            CommoResult addCoTMessageListener(ICoTMessageListener ^listener);
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
            CommoResult removeCoTMessageListener(ICoTMessageListener ^listener);

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
            CommoResult sendCoT(
                Windows::Foundation::Collections::IVector<Platform::String ^> ^destinations,
                Platform::String ^cotMessage);

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
            CommoResult broadcastCoT(Platform::String ^cotMessage);




            /**
            * <summary>
            * Send a mission package file to one or more remote contacts.
            * The provided file will be transferred to a network-accessible
            * location for the given remote contacts; the remote contacts will
            * then be sent the file transfer request.  The transfers to each
            * client are then made asynchronously in the background.
            * A transfer identifier
            * is associated with this transfer request and can be used to
            * correlate file transfer progress messages with the this
            * file transfer (see IMissionPackageIO).
            * Note that the specified file MUST be readable until the
            * transfer has been completed (or fails) for all specified
            * recipients.
            * </summary>
            * <param name="xferId">
            * Assigned an integer that uniquely identifies this transfer;
            * this is assigned to only when ContactGone is returned and not
            * all contacts are returned as gone, or Success is returned.
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
            * "gone", IllegalArgument if this Commo does not have file
            * transfers enabled or the local file could not be read.
            * </returns>s
            */
            CommoResult sendMissionPackage(
                int* xferId,
                Windows::Foundation::Collections::IVector<Platform::String ^> ^destinations,
                Platform::String ^filePath,
                Platform::String ^fileName,
                Platform::String ^name);


            /**
            * <summary>
            * Obtains the UIDs of all contacts known via all active interfaces
            * </summary>
            * <returns>An array of UID strings for known contacts</returns>
            */
            Platform::Array<Platform::String ^> ^getContactList();

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
            CommoResult addContactPresenceListener(
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
            CommoResult removeContactPresenceListener(
                IContactPresenceListener ^listener);

        private:
            impl::CommoImpl* impl;
        };
    }
}