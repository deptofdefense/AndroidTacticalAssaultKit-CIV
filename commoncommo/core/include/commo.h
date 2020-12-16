#ifndef COMMO_H_
#define COMMO_H_


#include "commoresult.h"
#include "commologger.h"
#include "commoutils.h"
#include "netinterface.h"
#include "missionpackage.h"
#include "contactuid.h"
#include "cotmessageio.h"
#include "simplefileio.h"
#include "cloudio.h"
#include "fileioprovider.h"

#include <memory>
#include <stdint.h>
#include <stddef.h>

namespace atakmap {
namespace commoncommo {

namespace impl
{
    struct CommoImpl;
}

// Workaround quirks of operating on marine air ground tablet
#define QUIRK_MAGTAB 1

class COMMONCOMMO_API Commo {
public:
    // Both uid and callsign are copied internally
    Commo(CommoLogger *logger, const ContactUID *ourUID, const char *ourCallsign,
          netinterfaceenums::NetInterfaceAddressMode addrMode = netinterfaceenums::MODE_PHYS_ADDR);
    virtual ~Commo();

    // Configures mission package transfer helper client-side interface
    // for this Commo instance.
    // Once configured, the helper interface implementation must remain valid
    // until shutdown() is invoked or this Commo instance is destroyed;
    // the interface implementation cannot be swapped or deactivated.
    // After a successful return, other Mission Package related
    // api calls may be made.
    // Returns SUCCESS if IO implementation is accepted, or ILLEGAL_ARGUMENT
    // if missionPacakgeIO is NULL or if setupMissionPackageIO has
    // previously been called successfully on this Commo instance.
    CommoResult setupMissionPackageIO(MissionPackageIO *missionPackageIO);

    CommoResult enableSimpleFileIO(SimpleFileIO *simpleFileIO);

    void shutdown();
    
    // Enable/disable workarounds for various quirks.
    // Changing this may cause restarts/reconnects of various interfaces
    // depending on the quirk changed.
    void setWorkaroundQuirks(int quirksMask);

    // Change our callsign. Callsign is copied internally.
    void setCallsign(const char *callsign);

    // Register the FileIOProvider to be used.
    void registerFileIOProvider(std::shared_ptr<FileIOProvider>& provider);

    // Deregister the FileIOProvider to be used.
    void deregisterFileIOProvider(const FileIOProvider& provider);

    // Set what type of endpoint (mesh/local or streaming/server) to use
    // when both types are available and current/up to date.
    // Defaults to false (prefer mesh/local).
    void setPreferStreamEndpoint(bool preferStream);

    // Advertise our endpoint as UDP instead of TCP.  USE WITH CAUTION -
    // this will cause remote devices to send direct messages to us
    // using unreliable UDP; messages my be dropped as it doesn't ensure
    // delivery or report errors!
    void setAdvertiseEndpointAsUdp(bool en);

    // Sets cryptographic keys to use for encrypting/decrypting all
    // non-streaming CoT traffic.  This is symmetric in that if you encrypt
    // outbound, everything is expected to be encrypted inbound and will
    // fail if it isn't.
    // Keys must each be 256-bit in length and must be unique from each other.
    // Pass NULL for both keys to disable crypto for non-streaming traffic
    // (this is the default state)
    // Returns SUCCESS or INVALID_ARGUMENT if keys are identical
    CommoResult setCryptoKeys(const uint8_t *authKey, const uint8_t *cryptoKey);

    // Sets global option on if datagram sockets should allow for local address reuse when binding
    // Defaults to off/false.  Changing causes all sockets to be torn down and rebuilt.
    // NOTE: PRESENTLY ONLY SUPPORTED ON LINUX AND WINDOWS. Enabling on any other platform will cause undefined behavior!
    void setEnableAddressReuse(bool en);

    // Sets global option on if datagram sockets used for multicasting
    // should send out messages such that they will be looped back to
    // other sockets on the same system that subscribe to those multicast
    // groups.  Defaults to off/false.  
    // Changing causes all sockets to be torn down and rebuilt.
    // NOTE: On Windows, this method has no effect as Windows does not
    // provide a means to control this on the sending side.  Windows
    // controls this on the receiving side.
    void setMulticastLoopbackEnabled(bool en);

    // Sets global TTL for broadcast messages
    void setTTL(int ttl);
    
    // Sets global UDP timeout value, in seconds; if the given # of seconds
    // elapses without a socket receiving data, it will be destroyed
    // and rebuilt. If it was a multicast socket, multicast joins will
    // be re-issued. 0 means never timeout. Default is 30s
    void setUdpNoDataTimeout(int seconds);

