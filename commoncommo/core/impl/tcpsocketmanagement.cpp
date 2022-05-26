#define __STDC_LIMIT_MACROS
#include "tcpsocketmanagement.h"
#include "takmessage.h"
#include "internalutils.h"
#include "commothread.h"

#include <limits.h>
#include <string.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;
using namespace atakmap::commoncommo::impl::thread;


namespace {
    const int LISTEN_BACKLOG = 15;
    const size_t RX_CLIENT_MAX_DATA_LEN = 500 * 1024;
    const float INBOUND_RETRY_SECS = 60.0f;
    const float DEFAULT_CONN_TIMEOUT_SEC = 20.0f;

    const char *THREAD_NAMES[] = {
        "cmotcp.io", 
        "cmotcp.rxq",
        "cmotcp.txerr"
    };
}


TcpSocketManagement::TcpSocketManagement(CommoLogger *logger, 
                                         ContactUID *ourUid) :
        ThreadedHandler(3, THREAD_NAMES),
        logger(logger),
        ourUid(ourUid),
        resolver(NULL),
        connTimeoutSec(DEFAULT_CONN_TIMEOUT_SEC),
        rxCrypto(NULL),
        txCrypto(NULL),
        inboundContexts(),
        inboundNeedsRebuild(false),
        clientContexts(),
        txContexts(),
        txNeedsRebuild(false),
        txMutex(),
        resolverContexts(),
        resolverContextsMutex(),
        rxQueue(),
        rxQueueMutex(),
        rxQueueMonitor(),
        txErrQueue(),
        txErrQueueMutex(),
        txErrQueueMonitor(),
        listeners(),
        listenerMutex(),
        txErrListeners(),
        txErrListenerMutex(),
        ifaceListeners(),
        ifaceListenerMutex(),
        globalIOMutex(RWMutex::Policy_Fair),
        selector()
{
    resolver = new ResolverQueue(logger, this, 5.0f, 1);
    startThreads();
}

TcpSocketManagement::~TcpSocketManagement()
{
    delete resolver;
    stopThreads();

    // With the threads all joined, we can remove everything safely
    // Clean the queues first
    while (!rxQueue.empty()) {
        RxQueueItem rxi = rxQueue.back();
        rxQueue.pop_back();
        rxi.implode();
    }
    
    InboundCtxSet::iterator inbIter;
    for (inbIter = inboundContexts.begin(); 
                inbIter != inboundContexts.end(); ++inbIter) {
        delete *inbIter;
    }

    ClientCtxSet::iterator clIter;
    for (clIter = clientContexts.begin(); 
                clIter != clientContexts.end(); ++clIter) {
        delete *clIter;
    }

    TxCtxSet::iterator txIter;
    for (txIter = txContexts.begin(); 
                txIter != txContexts.end(); ++txIter) {
        delete *txIter;
    }

    ResolverReqMap::iterator resIter;
    for (resIter = resolverContexts.begin(); 
                resIter != resolverContexts.end(); ++resIter) {
        delete resIter->second;
    }
    
    if (txCrypto)
        delete txCrypto;
    if (rxCrypto)
        delete rxCrypto;
}

void TcpSocketManagement::threadStopSignal(size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        {
            break;
        }
    case TX_ERRQUEUE_THREADID:
        {
            Lock lock(txErrQueueMutex);
            txErrQueueMonitor.broadcast(lock);
        }
        break;
    case RX_QUEUE_THREADID:
        {
            Lock lock(rxQueueMutex);
            rxQueueMonitor.broadcast(lock);
            break;
        }
    }
}


TcpInboundNetInterface *TcpSocketManagement::addInboundInterface(int port)
{
    WriteLock lock(globalIOMutex);
    
    if (port > UINT16_MAX || port < 0)
        return NULL;
    uint16_t sport = (uint16_t)port;

    InboundContext *newCtx = new InboundContext(sport);
    if (inboundContexts.find(newCtx) != inboundContexts.end())
        return NULL;
    
    try {
        newCtx->initSocket();
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Successfully created socket to listen on TCP port %d at initial addition", port);
    } catch (SocketException &) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Failed to create socket to listen on TCP port %d during initial addition - will retry", port);
    }
    
    inboundContexts.insert(newCtx);
    inboundNeedsRebuild = true;
    return newCtx;
}

