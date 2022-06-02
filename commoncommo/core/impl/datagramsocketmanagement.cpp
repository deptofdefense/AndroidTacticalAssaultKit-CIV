#include "commologger.h"
#include "datagramsocketmanagement.h"
#include "internalutils.h"
#include "commothread.h"
#include <string.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

namespace {
    // Controls how long we let an RX socket persist without seeing data come
    // in.
    const int DEFAULT_RX_NO_DATA_REBUILD_TIME = 30;
    const InternalHwAddress UNICAST_HW_ADDR((uint8_t *)"", 0);
    
    const char *THREAD_NAMES[] = {
        "cmodg.tx", 
        "cmodg.rx",
        "cmodg.rxq"
    };
    
    struct DestInfo
    {
        std::string srcIpString;
        EndpointType srcEpType;
        std::vector<NetAddress *> dests;
        
        DestInfo(EndpointType epType) : srcIpString(), 
                                        srcEpType(epType), dests() {
        }
        DestInfo() : srcIpString(), srcEpType(ENDPOINT_TCP), dests() {
        }
        void clear(EndpointType epType) {
            srcIpString = "";
            srcEpType = epType;
            dests.clear();
        }
    };

}


DatagramSocketManagement::DatagramSocketManagement(CommoLogger *logger,
        ContactUID *ourUid,
        HWIFScanner *scanner) :
        HWIFScannerListener(), ThreadedHandler(3, THREAD_NAMES),
        logger(logger),
        ourUid(ourUid),
        scanner(scanner),
        unicastBroadcastContexts(),
        interfaceContexts(),
        socketContexts(),
        interfaceMutex(thread::RWMutex::Policy_Fair),
        txSocket(NULL),
        globalRxMutex(thread::RWMutex::Policy_Fair),
        rxSelector(),
        rxNeedsRebuild(false),
        globalTxMutex(),
        rxQueue(),
        rxQueueMutex(), rxQueueMonitor(),
        txQueue(),
        txQueueMutex(), txQueueMonitor(),
        nextBroadcastTime(CommoTime::ZERO_TIME),
        listeners(),
        listenerMutex(),
        ifaceListeners(),
        ifaceListenerMutex(),
        rxCrypto(NULL),
        txCrypto(NULL),
        reuseAddress(false),
        mcastLoop(false),
        ttl(1),
        noDataTimeoutSecs(DEFAULT_RX_NO_DATA_REBUILD_TIME),
        epAsUdp(false),
        magtabEnabled(false),
        protoVersion(TakProtoInfo::SELF_MAX)
{
    startThreads();
}

DatagramSocketManagement::~DatagramSocketManagement()
{
    stopThreads();

    // With the threads all joined, we can remove everything safely
    // Clean the queues first
    while (!rxQueue.empty()) {
        RxQueueItem rxi = rxQueue.back();
        rxQueue.pop_back();
        rxi.implode();
    }
    while (!txQueue.empty()) {
        TxQueueItem txi = txQueue.back();
        txQueue.pop_back();
        txi.implode();
    }
    InterfaceMap::iterator iter;
    for (iter = interfaceContexts.begin(); iter != interfaceContexts.end(); ++iter) {
        delete iter->second;
        delete (InternalHwAddress *)iter->first;
    }
    SharedSocketMap::iterator ssmiter;
    for (ssmiter = socketContexts.begin(); ssmiter != socketContexts.end(); ++ssmiter)
        delete ssmiter->second;

    if (rxCrypto)
        delete rxCrypto;
    if (txCrypto)
        delete txCrypto;
}

void DatagramSocketManagement::setEnableAddressReuse(bool en)
{
    thread::WriteLock rxLock(globalRxMutex);
    thread::WriteLock interfaceLock(interfaceMutex);

    if (en == reuseAddress)
        return;
    reuseAddress = en;

    InternalUtils::logprintf(logger, 
                             CommoLogger::LEVEL_INFO,
                             "Address reuse option changed to %s, all rx sockets will be rebuilt",
                             en ? "enabled" : "disabled");

    SharedSocketMap::iterator iter;
    for (iter = socketContexts.begin(); iter != socketContexts.end(); ++iter) {
        killRXSocket(iter->second);
    }

    rxNeedsRebuild = true;
}

void DatagramSocketManagement::setMulticastLoopbackEnabled(bool en)
{
    thread::Lock txLock(globalTxMutex);
    
    thread::WriteLock interfaceLock(interfaceMutex);

    if (en == mcastLoop)
        return;
    mcastLoop = en;

    InternalUtils::logprintf(logger, 
                             CommoLogger::LEVEL_INFO,
                             "Multicast looping option changed to %s, all tx sockets will be rebuilt",
                             en ? "enabled" : "disabled");

    InterfaceMap::iterator ifIter;
    for (ifIter = interfaceContexts.begin(); ifIter != interfaceContexts.end(); ++ifIter) {
        InterfaceContext *ifCtx = ifIter->second;
        if (ifCtx->outboundSocket) {
            delete ifCtx->outboundSocket;
            ifCtx->outboundSocket = NULL;
        }
    }
    if (txSocket) {
        delete txSocket;
        txSocket = NULL;
    }
}


void DatagramSocketManagement::setRxTimeoutSecs(int seconds)
{
    if (seconds < 0)
        seconds = DEFAULT_RX_NO_DATA_REBUILD_TIME;
    {
        thread::WriteLock ifLock(interfaceMutex);
        noDataTimeoutSecs = seconds;
    }
}