    // Sets global TCP connection timeout for all connections except for
    // mission package uploads/downloads
    void setTcpConnTimeout(int seconds);

    // Controls if streams should be monitored or not
    void setStreamMonitorEnabled(bool enable);
    
    // Get protocol version in use by broadcast system
    int getBroadcastProto();


    // Sets the local web server port for peer-to-peer transfers,
    // or disables the function if passed MP_LOCAL_PORT_DISABLE.
    // Default state is as though this were passed MP_LOCAL_PORT_DISABLE.
    //
    // Returns SUCCESS if the provided TCP port number is successfully
    // opened and usable for the local web server (or if MP_LOCAL_PORT_DISABLE
    // is passed), or ILLEGAL_ARGUMENT if the passed port cannot be used 
    // or if setupMissionPackageIO() has not previously been invoked
    // successfully. NOTE: on failure, local web server is DISABLED.
    // NOTE: Changing the port or disabling may cause in-progress sends
    // of MPs to be aborted!
    CommoResult setMissionPackageLocalPort(int localWebPort);
    
    // Enables or disables mission package sends via
    // TAK servers. By default, mission package sends via TAK server
    // are enabled (once setupMissionPackageIO() is successfully invoked).
    // Disabling may cause existing outbound transfers to be aborted.
    void setMissionPackageViaServerEnabled(bool enabled);

    // Set port for http interactions with TAK server, primarily
    // during MP uploads. Default 8080
    // OK or ILLEGAL_ARGUMENT if value is out of range
    CommoResult setMissionPackageHttpPort(int port);

    // Set port for https interactions with TAK server, primarily
    // during MP uploads. Default 8443
    // OK or ILLEGAL_ARGUMENT if value is out of range
    CommoResult setMissionPackageHttpsPort(int port);

    // Set number of attempts to receive a mission package.
    // Minimum value is one (1), default value is 10.
    // New settings influence subsequent transfers;
    // currently ongoing transfers use settings in place at the time they
    // began.
    // Returns SUCCESS if argument is legitimate value, ILLEGAL_ARGUMENT
    // if it is out of legitimate range.
    CommoResult setMissionPackageNumTries(int nTries);
    
    // Set timeout, in seconds, for initiating connections to remote
    // hosts when transferring mission packages.  This is used when receiving
    // mission packages from TAK servers and/or other devices, as well 
    // as uploading mission packages to TAK servers.
    // Minimum value is five (5) seconds, default value is 90 seconds.
    // New settings influence subsequent transfers;
    // currently ongoing transfers use settings in place at the time they
    // began.
    // Returns SUCCESS if argument is legitimate value, ILLEGAL_ARGUMENT
    // if it is out of legitimate range.
    CommoResult setMissionPackageConnTimeout(int seconds);
    
    // Set timeout, in seconds, for data transfers.  After this number
    // of seconds has elapsed with no data transfer progress taking place,
    // the transfer attempt is considered a failure.
    // This is used when receiving mission packages.
    // Minimum value is 15 seconds, default value is 120 seconds.
    // New settings influence subsequent transfers;
    // currently ongoing transfers use settings in place at the time they
    // began.
    // Returns SUCCESS if argument is legitimate value, ILLEGAL_ARGUMENT
    // if it is out of legitimate range.
    CommoResult setMissionPackageTransferTimeout(int seconds);

    // Sets the local https web server parameters for peer-to-peer transfers,
    // or disables the function if passed MP_LOCAL_PORT_DISABLE.
    // Default state is as though this were passed MP_LOCAL_PORT_DISABLE.
    // Cert must be non-zero length and non-null.
    //
    // Returns SUCCESS if the provided TCP port number is successfully
    // opened and usable for the local web server (or if MP_LOCAL_PORT_DISABLE
    // is passed), ILLEGAL_ARGUMENT if the passed port cannot be used
    // or if setupMissionPackageIO() has not previously been invoked
    // successfully, INVALID_CERT if certificate could not be read,
    // or INVALID_CERT_PASSWORD if cert password is incorrect. 
    // NOTE: on any failure, local web server is DISABLED.
    // NOTE: Changing the port or disabling may cause in-progress sends
    // of MPs to be aborted!
    CommoResult setMissionPackageLocalHttpsParams(int port, const uint8_t *cert,
                               size_t certLen, const char *certPass);

