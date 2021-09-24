#ifndef IMPL_CONTACTMANAGER_H_
#define IMPL_CONTACTMANAGER_H_

#include "commologger.h"
#include "contactuid.h"
#include "commotime.h"
#include "netinterface.h"
#include "netsocket.h"
#include "datagramsocketmanagement.h"
#include "streamingsocketmanagement.h"
#include "tcpsocketmanagement.h"
#include "threadedhandler.h"
#include "takmessage.h"
#include "commothread.h"
#include "internalutils.h"

#include <stdexcept>
#include <map>
#include <set>


namespace atakmap {
namespace commoncommo {
namespace impl
{


class CoTEndpoint
{
public:
    typedef enum {
        DATAGRAM,
        TCP,
        STREAMING
    } InterfaceType;

    InterfaceType getType() const;
    CommoTime getLastRxTime() const;

protected:
    CoTEndpoint(InterfaceType type);
    virtual ~CoTEndpoint();

    COMMO_DISALLOW_COPY(CoTEndpoint);

    void touchRxTime();

private:
    InterfaceType type;
    CommoTime lastRxTime;
};


class DatagramCoTEndpoint : public CoTEndpoint
{
public:
    DatagramCoTEndpoint(const std::string &endpointString) COMMO_THROW (std::invalid_argument);
    DatagramCoTEndpoint(const std::string &ipString, int port) COMMO_THROW (std::invalid_argument);
    virtual ~DatagramCoTEndpoint();

    const NetAddress *getBaseAddr() const;
    const NetAddress *getNetAddr(CoTMessageType cotType) const;

    // Compare and update if needed
    // Always bump the timestamp
    void updateFromCoTEndpoint(const std::string &endpointString) COMMO_THROW (std::invalid_argument);

    void updateKnownEndpoint(const std::string &ipString, int port) COMMO_THROW (std::invalid_argument);

    std::string getEndpointString();

private:
    COMMO_DISALLOW_COPY(DatagramCoTEndpoint);

    NetAddress *baseAddr;
    NetAddress *chatAddr;
    NetAddress *saAddr;
    std::string cachedEndpointString;
    // configured manually, known, non-discovered contact
    bool isKnown;
    // port only valid for "known" contact
    int port;
};


class TcpCoTEndpoint : public CoTEndpoint
{
public:
    //TcpCoTEndpoint(const std::string &endpointString) COMMO_THROW (std::invalid_argument);
    TcpCoTEndpoint(const std::string &ipString, int port) COMMO_THROW (std::invalid_argument);
    virtual ~TcpCoTEndpoint();

    const std::string getHostString() const;
    const int getPortNum(CoTMessageType cotType) const;

    // Compare and update if needed
    // Always bump the timestamp
    void updateFromCoTEndpoint(const std::string &endpointString,
                               int port) COMMO_THROW (std::invalid_argument);

private:
    COMMO_DISALLOW_COPY(TcpCoTEndpoint);

    std::string cachedEndpointString;
    int port;
};


class StreamingCoTEndpoint : public CoTEndpoint
{
public:
    StreamingCoTEndpoint(const std::string &endpoint);
    virtual ~StreamingCoTEndpoint();

    // some method of updating the connection id and timestamp bump
    void updateFromStreamingEndpoint(const std::string &sourceEndpoint);

    std::string getEndpointString();

private:
    // identifies the connection
    std::string endpointString;
};






struct ContactState
{
    ContactState(bool knownEP);
    ~ContactState();

    // Indicates if protoInfExpireTime is actually
    // an expiration of protoInf (and, if protoInf
    // was obtained from a legit version support msg
    // and not "guessed")
    bool protoInfValid;
    // only valid if protoInfValid
    CommoTime protoInfExpireTime;
    
    // Pair of version - min, max
    std::pair<unsigned int, unsigned int> protoInf;
    
    // last rx time of most recently received non-stream endpoint
    // plus protocol timeout
    // If no non-stream endpoints or knownEndpoint, then ZERO_TIME
    CommoTime meshEndpointExpireTime;
    
    // TAK protocol version # of the last mesh endpoint-advertising
    // contact message. 0 if mesh EP never received or is known EP
    unsigned int lastMeshEndpointProto;