void DatagramSocketManagement::setTTL(int ttl)
{
    thread::Lock txLock(txQueueMutex);
    this->ttl = ttl;
}


void DatagramSocketManagement::protoLevelChange(int newVersion)
{
    thread::Lock txLock(txQueueMutex);
    protoVersion = newVersion;
    nextBroadcastTime = CommoTime::ZERO_TIME;
    txQueueMonitor.broadcast(txLock);
}


void DatagramSocketManagement::setAdvertiseEndpointAsUdp(bool en)
{
    epAsUdp = en;
}


void DatagramSocketManagement::setCryptoKeys(const uint8_t *authKey,
                                             const uint8_t *cryptoKey)
{
    {
        thread::Lock lock(rxQueueMutex);
        
        if (rxCrypto) {
            delete rxCrypto;
            rxCrypto = NULL;
        }
        if (authKey && cryptoKey)
            rxCrypto = new MeshNetCrypto(logger, cryptoKey, authKey);
    }
    {
        thread::Lock txLock(globalTxMutex);
        
        if (txCrypto) {
            delete txCrypto;
            txCrypto = NULL;
        }
        if (authKey && cryptoKey)
            txCrypto = new MeshNetCrypto(logger, cryptoKey, authKey);
    }
}


void DatagramSocketManagement::setMagtabWorkaroundEnabled(bool en)
{
    magtabEnabled = en;
}


void DatagramSocketManagement::threadStopSignal(size_t threadNum)
{
    switch (threadNum) {
    case TX_THREADID:
        {
            thread::Lock lock(txQueueMutex);
            txQueueMonitor.broadcast(lock);
            break;
        }
    case RX_THREADID:
        break;
    case RX_QUEUE_THREADID:
        {
            thread::Lock lock(rxQueueMutex);
            rxQueueMonitor.broadcast(lock);
            break;
        }
    }
}


void DatagramSocketManagement::interfaceUp(
        const HwAddress* addr, const NetAddress* netAddr)
{
    thread::ReadLock lock(interfaceMutex);
    try {
        InterfaceContext *ctx = interfaceContexts.at(addr);
        if (ctx->netAddr) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Interface up for already up interface? Ignoring");
            return;
        } else {
            thread::Lock slock(ctx->stateMutex);
            ctx->netAddr = NetAddress::duplicateAddress(netAddr);
            fireIfaceStatus(ctx, true);
        }
        // Flag for rebuild on receive thread
        rxNeedsRebuild = true;
    } catch (std::out_of_range &) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Interface up for unknown interface?");
    }
}

void DatagramSocketManagement::interfaceDown(
        const HwAddress* addr)
{
    thread::ReadLock lock(interfaceMutex);
    try {
        InterfaceContext *ctx = interfaceContexts.at(addr);
        if (ctx->netAddr) {
            thread::Lock slock(ctx->stateMutex);
            delete ctx->netAddr;
            ctx->netAddr = NULL;
            fireIfaceStatus(ctx, false);
        } else {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Interface down for already down interface? Ignoring");
        }
    } catch (std::out_of_range &) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Interface down for unknown interface?");
    }
}



bool DatagramSocketManagement::getOrAllocIfaceByAddr(const HwAddress **hwAddr, InterfaceContext **ctx)
{
    InterfaceMap::iterator iter = interfaceContexts.find(*hwAddr);
    bool isNewCtx;
    if (iter == interfaceContexts.end()) {
        isNewCtx = true;
        *ctx = new InterfaceContext();
        *hwAddr = new InternalHwAddress(*hwAddr);
    } else {
        isNewCtx = false;
        *hwAddr = iter->first;
        *ctx = iter->second;
    }
    return isNewCtx;
}

bool DatagramSocketManagement::getOrAllocSocketContext(int port, bool forGeneric, SharedSocketContext **ctx)
{
    SharedSocketMap::iterator iter = socketContexts.find(port);
    bool isNewCtx;
    if (iter == socketContexts.end()) {
        *ctx = new SharedSocketContext(port, forGeneric);
        isNewCtx = true;
    } else {
        *ctx = iter->second;
        isNewCtx = false;
    }
    return isNewCtx;
}


PhysicalNetInterface* DatagramSocketManagement::addBroadcastInterface(
        const HwAddress* hwAddress, const CoTMessageType* types, size_t nTypes,
        const char* addr, int destPort)
{
    thread::WriteLock lock(interfaceMutex);
    // Do we have this interface yet?
    const HwAddress *hwAddr = hwAddress;
    InterfaceContext *ctx;
    std::set<BroadcastContext *> *bcastSet;
    bool isNewCtx;
    
    if (hwAddr) {
        isNewCtx = getOrAllocIfaceByAddr(&hwAddr, &ctx);
        bcastSet = &ctx->broadcastContexts;
    } else {
        ctx = NULL;
        isNewCtx = false;
        bcastSet = &unicastBroadcastContexts;
    }
    
    try {
        BroadcastContext *newContext = new BroadcastContext(hwAddr, types, nTypes, addr, destPort);
        bcastSet->insert(newContext);
        if (isNewCtx) {
            interfaceContexts.insert(InterfaceMap::value_type(hwAddr, ctx));
            scanner->addHWAddress(hwAddr);
        }
        return newContext;
    } catch (std::invalid_argument &ex) {
        const char *reason = ex.what();
        if (!reason)
            reason = "";
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Failed to create broadcast interface (%s)", reason);
        if (isNewCtx) {
            delete ctx;
            delete hwAddr;
        }
        return NULL;
    }
}

