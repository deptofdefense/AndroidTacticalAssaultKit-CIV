#ifndef IMPL_TCPSOCKETMANAGEMENT_H_
#define IMPL_TCPSOCKETMANAGEMENT_H_

#include "netsocket.h"
#include "threadedhandler.h"
#include "cotmessage.h"
#include "commoresult.h"
#include "commologger.h"
#include "commotime.h"
#include "resolverqueue.h"
#include "cryptoutil.h"
#include <set>
#include <map>
#include <deque>
#include <RWMutex.h>

namespace atakmap {
namespace commoncommo {
namespace impl
{


class TcpMessageListener
{
public:
    // Parameters must be copied during event servicing if used after return
    virtual void tcpMessageReceived(const NetAddress *sender, const std::string *endpointId, const CoTMessage *message) = 0;

protected:
    TcpMessageListener() {};
    virtual ~TcpMessageListener() {};

private:
    COMMO_DISALLOW_COPY(TcpMessageListener);
};



class TcpSocketManagement : public ThreadedHandler, public ResolverListener
{
public:
    TcpSocketManagement(CommoLogger *logger, ContactUID *ourUid);
    virtual ~TcpSocketManagement();



    TcpInboundNetInterface *addInboundInterface(int port);
    CommoResult removeInboundInterface(TcpInboundNetInterface *iface);

    void setConnTimeout(float seconds);
    void setCryptoKeys(const uint8_t *authKey, const uint8_t *cryptoKey);

    // Uses protoVersion = 0 if supplied version not supported
    void sendMessage(const std::string &host, int port,
                     const CoTMessage *msg, int protoVersion) 
                     COMMO_THROW (std::invalid_argument);

    void addMessageReceiver(TcpMessageListener *receiver);
    void removeMessageReceiver(TcpMessageListener *receiver);
    CommoResult addCoTSendFailureListener(CoTSendFailureListener *listener);
    CommoResult removeCoTSendFailureListener(CoTSendFailureListener *listener);
    void addInterfaceStatusListener(InterfaceStatusListener *listener);
    void removeInterfaceStatusListener(InterfaceStatusListener *listener);
    
    // ResolverListener
    virtual bool resolutionComplete(ResolverQueue::Request *identifier,
                                    const std::string &hostAddr,
                                    NetAddress *result);

protected:
    // ThreadedHandler impl
    virtual void threadEntry(size_t threadNum);
    virtual void threadStopSignal(size_t threadNum);


private:
    enum { IO_THREADID, RX_QUEUE_THREADID, TX_ERRQUEUE_THREADID };

    struct InboundContext;
    struct ClientContext;
    struct IBPortComp
    {
        bool operator()(const InboundContext * const &a, const InboundContext * const &b) const
        {
            return a->port < b->port;
        }
    };
    typedef std::set<InboundContext *, IBPortComp> InboundCtxSet;
    typedef std::set<ClientContext *> ClientCtxSet;


    struct InboundContext : public TcpInboundNetInterface
    {
        TcpSocket *socket;
        CommoTime retryTime;

        const uint16_t localPort;
        NetAddress *localAddr; // includes port
        
        std::string endpoint;
        
        bool upEventFired;

        InboundContext(uint16_t localPort);
        ~InboundContext();
        
        void initSocket() COMMO_THROW (SocketException);

    private:
        COMMO_DISALLOW_COPY(InboundContext);
    };
    
    struct ClientContext
    {
        NetAddress *clientAddr;
        std::string endpoint;

        TcpSocket *socket;
        uint8_t *data;
        size_t len;
        size_t bufLen;
        
        ClientContext(TcpSocket *socket, NetAddress *clientAddr,
                      const std::string &endpoint);
        ~ClientContext();
        
        void growCapacity(size_t n);
        void clearBuffers();
    private:
        COMMO_DISALLOW_COPY(ClientContext);
    };


    struct TxContext
    {
        // Where's it going? Includes port
        // NULL if out for name resolution
        NetAddress *destination;
        std::string host;
        uint16_t destPort;

        // The actual data to send
        uint8_t *data;
        uint8_t *origData;
        size_t dataLen;

        
        // Socket
        TcpSocket *socket;
        