CommoResult TcpSocketManagement::removeInboundInterface(
                                       TcpInboundNetInterface *iface)
{
    WriteLock lock(globalIOMutex);
    
    InboundContext *ctx = (InboundContext *)iface;
    InboundCtxSet::iterator iter = inboundContexts.find(ctx);
    if (iter == inboundContexts.end())
        return COMMO_ILLEGAL_ARGUMENT;
    
    inboundContexts.erase(ctx);
    delete ctx;
    inboundNeedsRebuild = true;
    return COMMO_SUCCESS;
}

void TcpSocketManagement::setConnTimeout(float seconds)
{
    connTimeoutSec = seconds;
}

void TcpSocketManagement::setCryptoKeys(const uint8_t *authKey,
                                        const uint8_t *cryptoKey)
{
    {
        Lock lock(rxQueueMutex);
        
        if (rxCrypto) {
            delete rxCrypto;
            rxCrypto = NULL;
        }
        if (authKey && cryptoKey)
            rxCrypto = new MeshNetCrypto(logger, cryptoKey, authKey);
    }
    {
        Lock txLock(txMutex);
        
        if (txCrypto) {
            delete txCrypto;
            txCrypto = NULL;
        }
        if (authKey && cryptoKey)
            txCrypto = new MeshNetCrypto(logger, cryptoKey, authKey);
    }
}

void TcpSocketManagement::sendMessage(const std::string &host, int port,
                                      const CoTMessage *msg, int protoVersion)
                                               COMMO_THROW (std::invalid_argument)
{
    if (port > UINT16_MAX || port < 0)
        throw std::invalid_argument("Destination port out of range");
    uint16_t sport = (uint16_t)port;

    TxContext *ctx = NULL;
    {
        Lock lock(txMutex);
        ctx = new TxContext(host, sport, txCrypto, msg, ourUid, protoVersion);
    }

    // See if host contains an IP string and does not need resolution
    NetAddress *destAddr = NetAddress::create(host.c_str(), sport);

    if (destAddr) {
        Lock lock(txMutex);
        ctx->destination = destAddr;
        txContexts.insert(ctx);
        txNeedsRebuild = true;
    } else {
        // Needs name resolution
        Lock lock(resolverContextsMutex);
        ResolverReqMap resolverContexts;
        ResolverQueue::Request *r = resolver->queueForResolution(host);
        resolverContexts.insert(ResolverReqMap::value_type(r, ctx));
    }
}

bool TcpSocketManagement::resolutionComplete(
            ResolverQueue::Request *identifier,
            const std::string &hostAddr,
            NetAddress *result)
{
    // Find our context
    TxContext *ctx = NULL;
    {
        Lock lock(resolverContextsMutex);
        ResolverReqMap::iterator iter = resolverContexts.find(identifier);
        if (iter == resolverContexts.end())
            // should never happen
            return false;
        ctx = iter->second;
        resolverContexts.erase(iter);
    }

    if (!result) {
        Lock lock(txMutex);
        queueTxErr(ctx, "Failed to resolve host", false);
    } else {
        Lock lock(txMutex);
        ctx->destination = result->deriveNewAddress(ctx->destPort);
        txContexts.insert(ctx);
        txNeedsRebuild = true;
    }
    return false;
}


void TcpSocketManagement::addMessageReceiver(TcpMessageListener *receiver)
{
    Lock lock(listenerMutex);
    listeners.insert(receiver);
}

void TcpSocketManagement::removeMessageReceiver(TcpMessageListener *receiver)
{
    Lock lock(listenerMutex);
    listeners.erase(receiver);
}

CommoResult TcpSocketManagement::addCoTSendFailureListener(
            CoTSendFailureListener *listener)
{
    Lock lock(txErrListenerMutex);
    if (!txErrListeners.insert(listener).second)
        return COMMO_ILLEGAL_ARGUMENT;
    return COMMO_SUCCESS;
}

CommoResult TcpSocketManagement::removeCoTSendFailureListener(
            CoTSendFailureListener *listener)
{
    Lock lock(txErrListenerMutex);
    if (txErrListeners.erase(listener) != 1)
        return COMMO_ILLEGAL_ARGUMENT;
    return COMMO_SUCCESS;
}


void TcpSocketManagement::addInterfaceStatusListener(
        InterfaceStatusListener *listener)
{
    Lock lock(ifaceListenerMutex);
    ifaceListeners.insert(listener);
}