    // NULL on error
    PhysicalNetInterface *addBroadcastInterface(const HwAddress *hwAddress, const CoTMessageType *types, size_t nTypes, const char *mcastAddr, int destPort);
    PhysicalNetInterface *addBroadcastInterface(const CoTMessageType *types, size_t nTypes, const char *unicastAddr, int destPort);
    // OK or ILLEGAL_ARGUMENT
    CommoResult removeBroadcastInterface(PhysicalNetInterface *iface);

    // NULL on error
    // Cannot mix generic and non-generic on same port, even across
    // different hwAddress's
    PhysicalNetInterface *addInboundInterface(const HwAddress *hwAddress, int port, const char **mcastAddrs, size_t nMcastAddrs, bool asGeneric);
    // OK or ILLEGAL_ARGUMENT
    CommoResult removeInboundInterface(PhysicalNetInterface *iface);

    // NULL on error
    TcpInboundNetInterface *addTcpInboundInterface(int port);
    // OK or ILLEGAL_ARGUMENT
    CommoResult removeTcpInboundInterface(TcpInboundNetInterface *iface);


    // NULL on error
    // errCode, if non-NULL, will give add'l details on error
    StreamingNetInterface *addStreamingInterface(const char *hostname, int port,
                                                 const CoTMessageType *types,
                                                 size_t nTypes,
                                                 const uint8_t *clientCert, size_t clientCertLen,
                                                 const uint8_t *caCert, size_t caCertLen,
                                                 const char *certPassword,
                                                 const char *caCertPassword,
                                                 const char *username, const char *password,
                                                 CommoResult *errCode = NULL);
    // OK or ILLEGAL_ARGUMENT
    CommoResult removeStreamingInterface(StreamingNetInterface *iface);


    // OK or ILLEGAL_ARGUMENT if listener already exists
    CommoResult addInterfaceStatusListener(InterfaceStatusListener *listener);
    // OK or ILLEGAL_ARGUMENT if listener was not already added
    CommoResult removeInterfaceStatusListener(InterfaceStatusListener *listener);

    // OK or ILLEGAL_ARGUMENT if listener already exists
    CommoResult addCoTMessageListener(CoTMessageListener *listener);
    // OK or ILLEGAL_ARGUMENT if listener was not already added
    CommoResult removeCoTMessageListener(CoTMessageListener *listener);

    // OK or ILLEGAL_ARGUMENT if listener already exists
    CommoResult addGenericDataListener(GenericDataListener *listener);
    // OK or ILLEGAL_ARGUMENT if listener was not already added
    CommoResult removeGenericDataListener(GenericDataListener *listener);

    // OK or ILLEGAL_ARGUMENT if listener already exists
    CommoResult addCoTSendFailureListener(CoTSendFailureListener *listener);
    // OK or ILLEGAL_ARGUMENT if listener was not already added
    CommoResult removeCoTSendFailureListener(CoTSendFailureListener *listener);


    // OK, CONTACT_GONE, or ILLEGAL_ARGUMENT
    // if CONTACT_GONE, the references in the destinations list are reordered
    // and the size is updated to indicate
    // which and how many of the provided Contacts are considered 'gone' OR
    // those that could not be sent to because the send method was incompatible
    // with their only known methods of reachability; elements
    // of the list after the call completes are the contacts which were
    // considered 'gone' or unknown via the specified send method.
    // sendMethod specifies how the message can be delivered - if the contact
    // does not have an endpoint matching the specified method(s), the
    // message will not be sent to them, GONE will be returned, and they
    // will be present at the head of the returned list. Specifying more
    // than one type does not mean the message will be sent via all
    // those methods; it merely specifies that those methods are allowed
    // If ILLEGAL_ARGUMENT, cotMessage was not recognized as a valid message.
    // Destination ContactUIDs must remain valid only for the duration of this call!
    CommoResult sendCoT(ContactList *destinations, const char *cotMessage, CoTSendMethod sendMethod = SEND_ANY);
    CommoResult broadcastCoT(const char *cotMessage, CoTSendMethod sendMethod = SEND_ANY);
    // Sends the cot message directly to the host on the tcp port specified
    // Connects, sends, disconnects. This method is deprecated
    // and here only to help support backwards compatibility
    CommoResult sendCoTTcpDirect(const char *host, int port, const char *cotMessage);

    // Sends to all streaming interfaces with destination set to
    // indicate to the server that this is a server control message.
    // streamingRemoteId is the identifier from a currently valid
    //    StreamingNetInterface, or NULL to send to all streaming servers.
    // OK or ILLEGAL_ARGUMENT if message is invalid or 
    //    streamingRemoteId is invalid
    CommoResult sendCoTServerControl(const char *streamingRemoteId,
                                     const char *cotMessage);
    