CommoResult DatagramSocketManagement::removeBroadcastInterface(
        PhysicalNetInterface* iface)
{
    thread::WriteLock rxLock(globalRxMutex);
    thread::Lock txLock(globalTxMutex);
    thread::WriteLock interfaceLock(interfaceMutex);
    
    BroadcastContext *bcastIface = (BroadcastContext *)iface;

    // See if unicast broadcast iface first
    if (unicastBroadcastContexts.erase(bcastIface) == 1) {
        delete bcastIface;
        return COMMO_SUCCESS;
    }

    // No? now check full suite of interfaces
    InterfaceMap::iterator imapIter = interfaceContexts.find(iface->addr);
    if (imapIter == interfaceContexts.end())
        return COMMO_ILLEGAL_ARGUMENT;


    InterfaceContext *ifCtx = imapIter->second;
    if (ifCtx->broadcastContexts.erase(bcastIface) != 1)
        return COMMO_ILLEGAL_ARGUMENT;

    delete bcastIface;

    // Now see if we need to shut down the whole iface because
    // this was the final sub-interface
    if (ifCtx->broadcastContexts.empty() && ifCtx->recvContexts.empty()) {
        // Tell hwscanner to stop looking at this
        // before we move on
        scanner->removeHWAddress(iface->addr);
        delete ifCtx;
        delete (InternalHwAddress *)imapIter->first;
        interfaceContexts.erase(imapIter);
    }

    return COMMO_SUCCESS;
}

PhysicalNetInterface* DatagramSocketManagement::addInboundInterface(
        const HwAddress* hwAddress, int port, const char** mcastAddrs,
        size_t nMcastAddrs, bool generic)
{
    thread::WriteLock lock(interfaceMutex);
    // Do we have this interface yet?
    const HwAddress *hwAddr = hwAddress;
    InterfaceContext *ctx;
    bool isNewCtx = getOrAllocIfaceByAddr(&hwAddr, &ctx);

    try {
        RecvUnicastContext *rxctx = new RecvUnicastContext(hwAddr, port, mcastAddrs, nMcastAddrs);

        // Try to insert into set - if another with same port is already
        // there, then insert will fail;  error out
        std::pair<RxCtxSet::iterator, bool> insRes = ctx->recvContexts.insert(rxctx);
        if (!insRes.second) {
            delete rxctx;
            throw std::invalid_argument("port already in use");
        }

        // See if there is a shared socket context for this port number
        SharedSocketContext *sockCtx;
        if (getOrAllocSocketContext(port, generic, &sockCtx))
            socketContexts.insert(SharedSocketMap::value_type(port, sockCtx));
        else if (sockCtx->generic != generic) {
            ctx->recvContexts.erase(insRes.first);
            delete rxctx;
            throw std::invalid_argument("Cannot mix generic and non-generic on same inbound port");
        }

        if (isNewCtx) {
            interfaceContexts.insert(InterfaceMap::value_type(hwAddr, ctx));
            scanner->addHWAddress(hwAddr);
        }

        // Add ourselves to the context's unjoined list
        sockCtx->pendingAddrs.insert(hwAddr);

        // Note that we need to rebuild the rx socket set since either
        // this could be a new socket entirely, or we will need to issue
        // join on this interface
        rxNeedsRebuild = true;

        return rxctx;
    } catch (std::invalid_argument &e) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING, "Unable to add inbound datagram interface: %s", e.what() ? e.what() : "unknown error");
        if (isNewCtx) {
            delete ctx;
            delete hwAddr;
        }
        return NULL;
    }
}

CommoResult DatagramSocketManagement::removeInboundInterface(
        PhysicalNetInterface* iface)
{
    thread::WriteLock rxLock(globalRxMutex);
    thread::Lock txLock(globalTxMutex);
    thread::WriteLock interfaceLock(interfaceMutex);

    InterfaceMap::iterator imapIter = interfaceContexts.find(iface->addr);
    if (imapIter == interfaceContexts.end())
        return COMMO_ILLEGAL_ARGUMENT;


    InterfaceContext *ifCtx = imapIter->second;

    RecvUnicastContext *rxctx = (RecvUnicastContext *)iface;
    if (ifCtx->recvContexts.erase(rxctx) != 1)
        return COMMO_ILLEGAL_ARGUMENT;

    // Remove ourselves from the shared list of sockets. If we are the last,
    // close it down and destroy the context.
    SharedSocketMap::iterator ssmIter = socketContexts.find(rxctx->localPort);
    HwAddrSet::iterator hwaddrIter = ssmIter->second->joinedAddrs.find(iface->addr);
    if (hwaddrIter != ssmIter->second->joinedAddrs.end()) {
        if (ifCtx->netAddr) {
            // Drop our mcast membership on this socket.
            std::map<std::string, NetAddress *>::iterator mciter;
            for (mciter = rxctx->mcastAddrs.begin(); mciter != rxctx->mcastAddrs.end(); ++mciter) {
                try {
                    ssmIter->second->socket->mcastLeave(logger, ifCtx->netAddr, mciter->second);
                } catch (SocketException &) {
                }
            }
        }
        ssmIter->second->joinedAddrs.erase(hwaddrIter);
    } else {
        ssmIter->second->pendingAddrs.erase(iface->addr);
    }
    if (ssmIter->second->joinedAddrs.empty()) {
        // Only (maybe) pending entries remain. Kill the socket since it is
        // of no use.
        delete ssmIter->second->socket;
        ssmIter->second->socket = NULL;

        // Note that we need to rebuild the rx socket set since we
        // killed the socket
        rxNeedsRebuild = true;

        if (ssmIter->second->pendingAddrs.empty()) {
            // Nothing left - kill the whole context.
            delete ssmIter->second;
            socketContexts.erase(ssmIter);
        }
    }

    delete rxctx;


    // Now see if we need to shut down the whole iface because
    // this was the final sub-interface
    if (ifCtx->broadcastContexts.empty() && ifCtx->recvContexts.empty()) {
        // Tell hwscanner to stop looking at this
        // before we move on
        scanner->removeHWAddress(iface->addr);
        delete ifCtx;
        delete (InternalHwAddress *)imapIter->first;
        interfaceContexts.erase(imapIter);
    }
    return COMMO_SUCCESS;
}