void TcpSocketManagement::removeInterfaceStatusListener(
        InterfaceStatusListener *listener)
{
    Lock lock(ifaceListenerMutex);
    ifaceListeners.erase(listener);
}

// Assumes holding of global IO mutex
void TcpSocketManagement::fireIfaceStatus(TcpInboundNetInterface *iface,
                                          bool up)
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

void TcpSocketManagement::threadEntry(
        size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        ioThreadProcess();
        break;
    case RX_QUEUE_THREADID:
        recvQueueThreadProcess();
        break;
    case TX_ERRQUEUE_THREADID:
        txErrQueueThreadProcess();
        break;
    }
}


void TcpSocketManagement::ioThreadProcess()
{
    std::vector<Socket *> writeSocks;
    std::vector<Socket *> readSocks;
    InboundCtxSet inboundErroredCtxs;
    CommoTime lowErrorTime = CommoTime::ZERO_TIME;
    TxCtxSet curTxSet;

    while (!threadShouldStop(IO_THREADID)) {
        ReadLock lock(globalIOMutex);
        bool resetSelect = false;
        
        if (!inboundNeedsRebuild && 
                    !inboundErroredCtxs.empty() &&
                    CommoTime::now() > lowErrorTime)
            inboundNeedsRebuild = true;

        if (inboundNeedsRebuild) {
            CommoTime nowTime = CommoTime::now();
            resetSelect = true;
            inboundErroredCtxs.clear();
            readSocks.clear();
            InboundCtxSet::iterator inbIter;
            for (inbIter = inboundContexts.begin(); 
                         inbIter != inboundContexts.end(); ++inbIter) {
                InboundContext *ctx = *inbIter;
                if (!ctx->socket && nowTime > ctx->retryTime) {
                    try {
                        ctx->initSocket();
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Successfully created socket to listen on TCP port %d", (int)ctx->localPort);
                    } catch (SocketException &) {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Failed to create socket to listen on TCP port %d - will retry", (int)ctx->localPort);
                        ioThreadResetInboundCtx(ctx, false);
                    }
                }
                
                if (!ctx->socket) {
                    if (inboundErroredCtxs.empty() || 
                            ctx->retryTime < lowErrorTime)
                        lowErrorTime = ctx->retryTime;
                    
                    inboundErroredCtxs.insert(ctx);
                } else {
                    readSocks.push_back(ctx->socket);
                    if (!ctx->upEventFired) {
                        fireIfaceStatus(ctx, true);
                        ctx->upEventFired = true;
                    }
                }
            }
            
            ClientCtxSet::iterator clientIter;
            for (clientIter = clientContexts.begin();
                         clientIter != clientContexts.end();
                         ++clientIter) {
                ClientContext *ctx = *clientIter;
                readSocks.push_back(ctx->socket);
            }
            
            inboundNeedsRebuild = false;
        }
        
        {
            Lock txLock(txMutex);
            if (txNeedsRebuild) {
                writeSocks.clear();
                curTxSet.clear();
                resetSelect = true;
                TxCtxSet::iterator txIter = txContexts.begin();
                while (txIter != txContexts.end()) {
                    TxContext *ctx = *txIter;
                    TxCtxSet::iterator curIter = txIter;
                    txIter++;

                    if (!ctx->socket) {
                        try {
                            // Create socket, issue connect call
                            ctx->socket = new TcpSocket(
                                    ctx->destination->family, false);
                            if (!ctx->socket->connect(ctx->destination)) {
                                ctx->isConnecting = true;
                                ctx->timeout = CommoTime::now() + connTimeoutSec;
                            }
                            // ... else immediate connect success
                        } catch (SocketException &) {
                            queueTxErr(ctx, "Failed to create transmission socket",
                                       false);
                            txContexts.erase(curIter);
                            continue;
                        }
                    }
                    writeSocks.push_back(ctx->socket);
                    curTxSet.insert(ctx);
                }
                
                txNeedsRebuild = false;
            }
        }
        
        if (resetSelect)
            selector.setSockets(&readSocks, &writeSocks);


        bool killAllSockets = false;
        try {
            if (!selector.doSelect(250)) {
                // Timeout
                CommoTime nowTime = CommoTime::now();
                TxCtxSet::iterator txIter;
                for (txIter = curTxSet.begin();
                                 txIter != curTxSet.end(); ++txIter) {
                    TxContext *ctx = *txIter;
                    if (ctx->isConnecting && nowTime > ctx->timeout) {
                        Lock txLock(txMutex);
                        queueTxErr(ctx, "Connection timed out", true);
                    }
                }
                continue;
            }

        } catch (SocketException &) {
            // odd. Force a rebuild
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "tcp main io select failed");
            killAllSockets = true;
        }



        // Look at our incoming clients *first*, before looking for
        // new clients as it could grow our client set to something NOT
        // in the select set
        ClientCtxSet::iterator clientIter = clientContexts.begin();
        while (clientIter != clientContexts.end()) {
            ClientContext *ctx = *clientIter;
            ClientCtxSet::iterator curIter = clientIter;
            clientIter++;

            if (killAllSockets) {
                delete ctx;
                clientContexts.erase(curIter);
                inboundNeedsRebuild = true;

            } else if (selector.getLastReadState(ctx->socket) == NetSelector::READABLE) {
                bool abort = false;
                try {
                    while (true) {
                        size_t n = ctx->bufLen - ctx->len;
                        if (!n) {
                            if (ctx->bufLen > RX_CLIENT_MAX_DATA_LEN) {
                                abort = true;
                                throw SocketException(netinterfaceenums::ERR_OTHER,
                                                      "Client sent excessive amount of data");
                            }
                            n = 5 * 1024;
                            ctx->growCapacity(n);
                        }
                        n = ctx->socket->read(ctx->data + ctx->len, n);
                        if (!n)
                            break;
                        ctx->len += n;
                    }
                } catch (SocketException &) {
                    // Something went awry or client disconnected.
                    // If we got any data, pass it on
                    if (!abort)
                        ioThreadQueueRx(ctx);
                    delete ctx;
                    clientContexts.erase(curIter);
                    inboundNeedsRebuild = true;
                }
            }
        }

        for (InboundCtxSet::iterator inbIter = inboundContexts.begin(); 
                     inbIter != inboundContexts.end(); ++inbIter) {
            InboundContext *ctx = *inbIter;
            bool ctxErr = killAllSockets;
            if (!ctxErr && ctx->socket && selector.getLastReadState(ctx->socket) == NetSelector::READABLE) {
                try {
                    while (true) {
                        NetAddress *clientAddr = NULL;
                        TcpSocket *clientSock = ctx->socket->accept(&clientAddr);
                        if (!clientSock)
                            break;

                        ClientContext *cctx = new ClientContext(clientSock,
                                                                clientAddr,
                                                                ctx->endpoint);
                        clientContexts.insert(cctx);
                        inboundNeedsRebuild = true;
                    }
                } catch (SocketException &) {
                    ctxErr = true;
                }
            }
            if (ctxErr)
                ioThreadResetInboundCtx(ctx, true);
        }


        TxCtxSet::iterator txIter;
        for (txIter = curTxSet.begin();
                         txIter != curTxSet.end(); ++txIter) {
            TxContext *ctx = *txIter;
            if (killAllSockets) {
                Lock txLock(txMutex);
                queueTxErr(ctx, "An unknown error occurred", true);

            } else {
                bool connDone = false;
                if (ctx->isConnecting) {
                    if (selector.getLastConnectState(ctx->socket) == NetSelector::WRITABLE) {
                        if (ctx->socket->isSocketErrored()) {
                            Lock txLock(txMutex);
                            queueTxErr(ctx, "Connection to remote host failed", true);
                            ctx = NULL;
                        } else {
                            // connection just finished
                            ctx->isConnecting = false;
                            connDone = true;
                        }
                    } else if (CommoTime::now() > ctx->timeout) {
                        Lock txLock(txMutex);
                        queueTxErr(ctx, "Connection timed out", true);
                        ctx = NULL;
                    }
                }
                
                if (ctx && !ctx->isConnecting && (connDone || selector.getLastWriteState(ctx->socket) == NetSelector::WRITABLE)) {
                    try {
                        while (ctx->dataLen > 0) {
                            size_t n = ctx->socket->write(ctx->data, ctx->dataLen);
                            if (n == 0)
                                // would block
                                break;
                            ctx->data += n;
                            ctx->dataLen -= n;
                        }
                        if (ctx->dataLen == 0) {
                            // all sent, all done
                            Lock txLock(txMutex);
                            killTxCtx(ctx, true);
                        }
                    } catch (SocketException &) {
                        Lock txLock(txMutex);
                        queueTxErr(ctx, "I/O error transmitting data", true);
                    }
                }
            } 
        }
    }
}