    // Sends to one specific, or all, streaming interfaces with destination set 
    // to indicate the specified mission
    // streamingRemoteId is the identifier from a currently valid
    //    StreamingNetInterface, or NULL to send to all streaming servers.
    // OK or ILLEGAL_ARGUMENT if message is invalid or streamingRemoteId
    //    is invalid
    CommoResult sendCoTToServerMissionDest(const char *streamingRemoteId,
                                           const char *mission,
                                           const char *cotMessage);
    

    // OK, COMMO_INVALID_CACERT, or ILLEGAL_ARGUMENT.
    // COMMO_INVALID_CACERT means caCert not valid,
    // COMMO_INVALID_CACERT_PASSWORD means caCert not valid
    // ILLEGAL_ARGUMENT means that URL has unsupported protocol,
    //     a password was given without a username,
    //     or that this Commo instance has not yet had SimpleFileIO enabled
    //     via enableSimpleFileIO().
    // Protocol *must* be specified in the URL.  Currently supported protocols:
    //     ftp 
    //     ftps (ssl)
    // For protocols with (ssl), server's cert is checked to be trusted
    //      against the supplied caCert;  if not given (NULL with caCertLen 0)
    //      then the server's cert is not checked at all. caCertPassword
    //      may be NULL for no password needed.
    //      caCert, caCertLen, caCertPassword are ignored for protocols
    //      not marked (ssl)
    // remoteURL *must* be URL escape/quoted (no bare characters that are
    //      illegal in URLs). Do NOT include auth/login/password parameters
    //      in the URL!
    // remoteUsername is the username to login as during the transaction, or
    //      NULL for no login
    // remotePassword is the password to login with, or NULL if not needed.
    //      Specifying non-NULL with a NULL remoteUsername will return
    //      ILLEGAL_ARGUMENT
    CommoResult simpleFileTransferInit(int *xferId,
            bool isUpload,
            const char *remoteURL, // MUST BE ESCAPED/QUOTED
            const uint8_t *caCert, size_t caCertLen,
            const char *caCertPassword,
            const char *remoteUsername,
            const char *remotePassword,
            const char *localFileName);
    // ILLEGAL_ARGUMENT if xferId not an initialized transfer, else OK
    CommoResult simpleFileTransferStart(int xferId);
    
    // pass non-null means user must be non-null
    // Use null user and pass for no login
    // For ssl protocols, if caCerts not given, all certs will be accepted
    // caCerts is pkcs12 encoded
    // basePath must be URL encoded!
    // "io" must exist until destroy call completes!
    CommoResult createCloudClient(CloudClient **result, 
                         CloudIO *io,
                         CloudIOProtocol proto,
                         const char *host,
                         int port,
                         const char *basePath,
                         const char *user,
                         const char *pass,
                         const uint8_t *caCerts,
                         size_t caCertsLen,
                         const char *caCertPass);

    CommoResult destroyCloudClient(CloudClient *client);

    // OK, ILLEGAL_ARGUMENT, or CONTACT_GONE
    // if CONTACT_GONE, the references in the destinations list are reordered
    // and the size is updated to indicate
    // which and how many of the provided Contacts are considered 'gone'; elements
    // of the list after the call completes are the contacts which are 'gone'.
    // Note this detects only contacts "gone" at the instant of the call; if
    // a contact disappears after the transmission is accepted then that status
    // will be signalled by the MissionPackageIO status callback.
    // As a special case, if *all* supplied contacts are "gone" then
    // ILLEGAL_ARGUMENT is returned instead to indicate the transfer is invalid
    // xferid is valid except ILLEGAL_ARGUMENT, in which case no transfer
    // is initiated.
    // ILLEGAL_ARGUMENT means the file to transfer could not be read, or that
    // this Commo was created without enabling MissionPackageIO via
    // successful call to setupMissionPackageIO
    // (and thus transfers are disabled).
    // Destination ContactUIDs must remain valid only for the duration of this call.
    // filePath is the full and complete path to the file to send
    // fileName is the name the remote receiver is asked to use for the received
    // file.
    // name is a simple name for the transmission (required by CoT message)
    CommoResult sendMissionPackageInit(int *xferId,
            ContactList *destinations,
            const char *filePath,
            const char *fileName,
            const char *name);