void DatagramSocketManagement::sendDatagram(
        const NetAddress *dest,
        const CoTMessage *msg,
        int protoVersion) COMMO_THROW (std::invalid_argument)
{
    thread::Lock qlock(txQueueMutex);
    txQueue.push_front(TxQueueItem(dest, msg, protoVersion));
    txQueueMonitor.broadcast(qlock);
}

void DatagramSocketManagement::sendMulticast(const CoTMessage *msg) COMMO_THROW (std::invalid_argument)
{
    thread::Lock qlock(txQueueMutex);
    txQueue.push_front(TxQueueItem(msg));
    txQueueMonitor.broadcast(qlock);
}

void DatagramSocketManagement::addDatagramReceiver(
        DatagramListener* receiver)
{
    thread::Lock lock(listenerMutex);
    listeners.insert(receiver);
}

void DatagramSocketManagement::removeDatagramReceiver(
        DatagramListener* receiver)
{
    thread::Lock lock(listenerMutex);
    listeners.erase(receiver);
}

void DatagramSocketManagement::addInterfaceStatusListener(
        InterfaceStatusListener *listener)
{
    thread::Lock lock(ifaceListenerMutex);
    ifaceListeners.insert(listener);
}

void DatagramSocketManagement::removeInterfaceStatusListener(
        InterfaceStatusListener *listener)
{
    thread::Lock lock(ifaceListenerMutex);
    ifaceListeners.erase(listener);
}

// Do not call direct - use fireIfaceStatus()
void DatagramSocketManagement::fireIfaceStatusImpl(
        PhysicalNetInterface *iface, bool up)
{
    std::set<InterfaceStatusListener *>::iterator iter;
    for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
        InterfaceStatusListener *listener = *iter;
        if (up)
            listener->interfaceUp(iface);
        else
            listener->interfaceDown(iface);
    }
}

// Assumes interface context map lock held
void DatagramSocketManagement::fireIfaceStatus(
        InterfaceContext *ctx, bool up)
{
    thread::Lock lock(ifaceListenerMutex);
    RxCtxSet::iterator rxIter;
    for (rxIter = ctx->recvContexts.begin(); rxIter != ctx->recvContexts.end(); ++rxIter) {
        RecvUnicastContext *rxCtx = *rxIter;
        fireIfaceStatusImpl(rxCtx, up);
    }

    std::set<BroadcastContext *>::iterator bcastIter;
    for (bcastIter = ctx->broadcastContexts.begin(); bcastIter != ctx->broadcastContexts.end(); ++bcastIter) {
        BroadcastContext *bcastCtx = *bcastIter;
        fireIfaceStatusImpl(bcastCtx, up);
    }

}

void DatagramSocketManagement::threadEntry(
        size_t threadNum)
{
    switch (threadNum) {
    case TX_THREADID:
        outQueueThreadProcess();
        break;
    case RX_QUEUE_THREADID:
        recvQueueThreadProcess();
        break;
    case RX_THREADID:
        recvThreadProcess();
        break;
    }
}





void DatagramSocketManagement::killRXSocket(SharedSocketContext *ctx)
{
    delete ctx->socket;
    ctx->socket = NULL;
    ctx->pendingAddrs.insert(ctx->joinedAddrs.begin(), ctx->joinedAddrs.end());
    ctx->joinedAddrs.clear();
}