    // Will always have one endpoint, maybe multiple.
    StreamingCoTEndpoint *streamEndpoint;
    DatagramCoTEndpoint *datagramEndpoint;
    TcpCoTEndpoint *tcpEndpoint;
    std::string callsign;
    // true for a "known" endpoint; 
    // not configured/found via discovery, only via API
    bool knownEndpoint;
    thread::Mutex mutex;
};


struct ContactUIDComp
{
    bool operator()(const ContactUID * const &a, const ContactUID * const &b) const {
        const size_t n = a->contactUIDLen < b->contactUIDLen ? a->contactUIDLen : b->contactUIDLen;
        for (size_t i = 0; i < n; ++i) {
            if (a->contactUID[i] < b->contactUID[i])
                return true;
            if (a->contactUID[i] > b->contactUID[i])
                return false;
        }
        return a->contactUIDLen < b->contactUIDLen;
    }
};






class ContactManager : public ThreadedHandler, public DatagramListener,
                       public StreamingMessageListener,
                       public InterfaceStatusListener
{
public:
    ContactManager(CommoLogger *logger, DatagramSocketManagement *dgMgmt,
                            TcpSocketManagement *tcpMgmt,
                            StreamingSocketManagement *streamMgmt);
    virtual ~ContactManager();


    // DatagramListener
    virtual void datagramReceived(const std::string *endpointId,
                      const NetAddress *sender, const TakMessage *msg);
    virtual void datagramReceivedGeneric(
                      const std::string *endpointId, 
                      const uint8_t *data, size_t length);

    // StreamingMessageListener
    virtual void streamingMessageReceived(std::string streamingEndpoint, const CoTMessage *message);

    // Interface status listener    
    virtual void interfaceUp(NetInterface *iface);
    virtual void interfaceDown(NetInterface *iface);
    void removeStream(const std::string &epString);

    // Current active protocol version (for broadcasts)
    int getProtoVersion();

    // Sets preference of endpoint type for outgoing sends
    // when a contact is known by both a stream endpoint and a mesh endpoint
    // Default is to prefer mesh (non-stream)
    void setPreferStreamEndpoint(bool preferStream);

    // Gets the current endpoint IP/host of any locally known (non-streaming)
    // and active endpoint for the given contact. Return is *just* the IP or
    // hostname as a string.  Returns empty string if contact's local
    // endpoint is inactive
    std::string getActiveEndpointHost(const ContactUID *contact);

    // Indicates if the contact has a known streaming endpoint
    bool hasStreamingEndpoint(const ContactUID *contact);
    
    // Indicates if the contact has any known endpoint
    bool hasContact(const ContactUID *contact);

    // Gets the streaming endpoint description for the given contact
    // Throws if contact does not have an active streaming endpoint
    std::string getStreamEndpointIdentifier(const ContactUID *contact, bool ifActive = false) COMMO_THROW (std::invalid_argument);

    // UIDs, cotMessage only need be valid for duration of call. cotMessage must be null terminated.
    // Returns OK or CONTACT_GONE
    // if CONTACT_GONE, the destinations list is updated to indicate
    // which and how many Contacts are considered 'gone'.
    CommoResult sendCoT(std::vector<const ContactUID *> *destinations, const CoTMessage *cotMessage, CoTSendMethod sendMethod = SEND_ANY);
    CommoResult sendCoT(ContactList *destinations, const CoTMessage *cotMessage, CoTSendMethod sendMethod = SEND_ANY);

    // Configure a "known endpoint" contact. This is 
    // a non-discoverable contact for whom we already know an endpoint.
    // The IP address is specified in "dotted decimal" string form; hostnames
    // are not allowed, only IP addresses.
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
                        

    void addContactPresenceListener(ContactPresenceListener *listener) COMMO_THROW (std::invalid_argument);
    void removeContactPresenceListener(ContactPresenceListener *listener) COMMO_THROW (std::invalid_argument);

    const ContactList *getAllContacts();
    static void freeContactList(const ContactList *list);

protected:
    virtual void threadStopSignal(size_t threadNum);
    virtual void threadEntry(size_t threadNum);


private:
    enum { EVENT_THREADID, PROTOV_THREADID };

    CommoLogger *logger;
    DatagramSocketManagement *dgMgr;
    TcpSocketManagement *tcpMgmt;
    StreamingSocketManagement *streamMgmt;
    typedef std::map<const ContactUID *, ContactState *, ContactUIDComp> ContactMap;
    ContactMap contacts;
    thread::RWMutex contactMapMutex;
    
    thread::Mutex protoVMutex;
    thread::CondVar protoVMonitor; // Fire when dirty set = true
    // lock protoVMutex for next 2
    int protoVersion;
    bool protoVDirty;
    
    bool preferStreaming;

    std::set<ContactPresenceListener *> listeners;
    thread::Mutex listenerMutex;
    
    // Newest at front, oldest at back
    std::deque<std::pair<InternalContactUID *, bool> > eventQueue;
    thread::Mutex eventQueueMutex;
    thread::CondVar eventQueueMonitor;


    void fireContactPresenceChange(const ContactUID *c, bool present);
    void queueContactPresenceChange(const ContactUID *c, bool present);
    void queueThread();
    
    void protoVThreadProcess();
    void protoVThreadFireVersionChange(int newVersion);

    // Assumes holding state lock
    int getSendProtoVersion(ContactState *contact);

    CoTEndpoint *getCurrentEndpoint(ContactState *contact, CoTSendMethod sendMethod = SEND_ANY);
    bool updateStateDatagramEndpoint(ContactState *contact,
            const std::string &endpoint,
            int port);
    bool updateStateTcpEndpoint(ContactState *contact,
            const std::string &endpoint, int port);
    bool updateStateStreamEndpoint(ContactState *contact,
            const std::string &endpoint);

    // ILLEGAL_ARGUMENT if knownEP does not match
    // existing contact state, otherwise SUCCESS
    // If protoInfOnly, then updates only protocol info;
    //    impl must ignore all args EXCEPT protoInf, knownEP, type, and uid
    // protoInf may be NULL
    CommoResult processContactUpdate(
            bool protoInfOnly,
            unsigned int msgVer,
            const ContactUID *uid,
            const TakProtoInfo *protoInf,
            bool knownEP,
            CoTEndpoint::InterfaceType type,
            const std::string &endpoint,
            const std::string &callsign,
            int port);
    // Core of processContactUpdate() once state to update is known
    bool processContactStateUpdate(ContactState *state,
            bool *protoNeedsRefresh,       // output: true if proto need be
                                           //         flagged dirty
                                           //     only possible true if
                                           //     return is true
            bool protoInfOnly,
            unsigned int msgVer,
            const TakProtoInfo *protoInf,
            bool knownEP,
            CoTEndpoint::InterfaceType type,
            const std::string &endpoint, 
            const std::string &callsign, int port);
    void msgRxImpl(const std::string &endpoint, 
                   const ContactUID *uid,
                   const TakProtoInfo *protoInf,
                   const CoTMessage *msg,
                   unsigned int msgVer,
                   CoTEndpoint::InterfaceType type);

};



}
}
}


#endif