        bool isConnecting;
        // Time at which in-progress connect should time out
        CommoTime timeout;
        
        TxContext(const std::string &host, uint16_t port,
                  MeshNetCrypto *crypto, const CoTMessage *msg, 
                  ContactUID *ourUid,
                  int protoVersion) COMMO_THROW (std::invalid_argument);
        // close+delete socket if !NULL, delete destination
        // delete cotmsg
        ~TxContext();

    private:
        COMMO_DISALLOW_COPY(TxContext);
    };


    struct RxQueueItem
    {
        NetAddress *sender;
        std::string endpoint;
        uint8_t *data;
        size_t dataLen;

        RxQueueItem(NetAddress *sender, const std::string &endpoint,
                    uint8_t *data, size_t dataLen);
        // Copy is ok
        ~RxQueueItem();
        void implode();
    };
    
    struct TxErrQueueItem
    {
        const std::string destinationHost;
        const int destinationPort;
        const std::string errMsg;
        TxErrQueueItem(const std::string &host, 
                       int port,
                       const std::string &errMsg);
        // Copy ok
        ~TxErrQueueItem();
    };


    typedef std::set<TxContext *> TxCtxSet;
    typedef std::map<ResolverQueue::Request *, TxContext *> ResolverReqMap;


    CommoLogger *logger;
    ContactUID *ourUid;
    ResolverQueue *resolver;
    float connTimeoutSec;
    
    MeshNetCrypto *rxCrypto;   // lock on rxQueueMutex 
    MeshNetCrypto *txCrypto;   // lock on txMutex

    // Next 2 protected by globalIOMutex
    InboundCtxSet inboundContexts;
    bool inboundNeedsRebuild;

    ClientCtxSet clientContexts;  // access only on io thread

    TxCtxSet txContexts;
    bool txNeedsRebuild;
    PGSC::Thread::Mutex txMutex;
    
    ResolverReqMap resolverContexts;
    PGSC::Thread::Mutex resolverContextsMutex;


    // RX Queue - new items on front
    std::deque<RxQueueItem> rxQueue;
    PGSC::Thread::Mutex rxQueueMutex;
    PGSC::Thread::CondVar rxQueueMonitor;

    // TX Error Queue - new items on front
    std::deque<TxErrQueueItem> txErrQueue;
    PGSC::Thread::Mutex txErrQueueMutex;
    PGSC::Thread::CondVar txErrQueueMonitor;

    // Listeners
    std::set<TcpMessageListener *> listeners;
    PGSC::Thread::Mutex listenerMutex;

    // Error Listeners
    std::set<CoTSendFailureListener *> txErrListeners;
    PGSC::Thread::Mutex txErrListenerMutex;

    // Interface status listeners
    std::set<InterfaceStatusListener *> ifaceListeners;
    PGSC::Thread::Mutex ifaceListenerMutex;


    // Protects all IO sockets across all contexts (aka the big select() mutex)
    // While this is a RW Mutex, the internal use of it is rather
    // non-traditional.  There is only ever one reader; this reader
    // holds the lock quite often while doing some long-running tasks, but
    // frequently and briefly relinquishes the lock to allow a writer to
    // interject and perform an exclusive operation.
    // Thus it is used basically like a regular Mutex but uses the reader
    // writer paradigm to allow prioritization to writers.  With a normal
    // Mutex, lock starvation of the "writing" calls was seen with the "read"
    // portion never giving up the mutex effectively (on some platforms,
    // notably Linux)
    PGSC::Thread::RWMutex globalIOMutex;

    NetSelector selector;



    COMMO_DISALLOW_COPY(TcpSocketManagement);
    void ioThreadProcess();
    void recvQueueThreadProcess();
    void txErrQueueThreadProcess();

    void queueTxErr(TxContext *ctx, const std::string &reason, 
                                    bool removeFromCtxSet);
    void killTxCtx(TxContext *ctx, bool removeFromCtxSet);
    void ioThreadResetInboundCtx(InboundContext *ctx, bool flagReset);
    void ioThreadQueueRx(ClientContext *ctx);

    void fireIfaceStatus(TcpInboundNetInterface *iface, bool up);

};

}
}
}


#endif