void DatagramSocketManagement::recvThreadProcess()
{
    NetAddress *wildcardAddr = NetAddress::createWildcard(NA_TYPE_INET4);
    std::vector<SharedSocketContext *> rxCtxList;
    const size_t rxBufLen0 = maxUDPMessageSize;
    uint8_t rxBuf[rxBufLen0];
    int rebuildTimeoutSecs;

    while (!threadShouldStop(RX_THREADID)) {
        thread::ReadLock lock(globalRxMutex);

        {
            thread::ReadLock ifLock(interfaceMutex);
            
            rebuildTimeoutSecs = noDataTimeoutSecs;

            if (rxNeedsRebuild) {
                rxNeedsRebuild = false;

                // New interface, removed interface, or failed socket -
                // Check all sockets and recreate if needed.
                // Rebuild the select list.
                // (Re-)issue joins if needed.
                std::vector<Socket *> sockets;
                rxCtxList.clear();

                SharedSocketMap::iterator iter;
                for (iter = socketContexts.begin(); iter != socketContexts.end(); ++iter) {
                    // First check if the socket is there
                    int curPort = iter->first;
                    SharedSocketContext *socketContext = iter->second;
                    if (!socketContext->socket) {
                        NetAddress *bindAddr = wildcardAddr->deriveNewAddress(curPort);
                        try {
                            socketContext->socket = new UdpSocket(bindAddr, false, reuseAddress);
                            delete bindAddr;
                            socketContext->lastRxTime = CommoTime::now();
                        } catch (SocketException &) {
                            CommoLogger::NetworkDetail detail{ curPort };
                            InternalUtils::logprintf(logger,
                                    CommoLogger::LEVEL_ERROR,
                                    CommoLogger::TYPE_NETWORK,
                                    &detail,
                                    "Unable to create listening UDP socket on port %d", curPort);
                            delete bindAddr;
                            continue;
                        }
                    }

                    // Now check for joins we can fulfill
                    try {
                        HwAddrSet::iterator hwSetIter = socketContext->pendingAddrs.begin();
                        while (hwSetIter != socketContext->pendingAddrs.end()) {
                            HwAddrSet::iterator curPosIter = hwSetIter;
                            // Move to next now in case we end up erasing curPosIter
                            hwSetIter++;

                            InterfaceMap::iterator ifIter = interfaceContexts.find(*curPosIter);
                            InterfaceContext *ifCtx = ifIter->second;
                            thread::Lock slock(ifCtx->stateMutex);
                            if (ifCtx->netAddr) {
                                RecvUnicastContext findByPort(curPort);
                                RecvUnicastContext *rxUniCtx = *(ifCtx->recvContexts.find(&findByPort));
                                std::map<std::string, NetAddress *>::iterator mcastIter;
                                for (mcastIter = rxUniCtx->mcastAddrs.begin(); mcastIter != rxUniCtx->mcastAddrs.end(); ++mcastIter)
                                    socketContext->socket->mcastJoin(logger, ifIter->second->netAddr, mcastIter->second);
                                // Move from pending to joined
                                socketContext->joinedAddrs.insert(*curPosIter);
                                socketContext->pendingAddrs.erase(curPosIter);
                            } // else leave as pending
                        }
                    } catch (SocketException &) {
                        // Kill the socket and we'll try again as
                        // all the joins we try should've succeeded
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error joining mcast group - will rebuild socket");
                        killRXSocket(socketContext);
                        continue;
                    }

                    // Finally, add our socket into the list of sockets to
                    // select on. Also note the set of SharedSocketContext's
                    // containing active sockets
                    rxCtxList.push_back(socketContext);
                    sockets.push_back(socketContext->socket);
                }
                rxSelector.setSockets(&sockets, NULL);
            }
        }

        // The sockets we are selecting on are now ready
        // in the context list.
        bool killAllSockets = false;
        bool selectTimedOut = false;
        try {
            if (!rxSelector.doSelect(500))
                // Timeout
                selectTimedOut = true;

        } catch (SocketException &) {
            // odd. Force a rebuild
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "datagram rx select failed");
            killAllSockets = true;
        }

        // Now iterate the list and read the ready sockets
        std::vector<SharedSocketContext *>::iterator rxCtxIter;
        for (rxCtxIter = rxCtxList.begin(); rxCtxIter != rxCtxList.end(); ++rxCtxIter) {
            SharedSocketContext *rxCtx = *rxCtxIter;
            if (killAllSockets) {
                killRXSocket(rxCtx);
            } else {
                if (selectTimedOut || rxSelector.getLastReadState(rxCtx->socket) != NetSelector::READABLE) {
                    // See if it has been a while since we got data for 
                    // this socket.
                    // If so, kill it and rebuild, re-sending mcast joins
                    if (rebuildTimeoutSecs != 0 && 
                            CommoTime::now().minus(rxCtx->lastRxTime) > 
                                                (float)rebuildTimeoutSecs) {
                        InternalUtils::logprintf(logger, 
                                CommoLogger::LEVEL_VERBOSE,
                                "datagram rx socket for port %d has not "
                                "received data in a long time; rebuilding and "
                                "reissuing joins", rxCtx->port);
                        killRXSocket(rxCtx);
                        rxNeedsRebuild = true;
                    }
                    continue;
                }

               
                int count = 0;
                try {
                    do {
                        size_t len = rxBufLen0;
                        NetAddress *rxAddr;

                        // Read until error or would block
                        rxCtx->socket->recvfrom(&rxAddr, rxBuf, &len);
                        count++;

                        // Now push on to queue
                        {
                            thread::Lock qLock(rxQueueMutex);
                            rxQueue.push_front(RxQueueItem(rxAddr, 
                                                           rxCtx->endpointStr,
                                                           rxCtx->generic, 
                                                           rxBuf, len));
                            rxQueueMonitor.broadcast(qLock);
                        }
                    } while (true);
                    
                } catch (SocketWouldBlockException &) {
                    if (count)
                        // Got one or more - reset rx time
                        rxCtx->lastRxTime = CommoTime::now();
                    //else
                    //    InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING, "port %d got wouldblock?", rxCtx->port);

                } catch (SocketException &e) {
                    // Close the socket and re-open next go around
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING, "datagram rx socket read error for port %d; will retry if interface still valid %s", rxCtx->port, e.what());
                    killRXSocket(rxCtx);
                    rxNeedsRebuild = true;
                }

            }
        }
        if (killAllSockets)
            rxNeedsRebuild = true;
    }

    delete wildcardAddr;
}