void TcpSocketManagement::recvQueueThreadProcess()
{
    while (!threadShouldStop(RX_QUEUE_THREADID)) {
        Lock qLock(rxQueueMutex);
        if (rxQueue.empty()) {
            rxQueueMonitor.wait(qLock);
            continue;
        }

        RxQueueItem qItem = rxQueue.back();
        rxQueue.pop_back();
        uint8_t *data = qItem.data;
        size_t dataLen = qItem.dataLen;
        bool decrypted = false;
        try {
            if (rxCrypto) {
                decrypted = rxCrypto->decrypt(&data, &dataLen);
                if (!decrypted)
                    throw std::invalid_argument("Unable to decrypt");
            }
            TakMessage takmsg(logger, data, dataLen, true, true);
            const CoTMessage *msg = takmsg.getCoTMessage();
            if (msg) {
                Lock listenerLock(listenerMutex);
                std::set<TcpMessageListener *>::iterator iter;
                for (iter = listeners.begin(); iter != listeners.end(); 
                                                                 ++iter) {
                    TcpMessageListener *l = *iter;
                    l->tcpMessageReceived(qItem.sender, &qItem.endpoint, msg);
                }
            }
        } catch (std::invalid_argument &e) {
            // Drop this item
            CommoLogger::ParsingDetail detail{ data, dataLen, e.what() == NULL ? "" : e.what(), qItem.endpoint.c_str() };
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, CommoLogger::TYPE_PARSING, &detail, "Invalid CoT message received: %s", e.what() == NULL ? "unknown error" : e.what());
        }
        if (decrypted)
            delete[] data;
        qItem.implode();
    }
}