    // OK, ILLEGAL_ARGUMENT, or CONTACT_GONE
    // xferid is valid only on OK return.
    // ILLEGAL_ARGUMENT means the file could not be read this
    //   Commo instance was created without enabling MissionPackageIO via
    //   successful call to setupMissionPackageIO (and thus transfers are
    //   disabled).
    // CONTACT_GONE means the endpoint specified is invalid
    // Sends a mission package to a tak server.
    // First checks to see if the file exists on server, then uploads
    // if it does not.  Completion and result of the transfer are reported
    // via the missionPackageSendStatus callback of the registered
    // MissionPackageIO implementation.
    // streamingRemoteId is the identifier from a currently valid
    //    StreamingNetInterface.
    // filePath is the full and complete path to the file to send.
    // fileName is the name the tak server is asked to use for the uploaded
    // file (may differ from physical file name; up to caller).
    CommoResult sendMissionPackageInit(int *xferId,
            const char *streamingRemoteId,
            const char *filePath,
            const char *fileName);

    // OK or ILLEGAL_ARGUMENT if xferId not valid.  This starts
    // transfers created by both forms of sendMissionPackageInit().    
    CommoResult sendMissionPackageStart(int xferId);



    // Obtains a list of ContactUIDs known at this moment in time.
    // Return value indicates number of items in the list (which could be 0).
    // Caller must release the list when done with it by passing to
    // freeContactList (even if it contained 0 contacts).
    // Note that the contact list is never reclaimed via other means (including
    // deleting this Commo object).
    const ContactList *getContactList();
    // Frees a contact list and its elements which were
    // previously obtained via getContactList().
    static void freeContactList(const ContactList *contactList);


    // Configure a "known endpoint" contact. This is 
    // a non-discoverable contact for whom we already know an endpoint.
    // The IP address is specified in "dotted decimal" string form; hostnames
    // are not allowed, only IP addresses.
    // If the address is a multicast address, messages sent to the created
    // Contact will be multicasted to all configured broadcast interfaces,
    // regardless of message type.
    // The port specifies a UDP port endpoint on the specified host.
    // The UID must be unique from existing known UIDs and should be chosen
    // to be unique from other potentially self-discovered contacts;
    // any message-based discovery for a contact with the specified UID
    // will be ignored; the endpoint remains fixed to the specified one.
    // Specifying a UID of an already configured known endpoint contact
    // allows it to be reconfigured to a new endpoint or callsign;
    // specify NULL for callsign and ipAddr (and any port number) to delete.
    // Specifying NULL for either callsign or ipAddr but not both results
    // in ILLEGAL_ARGUMENT.
    // Specifying a ContactUID for an already known via discovery endpoint,
    // or NULL ipAddr for a contact that was not previously configured
    // via this call, or an unparsable ipAddr 
    // results in ILLEGAL_ARGUMENT. SUCCESS is returned
    // if the contact is added, changed, or removed as specified.
    CommoResult configKnownEndpointContact(const ContactUID *contact,
                                           const char *callsign,
                                           const char *ipAddr,
                                           int destPort);

    // OK or ILLEGAL_ARG if listener already present
    CommoResult addContactPresenceListener(ContactPresenceListener *listener);
    // OK or ILLEGAL_ARGUMENT if listener not previously registered
    CommoResult removeContactPresenceListener(ContactPresenceListener *listener);


    // Next 3 support CSR gen for tak server cert requests. Results
    // NULL for errors, requested string for success.  Free result
    // using freeCryptoString().
    char *generateKeyCryptoString(const char *password, const int keyLen);
    char *generateCSRCryptoString(const char **dnEntryKeys, const char **dnEntryValues,
                                  size_t nDnEntries, const char *pkeyPem, 
                                  const char *password);
    char *generateKeystoreCryptoString(const char *certPem, const char **caPem, 
                           size_t nCa, const char *pkeyPem,
                           const char *password, 
                           const char *friendlyName);
    void freeCryptoString(char *cryptoString);
    
    size_t generateSelfSignedCert(uint8_t **cert, const char *password);
    void freeSelfSignedCert(uint8_t *cert);


    
    // Next 2 - convert between xml and tak protocol representation
    // SUCCESS if converted ok and result pointers allocated and filled.
    // Xml blobs are null terminated. Proto blobs are not.
    // ILLEGAL_ARGUMENT otherwise.
    // Allocated returns (on success) must be freed via takmessageFree()
    CommoResult cotXmlToTakproto(char **protodata, size_t *dataLen,
                                 const char *cotXml, int desiredVersion);
    CommoResult takprotoToCotXml(char **cotXml,
                                 const char *protodata,
                                 const size_t dataLen);
    void takmessageFree(char *takmessage);

private:
    COMMO_DISALLOW_COPY(Commo);


    atakmap::commoncommo::impl::CommoImpl *impl;
};


}
}

#endif /* COMMO_H_ */