void DatagramSocketManagement::recvQueueThreadProcess()
{
    while (!threadShouldStop(RX_QUEUE_THREADID)) {
        thread::Lock qLock(rxQueueMutex);
        if (rxQueue.empty()) {
            rxQueueMonitor.wait(qLock);
            continue;
        }

        RxQueueItem qItem = rxQueue.back();
        rxQueue.pop_back();
        if (qItem.generic) {
            thread::Lock listenerLock(listenerMutex);
            std::set<DatagramListener *>::iterator iter;
            for (iter = listeners.begin(); iter != listeners.end(); ++iter) {
                DatagramListener *l = *iter;
                l->datagramReceivedGeneric(&qItem.endpointId, qItem.data, qItem.dataLen);
            }
        } else {

            bool decrypted = false;
            uint8_t *data = qItem.data;
            size_t dataLen = qItem.dataLen;
            try {
                if (rxCrypto) {
                    decrypted = rxCrypto->decrypt(&data, &dataLen);
                    if (!decrypted)
                        throw std::invalid_argument("Decryption failed");
                }
                TakMessage msg(logger, data, dataLen, true, true);
                thread::Lock listenerLock(listenerMutex);
                std::set<DatagramListener *>::iterator iter;
                for (iter = listeners.begin(); iter != listeners.end(); ++iter) {
                    DatagramListener *l = *iter;
                    l->datagramReceived(&qItem.endpointId, qItem.sender, &msg);
                }
            } catch (std::invalid_argument &e) {
                // Drop this item
                CommoLogger::ParsingDetail detail{ data, dataLen, e.what(), qItem.endpointId.c_str() };
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, CommoLogger::TYPE_PARSING, &detail, "Invalid CoT Message received; dropping (%s)", e.what());
            }
            if (decrypted)
                delete[] data;
        }
        qItem.implode();
    }
}


bool DatagramSocketManagement::checkTXSocket(InterfaceContext *ctx, std::string *netAddrStr)
{
    UdpSocket **sockPtr;
    NetAddress *netAddr;
    if (ctx) {
        sockPtr = &ctx->outboundSocket;
        thread::Lock lock(ctx->stateMutex);
        if (!ctx->netAddr)
            return false;

        netAddr = NetAddress::duplicateAddress(ctx->netAddr);
        netAddr->getIPString(netAddrStr);
    } else {
        sockPtr = &txSocket;
        netAddr = NetAddress::createWildcard(NA_TYPE_INET4);
        *netAddrStr = "";
    }

    bool ret;
    if (*sockPtr) {
        ret = true;
    } else {
        try {
            *sockPtr = new UdpSocket(netAddr, true, false, mcastLoop);
            ret = true;
        } catch (SocketException &ex) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING, "Unable to build outbound datagram socket (address %s port %d); will retry later %s", netAddrStr->length() == 0 ? "*" : netAddrStr->c_str(), (unsigned int)netAddr->getPort(), ex.what());
            ret = false;
        }
    }
    delete netAddr;

    return ret;
}