void TcpSocketManagement::txErrQueueThreadProcess()
{
    while (!threadShouldStop(TX_ERRQUEUE_THREADID)) {
        Lock qLock(txErrQueueMutex);
        if (txErrQueue.empty()) {
            txErrQueueMonitor.wait(qLock);
            continue;
        }

        TxErrQueueItem qItem = txErrQueue.back();
        txErrQueue.pop_back();
        {
            Lock listenerLock(txErrListenerMutex);
            std::set<CoTSendFailureListener *>::iterator iter;
            for (iter = txErrListeners.begin(); iter != txErrListeners.end(); ++iter) {
                CoTSendFailureListener *l = *iter;
                l->sendCoTFailure(qItem.destinationHost.c_str(),
                                  qItem.destinationPort, qItem.errMsg.c_str());
            }
        }

    }
}




// Queue an error callback and kill the context
// Assumes holding of tx mutex
// if removeFromCtxSet, take from global ctxset and flag txrebuild
void TcpSocketManagement::queueTxErr(TxContext *ctx, const std::string &reason, bool removeFromCtxSet)
{
    {
        Lock errLock(txErrQueueMutex);
        txErrQueue.push_front(TxErrQueueItem(ctx->host, ctx->destPort, reason.c_str()));
        txErrQueueMonitor.broadcast(errLock);
    }
    killTxCtx(ctx, removeFromCtxSet);
}

// Kill off a tx context
// Assumes holding of tx mutex
// if removeFromCtxSet, take from global ctxset and flag txrebuild
void TcpSocketManagement::killTxCtx(TxContext *ctx, bool removeFromCtxSet)
{
    if (removeFromCtxSet) {
        txContexts.erase(ctx);
        txNeedsRebuild = true;
    }
    delete ctx;
}

// Close socket if non-null.
// Set retry time. flag inbound rebuild if flagReset true
// Assumes holding of globalIOMutex
void TcpSocketManagement::ioThreadResetInboundCtx(InboundContext *ctx,
                                                  bool flagReset)
{
    if (ctx->socket) {
        delete ctx->socket;
        ctx->socket = NULL;
    }
    if (ctx->upEventFired) {
        ctx->upEventFired = false;
        fireIfaceStatus(ctx, false);
    }
    ctx->retryTime = CommoTime::now() + INBOUND_RETRY_SECS;
    if (flagReset)
        inboundNeedsRebuild = true;
}

// Move any received data to rx queue; if nothing there, just
// no-op
void TcpSocketManagement::ioThreadQueueRx(ClientContext *ctx)
{
    if (ctx->len == 0)
        return;

    {
        Lock lock(rxQueueMutex);
        RxQueueItem rx(ctx->clientAddr, ctx->endpoint, ctx->data, ctx->len);
        ctx->clearBuffers();
        rxQueue.push_front(rx);
        rxQueueMonitor.broadcast(lock);
    }
}







