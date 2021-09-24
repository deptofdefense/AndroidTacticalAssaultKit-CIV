#ifndef IMPL_DATAGRAMSOCKETMANAGEMENT_H_
#define IMPL_DATAGRAMSOCKETMANAGEMENT_H_

#include "netsocket.h"
#include "threadedhandler.h"
#include "hwifscanner.h"
#include "cotmessageio.h"
#include "cotmessage.h"
#include "takmessage.h"
#include "commoresult.h"
#include "commotime.h"
#include "cryptoutil.h"
#include "commothread.h"
#include <set>
#include <map>
#include <deque>

namespace atakmap {
namespace commoncommo {
namespace impl
{


class DatagramListener
{
public:
    // Parameters must be copied during event servicing if used after return
    virtual void datagramReceived(const std::string *endpointId, const NetAddress *sender, const TakMessage *message) = 0;
    virtual void datagramReceivedGeneric(const std::string *endpointId, const uint8_t *data, size_t length) = 0;

protected:
    DatagramListener() {};
    virtual ~DatagramListener() {};

private:
    COMMO_DISALLOW_COPY(DatagramListener);
};



class DatagramSocketManagement : public HWIFScannerListener,
                                 public ThreadedHandler
{
public:
    DatagramSocketManagement(CommoLogger *logger, ContactUID *ourUid, HWIFScanner *scanner);
    virtual ~DatagramSocketManagement();




    // HWIFScannerListener implementation
    virtual void interfaceUp(const HwAddress *addr, const NetAddress *netAddr);
    virtual void interfaceDown(const HwAddress *addr);

    // TakProtoLevelListener implementation
    virtual void protoLevelChange(int newVersion);

    PhysicalNetInterface *addBroadcastInterface(const HwAddress *hwAddress, const CoTMessageType *types, size_t nTypes, const char *addr, int destPort);
    CommoResult removeBroadcastInterface(PhysicalNetInterface *iface);

    PhysicalNetInterface *addInboundInterface(const HwAddress *hwAddress, int port, const char **mcastAddrs, size_t nMcastAddrs, bool asGeneric);
    CommoResult removeInboundInterface(PhysicalNetInterface *iface);


    // Send using specified version to specified destination.
    // If version is unsupported, uses legacy xml (version 0)
    void sendDatagram(const NetAddress *dest, const CoTMessage *msg,
                      int protoVersion) COMMO_THROW (std::invalid_argument);
    void sendMulticast(const CoTMessage *msg) COMMO_THROW (std::invalid_argument);


    void addDatagramReceiver(DatagramListener *receiver);
    void removeDatagramReceiver(DatagramListener *receiver);
    void addInterfaceStatusListener(InterfaceStatusListener *listener);
    void removeInterfaceStatusListener(InterfaceStatusListener *listener);

    void setCryptoKeys(const uint8_t *authKey, const uint8_t *cryptoKey);
    
    void setAdvertiseEndpointAsUdp(bool en);
    void setMagtabWorkaroundEnabled(bool en);
    void setEnableAddressReuse(bool en);
    void setMulticastLoopbackEnabled(bool en);
    void setTTL(int ttl);
    // 0 for "never timeout and issue rejoins"
    void setRxTimeoutSecs(int seconds);


protected:
    // ThreadedHandler impl
    virtual void threadEntry(size_t threadNum);
    virtual void threadStopSignal(size_t threadNum);


private:
    enum { TX_THREADID, RX_THREADID, RX_QUEUE_THREADID };

    struct RecvUnicastContext;
    struct RUPortComp
    {
        bool operator()(const RecvUnicastContext * const &a, const RecvUnicastContext * const &b) const
        {
            return a->localPort < b->localPort;
        }
    };
    typedef std::set<RecvUnicastContext *, RUPortComp> RxCtxSet;


    struct RecvUnicastContext : public PhysicalNetInterface
    {
        const int localPort;
        std::map<std::string, NetAddress *> mcastAddrs;

        RecvUnicastContext(int localPort);
        RecvUnicastContext(const HwAddress *hwAddr, int localPort, const char **mcastAddrs, size_t nMcastAddrs) COMMO_THROW (std::invalid_argument);
        ~RecvUnicastContext();

    private:
        COMMO_DISALLOW_COPY(RecvUnicastContext);
    };
    struct BroadcastContext : public PhysicalNetInterface
    {
        NetAddress *dest;
        std::set<CoTMessageType> types;
        int destPort;

        // (uses outboundSocket in main context)
        BroadcastContext(const HwAddress *hwAddr, const CoTMessageType *types, size_t nTypes, const char *addr, int destPort) COMMO_THROW (std::invalid_argument);
        ~BroadcastContext();
    private:
        COMMO_DISALLOW_COPY(BroadcastContext);
    };

    struct InterfaceContext
    {
        // Unique by local port
        RxCtxSet recvContexts;

        // Unique by identity only
        std::set<BroadcastContext *> broadcastContexts;

        NetAddress *netAddr;
        thread::Mutex stateMutex;

        UdpSocket *outboundSocket;

        InterfaceContext();
        ~InterfaceContext();
    private:
        COMMO_DISALLOW_COPY(InterfaceContext);
    };

    typedef std::set<const HwAddress *, HwComp> HwAddrSet;
    struct SharedSocketContext
    {
        // If anything in joined list, the socket will exist
        HwAddrSet joinedAddrs;
        HwAddrSet pendingAddrs;
        // May be NULL if all interfaces are still pending or
        // some socket error occurred and we haven't rebuilt yet.
        UdpSocket *socket;
        CommoTime lastRxTime;
        std::string endpointStr;
        const int port;
        const bool generic;

        SharedSocketContext(int port, bool generic);
        ~SharedSocketContext();
    private:
        COMMO_DISALLOW_COPY(SharedSocketContext);
    };

    struct RxQueueItem
    {
        NetAddress *sender;
        std::string endpointId;
        bool generic;
        uint8_t *data;
        size_t dataLen;

        RxQueueItem(NetAddress *sender, const std::string &endpointId,
                    bool generic, uint8_t *data, size_t dataLen);
        // Copy is ok
        ~RxQueueItem();
        void implode();
    };

    struct TxQueueItem
    {
        // if NULL, it's a broadcast
        // if non-null, it's unicast or directed multicast
        NetAddress *destination;
        CoTMessage *cotmsg;
        // >= 0 forces use of that version, < 0 means use
        // current broadcasting version. Only < 0 if
        // a bcast message
        int protoVersion;

        TxQueueItem();
        TxQueueItem(const NetAddress *dest, const CoTMessage *msg,
                    int protoVersion) COMMO_THROW (std::invalid_argument);
        TxQueueItem(const CoTMessage *msg) COMMO_THROW (std::invalid_argument);
        // Copy is ok
        ~TxQueueItem();
        void implode();
    };

    CommoLogger *logger;
    ContactUID *ourUid;
    HWIFScanner *scanner;

    typedef std::map<const HwAddress *, InterfaceContext *, HwComp> InterfaceMap;
    typedef std::map<int, SharedSocketContext *> SharedSocketMap;
    // Unique by identity only
    std::set<BroadcastContext *> unicastBroadcastContexts;
    InterfaceMap interfaceContexts;
    SharedSocketMap socketContexts;
    thread::RWMutex interfaceMutex;

    UdpSocket *txSocket;

    // Protects all rx sockets across all interfaces (aka the big select() mutex)
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
    thread::RWMutex globalRxMutex;
    NetSelector rxSelector;
    bool rxNeedsRebuild;
    // Protects all tx sockets across all interfaces
    thread::Mutex globalTxMutex;

    // RX Queue - new items on front
    std::deque<RxQueueItem> rxQueue;
    thread::Mutex rxQueueMutex;
    thread::CondVar rxQueueMonitor;

    // TX Queue - new items on front
    std::deque<TxQueueItem> txQueue;
    thread::Mutex txQueueMutex;
    thread::CondVar txQueueMonitor;

    CommoTime nextBroadcastTime;

    // Listeners
    std::set<DatagramListener *> listeners;
    thread::Mutex listenerMutex;

    // Interface status listeners
    std::set<InterfaceStatusListener *> ifaceListeners;
    thread::Mutex ifaceListenerMutex;

    MeshNetCrypto *rxCrypto;  // lock on rxQueueMutex
    MeshNetCrypto *txCrypto;  // lock on globalTxMutex
    bool reuseAddress;
    bool mcastLoop;
    int ttl;
    int noDataTimeoutSecs;
    bool epAsUdp;
    bool magtabEnabled;
    int protoVersion;

    COMMO_DISALLOW_COPY(DatagramSocketManagement);
    void recvThreadProcess();
    void recvQueueThreadProcess();
    void outQueueThreadProcess();
    bool getOrAllocIfaceByAddr(const HwAddress **hwAddr, InterfaceContext **ctx);
    bool getOrAllocSocketContext(int port, bool forGeneric,
                                 SharedSocketContext **ctx);
    bool checkTXSocket(InterfaceContext *ctx, std::string *netAddrStr);
    void killRXSocket(SharedSocketContext *ctx);
    void fireIfaceStatus(InterfaceContext *ctx, bool up);
    void fireIfaceStatusImpl(PhysicalNetInterface *iface, bool up);


    // NOTES:
    /*
     * RX thread:
     * +globalRx
     * +interfaceMutex (R)
     * for each iface....
     *   +stateMutex
     *   if iface is up....
     *     for each rxctx....
     *         add to list to process
     *   -stateMutex
     * -interfaceMutex
     *  for each in list to process....
     *     if socket not open, try to open - discard if fails
     *     build into select list
     *  select()
     *  for each exception fd
     *     report & close()?
     *  for each readable fd
     *     read(): if error, close socket
     *          if ok, map fd back to context, add to result list
     *  +rxqueue
     *     dump results into rxq
     *     signal rxqcvar
     *  -rxqueue
     *  -globalRx
     *
     * Queue process thread:
     * +rxqueue
     * get one event or wait on cv
     * +listener list
     * for each listener, send event
     * free data, sender from event
     * -listener list
     * -rxqueue
     *
     * Interface state change:
     * +interfaceMutex(R)
     * find interface
     *   set state
     * -interfaceMutex
     *
     * Add RX interface:
     * +interfaceMutex(W)
     * add interface as empty and down
     * -interfaceMutex(W)
     *
     * Remove interface:
     * +globalRx
     * +globalTx
     * +interfaceMutex(W)
     *   find interface
     *   if (rxunicast) {
     *     find ctx
     *     +rxqueue
     *       purge this ctx out
     *     -rxqueue
     *     close socket if not invalid
     *     kill the rxctx and remove from list
     *   }
     *   if (bcast) {
     *   remove ctx
     *   }
     *   if was last rx and tx.... {
     *     close TX socket if valid
     *     remove interface entirely
     *     +txqueue
     *         purge ifctx from unicasts in queue
     *     -txqueue
     *   }
     * -interfaceMutex(W)
     * -globalTx
     * -globalRx
     *
     *
     * Tx Call:
     * Copy data
     * +interfaceMutex(R)
     *   if (unicast)
     *     fine matching ifctx by hwaddr
     *     copy dest
     *   +txqueue
     *     push on ifctx or bcast info + data, len
     *   -txqueue
     * -interfaceMutex(R)
     *
     * Tx Process:
     * +txQueue wait on cv if empty DO NOT POP ITEMS
     * -txQueue
     * +globalTx
     *   +interfaceMutex(R)
     *     +txqueue
     *         pop item; if empty, next iteration
     *     -txqueue
     *     if (bcast item)
     *         build list of ifctx's + mcast addrs
     *     foreach ifctx
     *         if !socket
     *           if ctx state is up
     *              create socket
     *           if (!socket)
     *              dump from list
     *   -interfaceMutex(R)
     *   send() to all
     * -globalTx
     *
     */

};

}
}
}


#endif /* IMPL_DATAGRAMSOCKETMANAGEMENT_H_ */