void DatagramSocketManagement::outQueueThreadProcess()
{
    int localTTL;
    int localProtoVersion;
    while (!threadShouldStop(TX_THREADID)) {
        {
            thread::Lock qLock(txQueueMutex);
            CommoTime nowTime = CommoTime::now();

            int64_t wakeMillis = 0;
            if (nowTime < nextBroadcastTime) {
                // Our time has not yet come - wait until it will
                CommoTime t = nextBroadcastTime;
                t -= nowTime;
                unsigned int s = t.getSeconds();
                wakeMillis = s * 1000;
            }
            
            if (!wakeMillis) {
                txQueue.push_front(TxQueueItem());
            } else if (txQueue.empty()) {
                txQueueMonitor.wait(qLock, wakeMillis);
                continue;
            }
        }
        {
            thread::Lock txLock(globalTxMutex);
            std::map<InterfaceContext *, DestInfo> destPairs;
            TxQueueItem qitem;
            CoTMessageType cotType;
            {
                thread::ReadLock ifaceLock(interfaceMutex);
                {
                    thread::Lock qLock(txQueueMutex);
                    if (txQueue.empty())
                        continue;
                    qitem = txQueue.back();
                    txQueue.pop_back();
                    localTTL = ttl;
                    localProtoVersion = protoVersion;
                }

                cotType = qitem.cotmsg ? qitem.cotmsg->getType() :
                                         SITUATIONAL_AWARENESS;
                DestInfo curDestInfo(epAsUdp ? ENDPOINT_UDP : ENDPOINT_TCP);
                if (qitem.destination && !qitem.destination->isMulticast()) {
                    // Unicast
                    if (checkTXSocket(NULL, &curDestInfo.srcIpString)) {
                        curDestInfo.dests.push_back(qitem.destination);
                        destPairs[NULL] = curDestInfo;
                    }
                } else {
                    // Broadcast or directed multicast
                    NetAddress *fixedDest = qitem.destination;
                    InterfaceMap::iterator ifIter;
                    std::set<BroadcastContext *>::iterator bcastIter;
                    for (ifIter = interfaceContexts.begin(); ifIter != interfaceContexts.end(); ++ifIter) {
                        InterfaceContext *ifCtx = ifIter->second;
                        if (ifCtx->broadcastContexts.empty())
                            continue;

                        if (fixedDest) {
                            // Direct multicast - just add it to all
                            // interfaces for which we have at least one
                            // broadcast configured
                            curDestInfo.dests.push_back(fixedDest);
                            
                        } else {
                            // Broadcast. Send to those addresses with
                            // matching traffic types
                            for (bcastIter = ifCtx->broadcastContexts.begin(); bcastIter != ifCtx->broadcastContexts.end(); ++bcastIter) {
                                BroadcastContext *bcastCtx = *bcastIter;
                                if (bcastCtx->types.find(cotType) != bcastCtx->types.end()) {
                                    // A match.  Add the destination
                                    curDestInfo.dests.push_back(bcastCtx->dest);
                                }
                            }
                        }
                        // If any destinations were found, add
                        // this socket
                        if (!curDestInfo.dests.empty()) {
                            if (checkTXSocket(ifCtx, &curDestInfo.srcIpString)) {
                                destPairs[ifCtx] = curDestInfo;
                            }
                            curDestInfo.clear(epAsUdp ? ENDPOINT_UDP :
                                                        ENDPOINT_TCP);
                        }
                    }
                    
                    // Now look at all unicast-destination broadcast contexts
                    if (!fixedDest) {
                        for (bcastIter = unicastBroadcastContexts.begin(); bcastIter != unicastBroadcastContexts.end(); ++bcastIter) {
                            BroadcastContext *bcastCtx = *bcastIter;
                            if (bcastCtx->types.find(cotType) != bcastCtx->types.end()) {
                                // A match.  Add the destination
                                curDestInfo.dests.push_back(bcastCtx->dest);
                            }
                        }
                        if (!curDestInfo.dests.empty()) {
                            if (checkTXSocket(NULL, &curDestInfo.srcIpString)) {
                                // unicast-dest broadcasts should send out
                                // with "respond to source" endpoints
                                curDestInfo.srcEpType = epAsUdp ? 
                                                        ENDPOINT_UDP_USESRC :
                                                        ENDPOINT_TCP_USESRC;
                                destPairs[NULL] = curDestInfo;
                            }
                        }
                    }
                }
            }

            TakMessage takmsg(logger, qitem.cotmsg, ourUid, true);
            const int sendVersion = qitem.protoVersion < 0 ? 
                            localProtoVersion : 
                            qitem.protoVersion;
            // Reset the timer for sending version announce if this
            // is both an SA broadcast *and* we will be sending a
            // a version announce (non-0 or no cot msg)
            // No worries about the unicast-dest broadcast special case
            // inside loop that overrides version -- unicast-dest broadcasts
            // don't get version announces anyway so pretend they don't exist
            if (!qitem.destination && cotType == SITUATIONAL_AWARENESS &&
                                             (sendVersion || !qitem.cotmsg))
                nextBroadcastTime = CommoTime::now() + 
                                    (const float)TakMessage::PROTOINF_BCAST_SEC;
                 
            

            std::map<InterfaceContext *, DestInfo>::iterator iter;
            for (iter = destPairs.begin(); iter != destPairs.end(); iter++) {
                int finalVersion = sendVersion;
                if (!iter->first && !qitem.destination) {
                    // This is broadcast to a unicast destination
                    // Skip version-only messages
                    if (!qitem.cotmsg)
                        continue;

                    // ... and force to legacy XML
                    finalVersion = 0;
                }

                // Set the message endpoint to our source addr string
                if (qitem.cotmsg)
                    qitem.cotmsg->setEndpoint(iter->second.srcEpType,
                                              iter->second.srcIpString);

                // Serialize the message
                size_t s = 0;
                uint8_t *data = NULL;
                try {
                    if (!finalVersion && qitem.cotmsg) {
                        // Legacy protocol (version 0) with a message.
                        // Send just the cot message.
                        s = qitem.cotmsg->serialize(&data);
                    } else {
                        // dealing with either legacy version 0, forced send
                        // of protocol info or protocol > 0 full or forced msg
                        // For forced sends, use SELF_MIN to send out
                        // version info
                        s = takmsg.serializeAsProtobuf(
                                       finalVersion ? 
                                           finalVersion :
                                           TakProtoInfo::SELF_MIN,
                                       &data, 
                                       TakMessage::HEADER_TAKPROTO,
                                       true,
                                       true);
                        
//                        uint8_t *td;
//                        size_t ts = qitem.cotmsg->serialize(&td);
//                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Broadcast msg xml size: %d protobuf size: %d", ts, s);
//                        delete[] td;
                    }
                } catch (std::invalid_argument &ex) {
                    // Can't serialize this message - move on
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                        "Unexpected error serializing message - %s", ex.what());
                    delete[] data;
                    continue;
                }
                
                if (txCrypto) {
                    uint8_t *origData = data;
                    try {
                        txCrypto->encrypt(&data, &s);
                    } catch (std::invalid_argument &e) {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                            "Failed to encrypt outgoing message - %s", e.what());
                        delete[] data;
                        continue;
                    }

                    delete[] origData;
                }

                std::vector<NetAddress *>::iterator addrIter;
                for (addrIter = iter->second.dests.begin(); addrIter != iter->second.dests.end(); ++addrIter) {
                    UdpSocket **sock = iter->first ? &iter->first->outboundSocket : &txSocket;
                    try {
                        if (iter->first && !magtabEnabled) {
                            (*sock)->multicastto(*addrIter, data, s, localTTL);
                        } else {
                            if (magtabEnabled && iter->first) {
                                NetAddress *addr = *addrIter;
                                NetAddress *mtAddr = addr->deriveMagtabAddress();
                                (*sock)->sendto(mtAddr, data, s);
                                delete mtAddr;
                            
                            } else {
                                (*sock)->sendto(*addrIter, data, s);
                            }
                        }
                    } catch (SocketException &) {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Socket error sending UDP message");
                        delete *sock;
                        *sock = NULL;
                        break;
                    }
                }
                delete[] data;
            }


            qitem.implode();
        }


    }
}