TcpSocketManagement::RxQueueItem::RxQueueItem(
        NetAddress *sender, const std::string &endpoint,
        uint8_t *data,
        size_t nData) :   sender(sender),
                          endpoint(endpoint),
                          data(data),
                          dataLen(nData)
{
}

TcpSocketManagement::RxQueueItem::~RxQueueItem()
{
}

void TcpSocketManagement::RxQueueItem::implode()
{
    delete sender;
    delete[] data;
}


TcpSocketManagement::TxErrQueueItem::TxErrQueueItem(
        const std::string &host,
        int port,
        const std::string &errMsg) :
                destinationHost(host),
                destinationPort(port),
                errMsg(errMsg)
{
}

TcpSocketManagement::TxErrQueueItem::~TxErrQueueItem()
{
}



TcpSocketManagement::InboundContext::InboundContext(uint16_t localPort) :
        TcpInboundNetInterface(localPort),
        socket(NULL),
        retryTime(CommoTime::ZERO_TIME),
        localPort(localPort),
        localAddr(NULL),
        endpoint(),
        upEventFired(false)
{
    localAddr = NetAddress::create(NA_TYPE_INET4, localPort);
    endpoint = "*:";
    endpoint += InternalUtils::intToString(localPort);
    endpoint += ":tcp";
}


TcpSocketManagement::InboundContext::~InboundContext()
{
    delete socket;
    delete localAddr;
}

void TcpSocketManagement::InboundContext::initSocket() COMMO_THROW (SocketException)
{
    try {
        socket = new TcpSocket(NA_TYPE_INET4, false);
        socket->bind(localAddr);
        socket->listen(LISTEN_BACKLOG);
    } catch (SocketException &e) {
        delete socket;
        socket = NULL;
        throw e;
    }
}


TcpSocketManagement::ClientContext::ClientContext(TcpSocket *socket,
                                                  NetAddress *clientAddr,
                                                  const std::string &endpoint) :
        clientAddr(clientAddr),
        endpoint(endpoint),
        socket(socket),
        data(NULL),
        len(0),
        bufLen(0)
{
}

TcpSocketManagement::ClientContext::~ClientContext()
{
    delete[] data;
    delete socket;
    delete clientAddr;
}

void TcpSocketManagement::ClientContext::growCapacity(size_t n)
{
    size_t newLen = bufLen + n;
    uint8_t *newBuf = new uint8_t[newLen];
    if (data) {
        memcpy(newBuf, data, len);
        delete[] data;
    }
    data = newBuf;
    bufLen = newLen;
}

// wipe buffer data so it isn't free'd
void TcpSocketManagement::ClientContext::clearBuffers()
{
    data = NULL;
    len = bufLen = 0;
    clientAddr = NULL;
}


TcpSocketManagement::TxContext::TxContext(const std::string &host,
                                          uint16_t port,
                                          MeshNetCrypto *crypto,
                                          const CoTMessage *msg,
                                          ContactUID *ourUid,
                                          int protoVersion)
                                              COMMO_THROW (std::invalid_argument) :
        destination(NULL),
        host(host),
        destPort(port),
        data(NULL),
        origData(NULL),
        dataLen(0),
        socket(NULL),
        isConnecting(false),
        timeout(CommoTime::ZERO_TIME)
{
    CoTMessage msgCopy(*msg);
    msgCopy.setEndpoint(ENDPOINT_NONE, "");
    protoVersion = TakMessage::checkProtoVersion(protoVersion);
    if (protoVersion) {
        TakMessage takmsg(msg->getLogger(), &msgCopy, ourUid, true);
        dataLen = takmsg.serializeAsProtobuf(protoVersion, &origData,
                                             TakMessage::HEADER_TAKPROTO,
                                             true, true);
    } else {
        dataLen = msgCopy.serialize(&origData);
    }
    if (crypto) {
        try {
            uint8_t *d = origData;
            crypto->encrypt(&origData, &dataLen);
            delete[] d;
        } catch (std::invalid_argument &e) {
            delete[] origData;
            origData = NULL;
            throw e;
        }
    }
    data = origData;
}

// close+delete socket if !NULL, delete destination
// delete cotmsg
TcpSocketManagement::TxContext::~TxContext()
{
    delete socket;
    delete destination;
    delete[] origData;
}