DatagramSocketManagement::RxQueueItem::RxQueueItem(
        NetAddress* sender, const std::string &endpointId, bool generic,
        uint8_t* data, size_t nData) :   sender(sender),
                          endpointId(endpointId),
                          generic(generic),
                          data(new uint8_t[nData]),
                          dataLen(nData)
{
    memcpy(this->data, data, dataLen);
}

DatagramSocketManagement::RxQueueItem::~RxQueueItem()
{
}

void DatagramSocketManagement::RxQueueItem::implode()
{
    delete sender;
    delete[] data;
}

DatagramSocketManagement::TxQueueItem::TxQueueItem() :
                        destination(NULL),
                        cotmsg(NULL),
                        protoVersion(-1)
{

}

DatagramSocketManagement::TxQueueItem::TxQueueItem(
        const NetAddress *dest,
        const CoTMessage *msg,
        int protoVersion) COMMO_THROW (std::invalid_argument) :
                    destination(NetAddress::duplicateAddress(dest)),
                    cotmsg(NULL),
                    protoVersion(TakMessage::checkProtoVersion(protoVersion))
{
    cotmsg = new CoTMessage(*msg);
}

DatagramSocketManagement::TxQueueItem::TxQueueItem(const CoTMessage *msg)
            COMMO_THROW (std::invalid_argument) :
                destination(NULL),
                cotmsg(NULL),
                protoVersion(-1)
{
    cotmsg = new CoTMessage(*msg);
}

DatagramSocketManagement::TxQueueItem::~TxQueueItem()
{
}

void DatagramSocketManagement::TxQueueItem::implode()
{
    if (cotmsg) {
        delete cotmsg;
        cotmsg = NULL;
    }
    if (destination) {
        delete destination;
        destination = NULL;
    }
}


DatagramSocketManagement::RecvUnicastContext::RecvUnicastContext(int localPort) :
        PhysicalNetInterface(NULL), localPort(localPort), mcastAddrs()
{
}

DatagramSocketManagement::RecvUnicastContext::RecvUnicastContext(
        const HwAddress* hwAddr, int localPort, const char** mcastAddrStrs,
        size_t nMcastAddrs) COMMO_THROW (std::invalid_argument) :
                PhysicalNetInterface(hwAddr), localPort(localPort),
                mcastAddrs()
{
    // Try first to convert the addresses given from text to
    // binary format.
    for (size_t i = 0; i < nMcastAddrs; ++i)
    {
        std::string mcastStr(mcastAddrStrs[i]);
        NetAddress *n = NetAddress::create(mcastStr.c_str());
        if (!n || mcastAddrs.find(mcastStr) != mcastAddrs.end()) {
            std::map<std::string, NetAddress *>::iterator iter;
            for (iter = mcastAddrs.begin(); iter != mcastAddrs.end(); ++iter)
                delete iter->second;
            this->mcastAddrs.clear();
            throw std::invalid_argument(!n ? "Invalid multicast address string" : "Duplicate multicast address string");
        }
        mcastAddrs.insert(std::pair<std::string, NetAddress *>(mcastStr, n));
    }
}

DatagramSocketManagement::RecvUnicastContext::~RecvUnicastContext()
{
    std::map<std::string, NetAddress *>::iterator iter;
    for (iter = mcastAddrs.begin(); iter != mcastAddrs.end(); ++iter)
        delete iter->second;
}

DatagramSocketManagement::BroadcastContext::BroadcastContext(
        const HwAddress* hwAddr, const CoTMessageType* typesArray, size_t nTypes,
        const char* addrStr, int destPort)
            COMMO_THROW (std::invalid_argument) :
                PhysicalNetInterface(hwAddr == NULL ? &UNICAST_HW_ADDR : hwAddr), dest(NULL), types(), destPort(destPort)
{
    // Parse address first in case it fails
    NetAddress *n = NetAddress::create(addrStr, destPort);
    if (!n)
        throw std::invalid_argument("Invalid address format");
    if (hwAddr && !n->isMulticast()) {
        delete n;
        throw std::invalid_argument("Non-multicast address given for multicast broadcast");
    }
    if (!hwAddr && n->isMulticast()) {
        delete n;
        throw std::invalid_argument("Multicast address given for unicast broadcast");
    }
    dest = n;
    for (size_t i = 0; i < nTypes; ++i)
        types.insert(typesArray[i]);
}

DatagramSocketManagement::BroadcastContext::~BroadcastContext()
{
    delete dest;
}

DatagramSocketManagement::InterfaceContext::InterfaceContext() :
        recvContexts(), broadcastContexts(),
        netAddr(NULL), stateMutex(), outboundSocket(NULL)
{
}

DatagramSocketManagement::InterfaceContext::~InterfaceContext()
{
    // Assumes external sync
    std::set<BroadcastContext *>::iterator iter;
    for (iter = broadcastContexts.begin(); iter != broadcastContexts.end(); ++iter)
    {
        delete *iter;
    }
    RxCtxSet::iterator rxiter;
    for (rxiter = recvContexts.begin(); rxiter != recvContexts.end(); ++rxiter)
    {
        delete *rxiter;
    }
    delete netAddr;
    delete outboundSocket;
}

DatagramSocketManagement::SharedSocketContext::SharedSocketContext(int port,
                                                    bool generic) :
        joinedAddrs(), pendingAddrs(), socket(NULL),
        lastRxTime(CommoTime::ZERO_TIME), endpointStr(),
        port(port), generic(generic)
{
    endpointStr = "*:";
    endpointStr += InternalUtils::intToString(port);
    endpointStr += ":udp";
}
DatagramSocketManagement::SharedSocketContext::~SharedSocketContext()
{
    delete socket;
}
