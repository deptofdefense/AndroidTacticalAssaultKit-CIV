#define __STDC_LIMIT_MACROS
#include "streamingsocketmanagement.h"
#include "takmessage.h"
#include "commothread.h"
#include "libxml/tree.h"

#include <sstream>
#include <limits.h>
#include <string.h>
#include <inttypes.h>

#include "openssl/pkcs12.h"
#include "openssl/err.h"

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;
using namespace atakmap::commoncommo::impl::thread;


namespace {
    const float PROTO_TIMEOUT_SECONDS = 60.0f;
    const float DEFAULT_CONN_TIMEOUT_SECONDS = 20.0f;
    const float CONN_RETRY_SECONDS = 15.0f;
    const float RESOLVE_RETRY_SECONDS = 30.0f;
    // Send "ping" to server after this long of no rx activity
    const float RX_STALE_SECONDS = 15.0f;
    // Repeat "ping" to server this often as long as no rx activity goes on
    const float RX_STALE_PING_SECONDS = 4.5f;
    // Reset connection after this long of no rx activity
    const float RX_TIMEOUT_SECONDS = 25.0f;
    const char MESSAGE_END_TOKEN[] = "</event>";
    const size_t MESSAGE_END_TOKEN_LEN = sizeof(MESSAGE_END_TOKEN) - 1;
    const char *PING_UID_SUFFIX = "-ping";

    char *copyString(const std::string &s)
    {
        char *c = new char[s.length() + 1];
        const char *sc = s.c_str();
        strcpy(c, sc);
        return c;
    }
    
    const char *THREAD_NAMES[] = {
        "cmostrm.conn", 
        "cmostrm.io",
        "cmostrm.rxq",
    };
}

/*************************************************************************/
// StreamingSocketManagement constructor/destructor


StreamingSocketManagement::StreamingSocketManagement(CommoLogger *logger,
                                                const std::string &myuid) :
        ThreadedHandler(3, THREAD_NAMES), logger(logger),
        resolver(new ResolverQueue(logger, this, RESOLVE_RETRY_SECONDS, RESQ_INFINITE_TRIES)),
        connTimeoutSec(DEFAULT_CONN_TIMEOUT_SECONDS),
        monitor(true),
        sslCtx(NULL),
        contexts(), contextMutex(RWMutex::Policy_Fair),
        ioNeedsRebuild(false),
        upContexts(), upMutex(),
        downContexts(), downNeedsRebuild(false), downMutex(),
        resolutionContexts(),
        myuid(myuid),
        myPingUid(myuid + PING_UID_SUFFIX),
        rxQueue(), rxQueueMutex(), rxQueueMonitor(),
        ifaceListeners(), ifaceListenersMutex(),
        listeners(), listenersMutex()
{
    ERR_clear_error();
    sslCtx = SSL_CTX_new(SSLv23_client_method());
    if (sslCtx)
        // Be certain openssl doesn't do anything with its internal
        // verification as we will do our own (internal verify cannot be made
        // to work with in-memory certs)
        SSL_CTX_set_verify(sslCtx, SSL_VERIFY_NONE, NULL);
    else {
        unsigned long errCode = ERR_get_error();
        char ebuf[1024];
        ERR_error_string_n(errCode, ebuf, 1024);
        ebuf[1023] = '\0';
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Cannot create SSL Context! SSL connections will not be available (details: %s)", ebuf);
    }
    startThreads();
}

StreamingSocketManagement::~StreamingSocketManagement()
{
    delete resolver;
    stopThreads();

    // With the threads all joined, we can remove everything safely
    // Clean the rx queue first
    while (!rxQueue.empty()) {
        RxQueueItem rxi = rxQueue.back();
        rxQueue.pop_back();
        rxi.implode();
    }

    // Now tear down all the contexts
    ContextMap::iterator iter;
    for (iter = contexts.begin(); iter != contexts.end(); ++iter)
        delete iter->second;

    if (sslCtx) {
        SSL_CTX_free(sslCtx);
        sslCtx = NULL;
    }
}


/*************************************************************************/
// StreamingSocketManagement - interface management

StreamingNetInterface *StreamingSocketManagement::addStreamingInterface(
        const char *addr, int port, const CoTMessageType *types,
        size_t nTypes, const uint8_t *clientCert, size_t clientCertLen,
        const uint8_t *caCert, size_t caCertLen,
        const char *certPassword,
        const char *caCertPassword,
        const char *username, const char *password, CommoResult *resultCode)
{
    CommoResult rc = COMMO_ILLEGAL_ARGUMENT;
    StreamingNetInterface *ret = NULL;

    uint16_t sport = (uint16_t)port;
    ConnType connType = CONN_TYPE_TCP;
    NetAddress *endpoint = NULL;
    SSLConnectionContext *ssl = NULL;
    std::string epString;


    // First try to parse all the args to get essential connection info
    if (port > UINT16_MAX || port < 0) {
        rc = COMMO_ILLEGAL_ARGUMENT;
        goto done;
    }
    // Try parsing addr as a string-form of an IP address directly.
    // If this fails, endpoint will be null
    endpoint = NetAddress::create(addr, sport);

    if (clientCert) {
        connType = CONN_TYPE_SSL;
        try {
            ssl = new SSLConnectionContext(myuid, 
                                           sslCtx, clientCert, clientCertLen,
                                           caCert, caCertLen, certPassword,
                                           caCertPassword,
                                           username, password);
        } catch (SSLArgException &e) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Unable to create SSL connection configuration: %s", e.what());
            // Remove any lingering open ssl error codes
            ERR_clear_error();
            rc = e.errCode;
            goto done;
        }
    }

    // Get string that is unique based on identifying criteria of the connection
    epString = getEndpointString(addr, port, connType);

    {
        WriteLock lock(contextMutex);
        ContextMap::iterator iter = contexts.find(epString);
        if (iter != contexts.end()) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                    "Streaming interface %s already exists", epString.c_str());
            delete endpoint;
            
            rc = COMMO_ILLEGAL_ARGUMENT;
            goto done;
        }

        ConnectionContext *ctx = new ConnectionContext(epString, addr, sport,
                                                       endpoint, connType, ssl);
        contexts.insert(ContextMap::value_type(epString, ctx));

        ctx->broadcastCoTTypes.insert(types, types + nTypes);

        if (endpoint) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                    "Streaming interface %s added with pre-resolved IP string", epString.c_str());

            // Already resolved - go straight to connection
            Lock downLock(downMutex);
            downContexts.insert(ctx);
        } else {
            // Needs name resolution
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                    "Streaming interface %s added with hostname string %s - going to name resolution", epString.c_str(), addr);

            ctx->resolverRequest = 
                    resolver->queueForResolution(ctx->remoteEndpointAddrString);
            resolutionContexts.insert(
                    ResolverMap::value_type(ctx->resolverRequest, ctx));
        }
        ret = ctx;
        rc = COMMO_SUCCESS;
    }
    
done:
    if (resultCode)
        *resultCode = rc;

    return ret;
}

CommoResult StreamingSocketManagement::removeStreamingInterface(
        std::string *endpoint, StreamingNetInterface *iface)
{
    WriteLock lock(contextMutex);

    // Try to erase context from the master list
    ContextMap::iterator cmIter = contexts.begin();
    for (cmIter = contexts.begin(); cmIter != contexts.end(); ++cmIter) {
        if (cmIter->second == iface)
            break;
    }
    if (cmIter == contexts.end())
        return COMMO_ILLEGAL_ARGUMENT;
    
    *endpoint = std::string(iface->remoteEndpointId, iface->remoteEndpointLen);
    contexts.erase(cmIter);

    // Now we know it was one of ours
    ConnectionContext *ctx = (ConnectionContext *)iface;

    // Remove from up, down, or resolution list, whichever it is in
    {
        Lock uLock(upMutex);
        if (upContexts.erase(ctx) == 1)
            ioNeedsRebuild = true;
    }
    {
        Lock dLock(downMutex);
        if (downContexts.erase(ctx) == 1)
            downNeedsRebuild = true;
    }
    
    if (ctx->resolverRequest) {
        resolutionContexts.erase(ctx->resolverRequest);
        resolver->cancelResolution(ctx->resolverRequest);
    }

    // Remove any reference in the rxQueue
    {
        Lock rxqlock(rxQueueMutex);
        RxQueue::iterator iter = rxQueue.begin();
        while (iter != rxQueue.end()) {
            if (iter->ctx == ctx) {
                RxQueue::iterator eraseMe = iter;
                iter++;
                eraseMe->implode();
                rxQueue.erase(eraseMe);
            } else {
                iter++;
            }
        }
    }

    // Clean up the context itself
    delete ctx;
    return COMMO_SUCCESS;
}

void StreamingSocketManagement::resetConnection(ConnectionContext *ctx, CommoTime nextConnTime, bool clearIo)
{
    if (ctx->connType == CONN_TYPE_SSL) {
        ctx->ssl->writeState = SSLConnectionContext::WANT_NONE;
        ctx->ssl->readState = SSLConnectionContext::WANT_NONE;
        if (ctx->ssl->ssl) {
            if (!ctx->ssl->fatallyErrored)
                SSL_shutdown(ctx->ssl->ssl);
            SSL_free(ctx->ssl->ssl);
            ctx->ssl->ssl = NULL;
        }
        ctx->ssl->fatallyErrored = false;
    }
    if (ctx->socket) {
        delete ctx->socket;
        ctx->socket = NULL;
    }
    ctx->retryTime = nextConnTime;
    if (clearIo) {
        while (!ctx->txQueue.empty()) {
            TxQueueItem &txi = ctx->txQueue.back();
            txi.implode();
            ctx->txQueue.pop_back();
        }
        ctx->txQueueProtoVersion = 0;
        ctx->rxBufOffset = 0;
        ctx->rxBufStart = 0;
        ctx->protoState = ConnectionContext::PROTO_XML_NEGOTIATE;
        ctx->protoLen = 0;
        ctx->protoBlockedForResponse = false;
    }
}


void StreamingSocketManagement::addInterfaceStatusListener(
        InterfaceStatusListener* listener)
{
    Lock lock(ifaceListenersMutex);
    ifaceListeners.insert(listener);
}

void StreamingSocketManagement::removeInterfaceStatusListener(
        InterfaceStatusListener* listener)
{
    Lock lock(ifaceListenersMutex);
    ifaceListeners.erase(listener);
}

std::string StreamingSocketManagement::getEndpointString(
        const char *addr, int port, ConnType connType)
{
    std::stringstream ss;
    switch (connType) {
    case CONN_TYPE_SSL:
        ss << "ssl:";
        break;
    case CONN_TYPE_TCP:
        ss << "tcp:";
        break;
    }
    ss << addr;
    ss << ":";
    ss << port;
    return ss.str();
}

void StreamingSocketManagement::fireInterfaceChange(ConnectionContext *ctx, bool up)
{
    Lock lock(ifaceListenersMutex);
    std::set<InterfaceStatusListener *>::iterator iter;
    for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
        InterfaceStatusListener *listener = *iter;
        if (up)
            listener->interfaceUp(ctx);
        else
            listener->interfaceDown(ctx);
    }
}

void StreamingSocketManagement::fireInterfaceErr(ConnectionContext *ctx,
                              netinterfaceenums::NetInterfaceErrorCode errCode)
{
    Lock lock(ifaceListenersMutex);
    std::set<InterfaceStatusListener *>::iterator iter;
    for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
        InterfaceStatusListener *listener = *iter;
        listener->interfaceError(ctx, errCode);
    }
}


/*************************************************************************/
// StreamingSocketManagement public api: misc 

void StreamingSocketManagement::setConnTimeout(float sec)
{
    connTimeoutSec = sec;
}


void StreamingSocketManagement::setMonitor(bool en)
{
    this->monitor = en;
}


/*************************************************************************/
// StreamingSocketManagement public api: SSL config access

void StreamingSocketManagement::configSSLForConnection(std::string streamingEndpoint, SSL_CTX *sslCtx) COMMO_THROW (std::invalid_argument)
{
    ReadLock lock(contextMutex);
    ContextMap::iterator iter = contexts.find(streamingEndpoint);
    if (iter == contexts.end())
        throw std::invalid_argument("Invalid stream endpoint - interface has been removed/disabled");

    ConnectionContext *ctx = iter->second;

    if (ctx->connType != CONN_TYPE_SSL)
        throw std::invalid_argument("Connection is not ssl based");

    SSL_CTX_use_certificate(sslCtx, ctx->ssl->cert);
    SSL_CTX_use_PrivateKey(sslCtx, ctx->ssl->key);
    // Disable ECDH ciphers as demo.atakserver.com fails when they are
    // enabled.  See:  https://bugs.launchpad.net/ubuntu/+source/openssl/+bug/1475228
    // for similar issues with other servers.
    SSL_CTX_set_cipher_list(sslCtx, "DEFAULT:!ECDH");

    // Can't use the cert store in ctx->ssl as it is used internally and they
    // don't share well in our current openssl version (set_cert_store does
    // not increment ref counts!)
    // Make a new one
    X509_STORE *store = X509_STORE_new();
    if (!store)
         throw std::invalid_argument("couldn't init cert store");

    int nCaCerts = sk_X509_num(ctx->ssl->caCerts);
    for (int i = 0; i < nCaCerts; ++i)
         X509_STORE_add_cert(store, sk_X509_value(ctx->ssl->caCerts, i));

    SSL_CTX_set_cert_store(sslCtx, store);
}

bool StreamingSocketManagement::isEndpointSSL(std::string streamingEndpoint) COMMO_THROW (std::invalid_argument)
{
    ReadLock lock(contextMutex);
    ContextMap::iterator iter = contexts.find(streamingEndpoint);
    if (iter == contexts.end())
        throw std::invalid_argument("Invalid stream endpoint - interface has been removed/disabled");

    ConnectionContext *ctx = iter->second;
    return ctx->connType == CONN_TYPE_SSL;
}

NetAddress *StreamingSocketManagement::getAddressForEndpoint(std::string endpoint) COMMO_THROW (std::invalid_argument)
{
    ReadLock lock(contextMutex);
    ContextMap::iterator iter = contexts.find(endpoint);
    if (iter == contexts.end())
        throw std::invalid_argument("Invalid stream endpoint - interface has been removed/disabled");

    ConnectionContext *ctx = iter->second;
    if (ctx->remoteEndpointAddr) {
        return NetAddress::duplicateAddress(ctx->remoteEndpointAddr);
    } else
        throw std::invalid_argument("Invalid stream endpoint - interface has hostname not yet resolved!");
}

/*************************************************************************/
// StreamingSocketManagement public api: cot messaging

void StreamingSocketManagement::sendMessage(
        std::string streamingEndpoint, const CoTMessage *msg)
                COMMO_THROW (std::invalid_argument)
{
    ReadLock lock(contextMutex);
    ContextMap::iterator iter = contexts.find(streamingEndpoint);
    if (iter == contexts.end())
        throw std::invalid_argument("Invalid stream endpoint - interface has been removed/disabled");

    ConnectionContext *ctx = iter->second;

    {
        Lock upLock(upMutex);
        if (upContexts.find(ctx) == upContexts.end())
            throw std::invalid_argument("Specified streaming interface is down");

        CoTMessage *msgCopy = new CoTMessage(*msg);

        //std::string dbgStr((const char *)msgBytes, len);
        //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Stream CoT message to ep %s is: {%s}", streamingEndpoint.c_str(), dbgStr.c_str());
        try {
            ctx->txQueue.push_front(TxQueueItem(ctx, msgCopy, 
                                                ctx->txQueueProtoVersion));
        } catch (std::invalid_argument &e) {
            delete msgCopy;
            throw e;
        }
    }
}

void StreamingSocketManagement::sendBroadcast(
        const CoTMessage *msg, bool ignoreType) COMMO_THROW (std::invalid_argument)
{
    CoTMessageType type = msg->getType();


    ReadLock lock(contextMutex);
    Lock upLock(upMutex);

    ContextSet::iterator iter;
    for (iter = upContexts.begin(); iter != upContexts.end(); ++iter) {
        ConnectionContext *ctx = *iter;
        if (ignoreType || ctx->broadcastCoTTypes.find(type) != 
                                              ctx->broadcastCoTTypes.end()) {
            CoTMessage *msgCopy = new CoTMessage(*msg);
            try {
                ctx->txQueue.push_front(TxQueueItem(ctx, msgCopy, 
                                                    ctx->txQueueProtoVersion));
            } catch (std::invalid_argument &e) {
                delete msgCopy;
                throw e;
            }
        }
    }
}

void StreamingSocketManagement::addStreamingMessageListener(
        StreamingMessageListener* listener)
{
    Lock lock(listenersMutex);
    listeners.insert(listener);
}

void StreamingSocketManagement::removeStreamingMessageListener(
        StreamingMessageListener* listener)
{
    Lock lock(listenersMutex);
    listeners.erase(listener);
}




/*************************************************************************/
// StreamingSocketManagement - ThreadedHandler impl

void StreamingSocketManagement::threadEntry(
        size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        ioThreadProcess();
        break;
    case RX_QUEUE_THREADID:
        recvQueueThreadProcess();
        break;
    case CONN_MGMT_THREADID:
        connectionThreadProcess();
        break;
    }
}

void StreamingSocketManagement::threadStopSignal(
        size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        break;
    case RX_QUEUE_THREADID:
    {
        Lock rxLock(rxQueueMutex);
        rxQueueMonitor.broadcast(rxLock);
        break;
    }
    case CONN_MGMT_THREADID:
        break;
    }
}



/*************************************************************************/
// StreamingSocketManagement - Connection management thread

// Only invoked on connection thread.
// Assumes underlying socket is fully connected.
// Returns true if not ssl or if is ssl and the ssl connection is completed
// Throws exception if in error
bool StreamingSocketManagement::connectionThreadCheckSsl(ConnectionContext *ctx) COMMO_THROW (SocketException)
{
    if (ctx->connType != CONN_TYPE_SSL)
        return true;

    if (!ctx->ssl->ssl) {
        // Need to make ssl context and associate fd
        ctx->ssl->ssl = SSL_new(sslCtx);
        if (!ctx->ssl->ssl)
            throw SocketException(netinterfaceenums::ERR_INTERNAL,
                                  "ssl context creation failed");
        // Set certs and key into the context.
        // These functions use the pointers as-is, no copies
        SSL_use_certificate(ctx->ssl->ssl, ctx->ssl->cert);
        SSL_use_PrivateKey(ctx->ssl->ssl, ctx->ssl->key);

        if (SSL_set_fd(ctx->ssl->ssl, (int)ctx->socket->getFD()) != 1)
            throw SocketException(netinterfaceenums::ERR_INTERNAL,
                                  "associating fd with ssl context failed");
    }

    ERR_clear_error();
    int r = SSL_connect(ctx->ssl->ssl);
    if (r == 1) {
        // openssl's cert verification during connection cannot be made
        // to use an in-memory certificate (only files, and even then seems
        // only globally on SSL_CTX and not per-connection like we need).
        // So instead we verify ourselves here
        // NOTE: we *intentionally* do not check authenticity of peer
        // certificate by CN v. hostname comparison or the like (per
        // Shawn Bisgrove and ATAK sources at time of writing)
        X509 *cert = SSL_get_peer_certificate(ctx->ssl->ssl);
        if (!cert)
            throw SocketException(netinterfaceenums::ERR_CONN_SSL_NO_PEER_CERT,
                                  "server did not provide a certificate");

        bool certOk = ctx->ssl->certChecker->checkCert(cert);
        X509_free(cert);

        if (!certOk) {
            int vResult = ctx->ssl->certChecker->getLastErrorCode();
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Server cert verification failed: %d - check truststore for this connection", vResult);
            throw SocketException(netinterfaceenums::ERR_CONN_SSL_PEER_CERT_NOT_TRUSTED,
                                  "server certificate failed verification - check truststore");
        }

        // Clear writeState now that we are connected!
        ctx->ssl->writeState = SSLConnectionContext::WANT_NONE;

        // If there is an auth document, push it on to the tx queue to get sent
        if (!ctx->ssl->authMessage.empty()) {
            ctx->txQueue.push_front(TxQueueItem(ctx, ctx->ssl->authMessage));
        }

        return true;
    }
    if (r == 0)
        throw SocketException(netinterfaceenums::ERR_CONN_SSL_HANDSHAKE,
                              "fatal error performing ssl handshake");
    else {
        r = SSL_get_error(ctx->ssl->ssl, r);
        // if it changes, flag for connection rebuild
        if (r == SSL_ERROR_WANT_READ || r == SSL_ERROR_WANT_WRITE) {
            SSLConnectionContext::SSLWantState newState;
            switch (r) {
            case SSL_ERROR_WANT_READ:
                newState = SSLConnectionContext::WANT_READ;
                break;
            case SSL_ERROR_WANT_WRITE:
                newState = SSLConnectionContext::WANT_WRITE;
                break;
            }
            if (newState != ctx->ssl->writeState) {
                ctx->ssl->writeState = newState;
                // Flag for select rebuild
                downNeedsRebuild = true;
            }
            return false;
        } else {
            if (r == SSL_ERROR_SSL) {
                char msg[1024];
                ERR_error_string_n(ERR_get_error(), msg, sizeof(msg));
                msg[1023] = '\0';
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error making SSL connection: %s", msg);
                ctx->ssl->fatallyErrored = true;
            } else if (r == SSL_ERROR_SYSCALL) {
                ctx->ssl->fatallyErrored = true;
            }
            throw SocketException(netinterfaceenums::ERR_CONN_SSL_HANDSHAKE,
                                  "unexpected error in ssl handshake");
        }
    }
}

void StreamingSocketManagement::connectionThreadProcess()
{
    ContextSet pendingContexts;
    ContextSet pendingReadContexts;
    ContextSet pendingWriteContexts;
    NetSelector selector;

    while (!threadShouldStop(CONN_MGMT_THREADID)) {
        // Hold this the entire iteration, preventing any of our
        // in-use contexts from being destroyed.
        ReadLock ctxLock(contextMutex);

        ContextSet newlyConnectedCtxs;

        // Run through all down contexts.
        // For each, if no connection in progress and time has come to try
        // again, start the connection attempt.
        ContextSet::iterator ctxIter;
        {
            Lock lock(downMutex);
            for (ctxIter = downContexts.begin(); ctxIter != downContexts.end(); ++ctxIter) {
                ConnectionContext *ctx = *ctxIter;
                CommoTime nowTime = CommoTime::now();

                if (!ctx->socket && nowTime > ctx->retryTime)
                {
                    try {
                        ctx->socket = new TcpSocket(ctx->remoteEndpointAddr->family, false);
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Initiating streaming network connection for %s", ctx->remoteEndpoint.c_str());
                        if (ctx->socket->connect(ctx->remoteEndpointAddr)) {
                            // this connection succeeded immediately
                            // no need to rebuild just yet,
                            // just note as connected.
                            if (connectionThreadCheckSsl(ctx))
                                newlyConnectedCtxs.insert(ctx);
                        } else {
                            // Flag for rebuilding our pending socket set
                            downNeedsRebuild = true;
                            // Set connection timeout
                            ctx->retryTime = nowTime + connTimeoutSec;
                        }
                    } catch (SocketException &e) {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_VERBOSE, "streaming socket connection initiation failed (%s); will retry", e.what());
                        fireInterfaceErr(ctx, e.errCode);
                        resetConnection(ctx, nowTime + CONN_RETRY_SECONDS, false);
                    }
                }
            }

            if (downNeedsRebuild) {
                std::vector<Socket *> pendingSockets;
                std::vector<Socket *> pendingConnSockets;
                std::vector<Socket *> pendingReadSockets;
                pendingContexts.clear();
                pendingWriteContexts.clear();
                pendingReadContexts.clear();

                for (ctxIter = downContexts.begin(); ctxIter != downContexts.end(); ++ctxIter) {
                    ConnectionContext *ctx = *ctxIter;
                    if (ctx->socket) {
                        if (ctx->connType == CONN_TYPE_SSL && ctx->ssl->writeState == SSLConnectionContext::WANT_READ) {
                            // Place on to read/write context list
                            pendingReadSockets.push_back(ctx->socket);
                            pendingReadContexts.insert(ctx);
                        } else {
                            if (ctx->connType == CONN_TYPE_SSL && ctx->ssl->writeState == SSLConnectionContext::WANT_WRITE)
                                pendingSockets.push_back(ctx->socket);
                            else
                                pendingConnSockets.push_back(ctx->socket);
                            pendingWriteContexts.insert(ctx);
                        }
                        pendingContexts.insert(ctx);
                    }
                }
                selector.setSockets(&pendingReadSockets, &pendingSockets, &pendingConnSockets);
                downNeedsRebuild = false;
            }
        }

        // If we have sockets with pending connections, select on them to see
        // if they complete or error. However, skip past this and process
        // any immediately connected sockets from above if we have such.
        if (newlyConnectedCtxs.empty()) {
            try {
                if (!selector.doSelect(500)) {
                    // Timed out
                    CommoTime nowTime = CommoTime::now();
                    for (ctxIter = pendingContexts.begin(); ctxIter != pendingContexts.end(); ++ctxIter) {
                        ConnectionContext *ctx = *ctxIter;
                        bool stillConnecting = ctx->connType != CONN_TYPE_SSL || ctx->ssl->writeState == SSLConnectionContext::WANT_NONE;
                        if (nowTime > ctx->retryTime) {
                            if (stillConnecting)
                                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error trying to make network connection for %s - will retry (connection timed out)", ctx->remoteEndpoint.c_str());
                            else
                                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error trying to make network connection for %s - will retry (SSL handshake following connection timed out)", ctx->remoteEndpoint.c_str());
                            fireInterfaceErr(ctx, netinterfaceenums::ERR_CONN_TIMEOUT);
                            resetConnection(ctx, nowTime + CONN_RETRY_SECONDS, false);
                            downNeedsRebuild = true;
                        }
                    }
                    continue;
                }
            } catch (SocketException &) {
                // Weird to get an error here, unless your name is Solaris
                // which will apparently error on select() for sockets in error
                // state.  Anyway, if this happens restart all of them
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error from tcp connection select() - retrying connections");
                CommoTime rTime = CommoTime::now() + CONN_RETRY_SECONDS;
                for (ctxIter = pendingContexts.begin(); ctxIter != pendingContexts.end(); ++ctxIter) {
                    ConnectionContext *ctx = *ctxIter;
                    fireInterfaceErr(ctx, netinterfaceenums::ERR_CONN_OTHER);
                    resetConnection(ctx, rTime, false);
                }
                downNeedsRebuild = true;
                continue;
            }

            // Something is ready; check for writes first as this
            // is most common case
            CommoTime nowTime = CommoTime::now();
            for (ctxIter = pendingWriteContexts.begin(); ctxIter != pendingWriteContexts.end(); ++ctxIter) {
                ConnectionContext *ctx = *ctxIter;
                bool stillConnecting = ctx->connType != CONN_TYPE_SSL || ctx->ssl->writeState == SSLConnectionContext::WANT_NONE;

                try {
                    if (selector.getLastConnectState(ctx->socket) != NetSelector::WRITABLE) {
                        if (nowTime > ctx->retryTime) {
                            if (stillConnecting)
                                throw SocketException(netinterfaceenums::ERR_CONN_TIMEOUT,
                                                  "tcp connection timed out");
                            else
                                throw SocketException(netinterfaceenums::ERR_CONN_TIMEOUT,
                                                  "SSL handshake following connection timed out");
                        }
                        continue;
                    }

                    if (stillConnecting) {
                        netinterfaceenums::NetInterfaceErrorCode errCode;
                        if (ctx->socket->isSocketErrored(&errCode)) {
                            throw SocketException(errCode,
                                                  "tcp socket connection error");
                        } else if (connectionThreadCheckSsl(ctx))
                            newlyConnectedCtxs.insert(ctx);
                    } else if (ctx->ssl->writeState == SSLConnectionContext::WANT_WRITE) {
                        // Is ssl and wanted write that is now fulfilled.
                        if (connectionThreadCheckSsl(ctx))
                            newlyConnectedCtxs.insert(ctx);
                        // else it now wants more writing or reading; all handled
                        // by CheckSsl().
                    }
                } catch (SocketException &e) {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error trying to make network connection for %s - will retry (%s)", ctx->remoteEndpoint.c_str(), e.what());
                    fireInterfaceErr(ctx, e.errCode);
                    resetConnection(ctx, nowTime + CONN_RETRY_SECONDS, false);
                    downNeedsRebuild = true;
                }
            }

            for (ctxIter = pendingReadContexts.begin(); ctxIter != pendingReadContexts.end(); ++ctxIter) {
                ConnectionContext *ctx = *ctxIter;
                try {
                    if (selector.getLastReadState(ctx->socket) != NetSelector::READABLE) {
                        if (nowTime > ctx->retryTime)
                            throw SocketException(netinterfaceenums::ERR_CONN_TIMEOUT,
                                                  "SSL handshake following connection timed out waiting on read");
                        continue;
                    }

                    // Only ssl connections go to read pending!
                    // Just check the connect status.
                    if (connectionThreadCheckSsl(ctx))
                        newlyConnectedCtxs.insert(ctx);
                } catch (SocketException &e) {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error trying to make network connection for %s - will retry (%s)", ctx->remoteEndpoint.c_str(), e.what());
                    fireInterfaceErr(ctx, e.errCode);
                    resetConnection(ctx, CommoTime::now() + CONN_RETRY_SECONDS, false);
                    downNeedsRebuild = true;
                }
            }

        }

        if (!newlyConnectedCtxs.empty()) {
            {
                Lock lock(downMutex);
                // Pull each from the down list
                for (ctxIter = newlyConnectedCtxs.begin(); ctxIter != newlyConnectedCtxs.end(); ++ctxIter)
                    downContexts.erase(*ctxIter);
            }

            for (ctxIter = newlyConnectedCtxs.begin(); ctxIter != newlyConnectedCtxs.end(); ++ctxIter)
                fireInterfaceChange(*ctxIter, true);

            {
                Lock lock(upMutex);
                CommoTime now = CommoTime::now();
                for (ctxIter = newlyConnectedCtxs.begin(); ctxIter != newlyConnectedCtxs.end(); ++ctxIter) {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Stream connection to %s is up!", (*ctxIter)->remoteEndpoint.c_str());
                    (*ctxIter)->retryTime = now;
                    (*ctxIter)->lastRxTime = now;
                    (*ctxIter)->protoTimeout = now + PROTO_TIMEOUT_SECONDS;
                    upContexts.insert(*ctxIter);
                }
                ioNeedsRebuild = true;
            }
            downNeedsRebuild = true;
        }



    }
}



/*************************************************************************/
// StreamingSocketManagement - I/O thread

size_t StreamingSocketManagement::ioThreadSslRead(ConnectionContext *ctx) COMMO_THROW (SocketException)
{
    ERR_clear_error();
    int n = SSL_read(ctx->ssl->ssl, ctx->rxBuf + ctx->rxBufOffset, (int)(ConnectionContext::rxBufSize - ctx->rxBufOffset));
    SSLConnectionContext::SSLWantState newState = SSLConnectionContext::WANT_NONE;
    if (n <= 0) {
        int n0 = n;
        n = SSL_get_error(ctx->ssl->ssl, n);

        switch (n) {
        case SSL_ERROR_WANT_READ:
            newState = SSLConnectionContext::WANT_READ;
            break;
        case SSL_ERROR_WANT_WRITE:
            newState = SSLConnectionContext::WANT_WRITE;
            break;
        case SSL_ERROR_SSL:
        case SSL_ERROR_SYSCALL:
            ctx->ssl->fatallyErrored = true;
            // Intentionally fall-through
        default:
            InternalUtils::logprintf(logger, 
                    CommoLogger::LEVEL_ERROR,
                    "SSL Read fatal error - %d, %d", 
                    n0,
                    n);
            throw SocketException();
        }

        n = 0;
    }
    ctx->ssl->readState = newState;

    return n;
}

size_t StreamingSocketManagement::ioThreadSslWrite(ConnectionContext *ctx, const uint8_t *data, size_t dataLen) COMMO_THROW (SocketException)
{
    ERR_clear_error();
    int n = SSL_write(ctx->ssl->ssl, data, (int)dataLen);
    SSLConnectionContext::SSLWantState newState = SSLConnectionContext::WANT_NONE;
    if (n <= 0) {
        int n0 = n;
        n = SSL_get_error(ctx->ssl->ssl, n);

        switch (n) {
        case SSL_ERROR_WANT_READ:
            newState = SSLConnectionContext::WANT_READ;
            break;
        case SSL_ERROR_WANT_WRITE:
            newState = SSLConnectionContext::WANT_WRITE;
            break;
        case SSL_ERROR_SSL:
        case SSL_ERROR_SYSCALL:
            ctx->ssl->fatallyErrored = true;
            // Intentionally fall-through
        default:
            InternalUtils::logprintf(logger, 
                    CommoLogger::LEVEL_ERROR,
                    "SSL Write fatal error - %d, %d", 
                    n0,
                    n);
            throw SocketException();
        }

        n = 0;
    }
    ctx->ssl->writeState = newState;

    return n;
}

size_t StreamingSocketManagement::ioThreadWrite(ConnectionContext *ctx, const uint8_t *data, size_t n) COMMO_THROW (SocketException)
{
    return ctx->socket->write(data, n);
}


void StreamingSocketManagement::ioThreadProcess()
{
    // RX set is all "up" ifaces  << rebuild on iface changes
    // TX set is all "up" ifaces that are full wqueue << rebuilds on iface changes or tx status change
    ContextSet rxSet;
    std::vector<Socket *> rxSockets;
    ContextSet txSet;
    std::vector<Socket *> txSockets;
    NetSelector selector;

    while (!threadShouldStop(IO_THREADID)) {
        ReadLock ctxLock(contextMutex);

        ContextSet errorSet;
        ContextSet::iterator ctxIter;
        ConnectionContext *ctx;

        // Set if txSet has changes and tx socket set needs rebuilding
        bool txNeedsRebuild = false;

        {
            Lock upLock(upMutex);

            // If list of contexts has externally changed, don't use existing
            // socket sets or txSet.  Just rebuild everything
            if (ioNeedsRebuild) {
                txSet.clear();
                rxSet.clear();
                rxSockets.clear();
                txNeedsRebuild = true;
            }

            for (ctxIter = upContexts.begin(); ctxIter != upContexts.end(); ++ctxIter) {
                ctx = *ctxIter;
                bool inTxSelection = txSet.find(ctx) != txSet.end();

                size_t (StreamingSocketManagement::*writeFunc)(ConnectionContext *ctx, const uint8_t *, size_t);
                bool isSSL = ctx->connType == CONN_TYPE_SSL;
                bool doTx;
                if (isSSL) {
                    doTx = ctx->ssl->writeState == SSLConnectionContext::WANT_NONE ||
                            (!ioNeedsRebuild && ((ctx->ssl->writeState == SSLConnectionContext::WANT_WRITE && selector.getLastWriteState(ctx->socket) == NetSelector::WRITABLE) ||
                            (ctx->ssl->writeState == SSLConnectionContext::WANT_READ && selector.getLastReadState(ctx->socket) == NetSelector::READABLE)));
                    writeFunc = &StreamingSocketManagement::ioThreadSslWrite;
                } else {
                    doTx = !inTxSelection || selector.getLastWriteState(ctx->socket) == NetSelector::WRITABLE;
                    writeFunc = &StreamingSocketManagement::ioThreadWrite;
                }

                try {
                    if (doTx) {
                        // Tx might have room
                        while (!ctx->txQueue.empty() && !ctx->protoBlockedForResponse) {
                            TxQueueItem &item = ctx->txQueue.back();
                            size_t r = item.dataLen - item.bytesSent;
                            while (r > 0) {
                                size_t w = (this->*writeFunc)(ctx, item.data + item.bytesSent, r);
                                if (!w)
                                    break;
                                r -= w;
                                item.bytesSent += w;
                            }
                            if (r) {
                                // socket tx queue is full (non-SSL) or
                                // some input or output needed (SSL)
                                // before finishing this item.
                                // break out and leave queue item intact
                                break;
                            } else {
                                if (item.protoSwapRequest)
                                    // No more sending on this guy until
                                    // we get a response to this proto swap
                                    // request (in rx handling)
                                    ctx->protoBlockedForResponse = true;
                                item.implode();
                                ctx->txQueue.pop_back();
                            }
                        }
                    }

                    if (!inTxSelection) {
                        if ((!isSSL && !ctx->txQueue.empty() && !ctx->protoBlockedForResponse) ||
                                (isSSL &&
                                    (ctx->ssl->writeState == SSLConnectionContext::WANT_WRITE ||
                                     ctx->ssl->readState == SSLConnectionContext::WANT_WRITE))) {
                            txSet.insert(ctx);
                            txNeedsRebuild = true;
                        }
                    } else {
                        if ((!isSSL && 
                              (ctx->txQueue.empty() || 
                               ctx->protoBlockedForResponse)) ||
                            (isSSL &&
                               ctx->ssl->writeState != SSLConnectionContext::WANT_WRITE &&
                               ctx->ssl->readState != SSLConnectionContext::WANT_WRITE)) {
                            // Sent everything - this guy is in the clear
                            txSet.erase(ctx);
                            txNeedsRebuild = true;
                        }
                    }
                } catch (SocketException &) {
                    // Socket error. Queue for going to down state.
                    fireInterfaceErr(ctx, netinterfaceenums::ERR_IO);
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error sending tcp data");
                    errorSet.insert(ctx);
                }

                if (ioNeedsRebuild) {
                    rxSockets.push_back(ctx->socket);
                    rxSet.insert(ctx);
                }
            }

            ioNeedsRebuild = false;
        }

        if (errorSet.empty() && txNeedsRebuild) {
            txSockets.clear();
            for (ctxIter = txSet.begin(); ctxIter != txSet.end(); ++ctxIter) {
                ctx = *ctxIter;
                txSockets.push_back(ctx->socket);
            }
            selector.setSockets(&rxSockets, &txSockets);
            txNeedsRebuild = false;
        }

        if (errorSet.empty()) {
            try {
                selector.doSelect(100);
            } catch (SocketException &) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error from tcp io select()?");
                fireInterfaceErr(ctx, netinterfaceenums::ERR_OTHER);
                errorSet.insert(rxSet.begin(), rxSet.end());
            }
        }

        // Only deal with RX. TX will be dealt with later.
        if (errorSet.empty()) {
            CommoTime now = CommoTime::now();
            for (ctxIter = rxSet.begin(); ctxIter != rxSet.end(); ++ctxIter) {
                ctx = *ctxIter;
                try {
                    bool foundSomething = false;
                    if (ctx->connType == CONN_TYPE_SSL) {
                        if ((ctx->ssl->readState == SSLConnectionContext::WANT_WRITE && selector.getLastWriteState(ctx->socket) == NetSelector::WRITABLE) || (ctx->ssl->readState != SSLConnectionContext::WANT_WRITE && selector.getLastReadState(ctx->socket) == NetSelector::READABLE)) {
                            while (true) {
                                size_t r = ioThreadSslRead(ctx);
                                if (!r)
                                    break;
                                bool f = scanStreamData(ctx, r);
                                foundSomething = foundSomething || f;
                            }
                        }
                    } else {
                        if (selector.getLastReadState(ctx->socket) == NetSelector::READABLE) {
                            // Read until there is nothing available
                            while (true) {
                                size_t r = ctx->socket->read(ctx->rxBuf + ctx->rxBufOffset, ConnectionContext::rxBufSize - ctx->rxBufOffset);
                                if (!r)
                                    break;
                                bool f = scanStreamData(ctx, r);
                                foundSomething = foundSomething || f;
                            }
                        }
                    }

                    if (foundSomething) {
                        ctx->lastRxTime = now;
                    } else if (monitor) {
                        float d = now.minus(ctx->lastRxTime);
                        if (d > RX_TIMEOUT_SECONDS) {
                            InternalUtils::logprintf(logger, 
                                    CommoLogger::LEVEL_ERROR,
                                    "No tcp data received from %s in %d seconds; reconnecting", 
                                    ctx->remoteEndpoint.c_str(),
                                    (int)d);
                            fireInterfaceErr(ctx, netinterfaceenums::ERR_IO_RX_DATA_TIMEOUT);
                            errorSet.insert(ctx);
                        } else if (d > RX_STALE_SECONDS && now > ctx->retryTime) {
                            InternalUtils::logprintf(logger, 
                                    CommoLogger::LEVEL_DEBUG,
                                    "No tcp data received from %s in %d seconds; sending ping", 
                                    ctx->remoteEndpoint.c_str(),
                                    (int)d);
                            sendPing(ctx);
                            ctx->retryTime = now + RX_STALE_PING_SECONDS;
                        }
                    }

                    if (!checkProtoTimeout(ctx, now)) {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "TakServer %s Proto Negotiate: timed out waiting for response from server", ctx->remoteEndpoint.c_str());
                        fireInterfaceErr(ctx, netinterfaceenums::ERR_OTHER);
                        errorSet.insert(ctx);
                    }

                } catch (SocketException &) {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Error receiving tcp data from %s", ctx->remoteEndpoint.c_str());
                    fireInterfaceErr(ctx, netinterfaceenums::ERR_IO);
                    errorSet.insert(ctx);
                }
            }
        }

        if (!errorSet.empty()) {
            {
                Lock lock(upMutex);
                // remove all in errorSet from up, killing sockets and
                // expunging txQueue
                for (ctxIter = errorSet.begin(); ctxIter != errorSet.end(); ctxIter++) {
                    ctx = *ctxIter;
                    resetConnection(ctx, CommoTime::now() + CONN_RETRY_SECONDS, true);
                    upContexts.erase(ctx);
                }
            }

            for (ctxIter = errorSet.begin(); ctxIter != errorSet.end(); ctxIter++)
                fireInterfaceChange(*ctxIter, false);

            {
                // Move to down list
                Lock lock(downMutex);
                downContexts.insert(errorSet.begin(), errorSet.end());
            }
            // Make certain to reset everything!
            ioNeedsRebuild = true;
        }

    }
}

void StreamingSocketManagement::sendPing(ConnectionContext *ctx)
{
    Lock uplock(upMutex);
    CoTMessage *msg = new CoTMessage(logger, myPingUid);
    try {
        ctx->txQueue.push_front(TxQueueItem(ctx, msg, ctx->txQueueProtoVersion));
    } catch (std::invalid_argument &) {
        delete msg;
    }
}

void StreamingSocketManagement::convertTxToProtoVersion(ConnectionContext *ctx,
                                                        int protoVersion)
{
    Lock upLock(upMutex);
    
    TxQueue::iterator iter;
    for (iter = ctx->txQueue.begin(); iter != ctx->txQueue.end(); )
    {
        try {
            iter->reserialize(protoVersion);
            iter++;
        } catch (std::invalid_argument &ex) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                "Unexpected error reserializing message! %s", ex.what());
            TxQueue::iterator curIter = iter;
            iter++;
            
            ctx->txQueue.erase(curIter);
        }
    }
    ctx->txQueueProtoVersion = protoVersion;

}

bool StreamingSocketManagement::checkProtoTimeout(ConnectionContext *ctx,
                                                  const CommoTime &now)
{
    if (ctx->protoState > ConnectionContext::PROTO_WAITRESPONSE)
        return true;
    if (now > ctx->protoTimeout) {
        if (ctx->protoState == ConnectionContext::PROTO_WAITRESPONSE) {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                "TakServer %s Proto Negotiate: Timed out waiting for protocol negotiation response, reconnecting...", ctx->remoteEndpoint.c_str());
            return false;
        } else { // PROTO_XML_NEGOTIATE
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                "TakServer %s Proto Negotiate: Timed out waiting for protocol version support message - continuing as XML only", ctx->remoteEndpoint.c_str());
            ctx->protoState = ConnectionContext::PROTO_XML_ONLY;
        }
    }
    return true;
}

bool StreamingSocketManagement::protoNegotiation(ConnectionContext *ctx,
                                                 CoTMessage *msg)
{
    bool ret = false;
    TakControlType t = msg->getTakControlType();
    switch (t) {
      case TYPE_SUPPORT:
        if (ctx->protoState == ConnectionContext::PROTO_XML_NEGOTIATE) {
            std::set<int> vs = msg->getTakControlSupportedVersions();
            std::string vstring;
            for (std::set<int>::iterator iter = vs.begin(); iter != vs.end(); ++iter) {
                vstring += InternalUtils::intToString(*iter);
                vstring += " ";
            }
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                "TakServer %s Proto Negotiate: Server supports protocol versions: %s", ctx->remoteEndpoint.c_str(), vstring.c_str());
            if (vs.count(1)) {
                // We support version 1 - tell server
                {
                    Lock uplock(upMutex);
                    CoTMessage *rmsg = new CoTMessage(logger, 
                                                      msg->getEventUid(), 1);
                    try {
                        TxQueueItem qi(ctx, rmsg);
                        qi.protoSwapRequest = true;
                        ctx->txQueue.push_front(qi);
                    } catch (std::invalid_argument &) {
                        delete rmsg;
                        InternalUtils::logprintf(logger,
                            CommoLogger::LEVEL_ERROR,
                            "TakServer %s Proto Negotiate: Unexpected error when serializing protocol swap request", ctx->remoteEndpoint.c_str());
                        break;
                    }
                }

                ctx->protoState = ConnectionContext::PROTO_WAITRESPONSE;
                ctx->protoTimeout = CommoTime::now() + PROTO_TIMEOUT_SECONDS;
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                    "TakServer %s Proto Negotiate: Requesting transition to protocol version 1", ctx->remoteEndpoint.c_str());
            }
        } else {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING,
                "TakServer %s Proto Negotiate: TakControl message advertising version support received after already being received, ignoring", ctx->remoteEndpoint.c_str());
        }
        break;
      case TYPE_RESPONSE:
        if (ctx->protoState == ConnectionContext::PROTO_WAITRESPONSE) {
            if (msg->getTakControlResponseStatus()) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                    "TakServer %s Proto Negotiate: Protocol negotiation request accepted, swapping proto version", ctx->remoteEndpoint.c_str());
                ctx->protoState = ConnectionContext::PROTO_HDR_MAGIC;
                ret = true;
                convertTxToProtoVersion(ctx, 1);

            } else {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                    "TakServer %s Proto Negotiate: protocol negotiation request denied, using xml only", ctx->remoteEndpoint.c_str());
                ctx->protoState = ConnectionContext::PROTO_XML_ONLY;
            }
            ctx->protoBlockedForResponse = false;
            
        } else {
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING,
                "TakServer %s Proto Negotiate: TakControl response message received when we didn't send a request - ignoring!", ctx->remoteEndpoint.c_str());
        }
        break;
      default:
        break;
    }

    return ret;
}

bool StreamingSocketManagement::dispatchRxMessage(ConnectionContext *ctx,
                                               size_t len, bool isXml)
{
    bool ret = false;

    // Convert the data to a CoTMessage
    try {
        TakMessage takmsg(logger, ctx->rxBuf + ctx->rxBufStart,
                             len, isXml, !isXml);
        CoTMessage *msg = takmsg.releaseCoTMessage();
        if (!msg)
            return ret;
        
        if (isXml && ctx->protoState != ConnectionContext::PROTO_XML_ONLY) {
            ret = protoNegotiation(ctx, msg);
        }
        
        // Discard if it is a pong or control message, else post it
        if (!msg->isPong() && msg->getTakControlType() == TakControlType::TYPE_NONE) {
            Lock qLock(rxQueueMutex);

            rxQueue.push_back(RxQueueItem(ctx, msg));
            rxQueueMonitor.broadcast(qLock);
        } else {
            delete msg;
        }
    } catch (std::invalid_argument &e) {
        std::string s((const char *)ctx->rxBuf + ctx->rxBufStart, len);
        CommoLogger::ParsingDetail detail{ ctx->rxBuf + ctx->rxBufStart, len, e.what() == NULL ? "" : e.what(), ctx->remoteEndpoint.c_str() };
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, CommoLogger::TYPE_PARSING, &detail, "Invalid CoT message received from stream: {%s} -- %s", s.c_str(), e.what() == NULL ? "" : e.what());
    }
    
    return ret;
}

bool StreamingSocketManagement::scanStreamData(
        ConnectionContext *ctx, size_t nNewBytes)
{
    bool foundSomething = false;
    size_t scanStart;
    bool protoIsXml = ctx->protoState <= ConnectionContext::PROTO_XML_ONLY;
    if (protoIsXml && (ctx->rxBufOffset - ctx->rxBufStart) > MESSAGE_END_TOKEN_LEN)
        scanStart = ctx->rxBufOffset - MESSAGE_END_TOKEN_LEN;
    else
        scanStart = ctx->rxBufStart;
    size_t scanEnd = ctx->rxBufOffset + nNewBytes;

    if (protoIsXml) {
        size_t tokenIdx = 0;
        for (size_t i = scanStart; i < scanEnd; ++i) {
            if (ctx->rxBuf[i] == MESSAGE_END_TOKEN[tokenIdx]) {
                if (++tokenIdx == MESSAGE_END_TOKEN_LEN) {
                    // msg complete - dispatch it and start new search
                    size_t msgLen = i - ctx->rxBufStart + 1;
                    bool protoSwap = dispatchRxMessage(ctx, msgLen, true);
                    ctx->rxBufStart = i + 1;
                    foundSomething = true;
                    if (protoSwap) {
                        // transition to protobuf immediately
                        // State already updated by dispatch
                        protoIsXml = false;
                        scanStart = ctx->rxBufStart;
                        break;
                    }

                    tokenIdx = 0;
                }
            } else
                tokenIdx = 0;
        }
    }

    if (!protoIsXml) {
        for (size_t i = scanStart; i < scanEnd; ) {
            switch (ctx->protoState) {
                case ConnectionContext::PROTO_HDR_MAGIC:
                    if (ctx->rxBuf[i] == 0xbf) {
                        ctx->protoState = ConnectionContext::PROTO_HDR_LEN;
                        ctx->rxBufStart = i + 1;
                        if (ctx->protoMagicSearchCount != 0) {
                            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                                "Found magic byte 0xBF in server stream after skipping %u bytes", ctx->protoMagicSearchCount);
                            ctx->protoMagicSearchCount = 0;
                        }
                    } else {
                        if (ctx->protoMagicSearchCount == 0) {
                            InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING,
                                "Found %X instead of magic byte 0xBF in server stream - skipping until next magic byte!", ((int)ctx->rxBuf[i]) & 0xFF);
                        }
                        ctx->protoMagicSearchCount++;
                    }

                    i++;
                    break;
                case ConnectionContext::PROTO_HDR_LEN:
                    if ((ctx->rxBuf[i] & 0x80) == 0) {
                        // End of varint
                        size_t n = scanEnd - ctx->rxBufStart;
                        try {
                            uint64_t vlen = InternalUtils::varintDecode(
                                    ctx->rxBuf + ctx->rxBufStart, &n);
                            // Second condition is just a sanity check
                            if (vlen > ConnectionContext::rxBufSize || (ctx->rxBufStart + n) != (i + 1)) {
                                ctx->protoState = ConnectionContext::PROTO_HDR_MAGIC;
                                ctx->rxBufStart = i + 1;
                                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                                    "Protobuf length varint encoding incorrect or too large (%" PRIu64 ") - skipping and rescanning for header", vlen);
                            } else {
                                //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                                //    "Protobuf length %d in server stream", (unsigned int)vlen);
                                ctx->protoState = ConnectionContext::PROTO_DATA;
                                ctx->protoLen = (size_t)vlen;
                                ctx->rxBufStart = i + 1;
                            }
                        } catch (std::invalid_argument &) {
                            ctx->protoState = ConnectionContext::PROTO_HDR_MAGIC;
                            ctx->rxBufStart = i + 1;
                        }
                        
                    } // else still looking for end of varint
                    
                    // All paths consume 1 byte
                    i++;
                    break;
                case ConnectionContext::PROTO_DATA:
                    // See if we have enough data in buffer to complete
                    if (scanEnd - i >= ctx->protoLen) {
                        // msg complete - dispatch it and start new search
                        dispatchRxMessage(ctx, ctx->protoLen, false);

                        i += ctx->protoLen;
                        ctx->rxBufStart = i;
                        ctx->protoState = ConnectionContext::PROTO_HDR_MAGIC;
                        foundSomething = true;
                        
                    } else {
                        // Not enough data - shortcut to end of new data
                        i = scanEnd;
                    }
                    break;
                case ConnectionContext::PROTO_XML_NEGOTIATE:
                case ConnectionContext::PROTO_WAITRESPONSE:
                case ConnectionContext::PROTO_XML_ONLY:
                    // Nothing to do here - this doesn't happen
                    // Here to appease compiler
                    break;
            }
        }
    }
    ctx->rxBufOffset = scanEnd;

    if (ctx->rxBufOffset == ctx->rxBufSize) {
        // Full buffer - need to make more room
        if (ctx->rxBufStart == 0) {
            // Have to dump some data.
            ctx->rxBufOffset = ctx->rxBufSize / 2;
            memcpy(ctx->rxBuf, ctx->rxBuf + ctx->rxBufOffset, ctx->rxBufOffset);
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                                     "Message scanning buffer full - had to discard some streaming data!");

            // Start proto scanning over since we dumped data...
            if (!protoIsXml)
                ctx->protoState = ConnectionContext::PROTO_HDR_MAGIC;
        } else {
            // Shift down
            ctx->rxBufOffset -= ctx->rxBufStart;
            memmove(ctx->rxBuf, ctx->rxBuf + ctx->rxBufStart, ctx->rxBufOffset);
            ctx->rxBufStart = 0;
        }
    }
    return foundSomething;
}


/*************************************************************************/
// StreamingSocketManagement - Rx queue processing thread

void StreamingSocketManagement::recvQueueThreadProcess()
{
    while (!threadShouldStop(RX_QUEUE_THREADID)) {
        RxQueueItem qitem;
        std::string endpoint;
        {
            Lock rxLock(rxQueueMutex);
            if (rxQueue.empty()) {
                rxQueueMonitor.wait(rxLock);
                continue;
            }

            qitem = rxQueue.back();
            rxQueue.pop_back();

            // Copy out the context identifier while holding the queue lock
            endpoint = qitem.ctx->remoteEndpoint;
        }

        // Override any endpoint in the message with the streaming
        // endpoint indicator.  This is holdover from old ATAK
        // CoTService, but its nice because less chance for clients
        // to use the wrong thing if sending outside the bounds of
        // this library
        qitem.msg->setEndpoint(ENDPOINT_STREAMING, "");
        
        Lock lock(listenersMutex);
        std::set<StreamingMessageListener *>::iterator iter;
        for (iter = listeners.begin(); iter != listeners.end(); ++iter) {
            StreamingMessageListener *listener = *iter;
            listener->streamingMessageReceived(endpoint, qitem.msg);
        }
        qitem.implode();
    }
}


/*************************************************************************/
// StreamingSocketManagement - name resolution processing


void StreamingSocketManagement::resolutionAttemptFailed(
                             ResolverQueue::Request *id,
                             const std::string &hostAddr)
{
    // Acquire the lock and be certain our context is still valid.
    // If so, send out an error indication.
    {
        ReadLock ctxLock(contextMutex);
        ConnectionContext *resolveMeContext = NULL;

        ResolverMap::iterator iter = resolutionContexts.find(id);
        if (iter != resolutionContexts.end()) {
            // Still valid
            resolveMeContext = iter->second;
            
            fireInterfaceErr(resolveMeContext, netinterfaceenums::ERR_CONN_NAME_RES_FAILED);
        } // else no longer valid, so just ignore it
    }
}


bool StreamingSocketManagement::resolutionComplete(
                                ResolverQueue::Request *request,
                                const std::string &hostAddr,
                                NetAddress *addr)
{
    if (addr) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Success resolving tak server address: %s", hostAddr.c_str());
    } else {
        // Should never happen!
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Resolver returned null on infinite retries - this is a bug!");
        return false;
    }
    
    // Acquire the lock and be certain our context is still valid.
    // If so, move it to the connection thread's "down" list.
    // If not, just forget it.
    // We grab the global context lock in read mode because the
    // context will be moved between lists.
    {
        ReadLock ctxLock(contextMutex);
        ConnectionContext *resolveMeContext = NULL;

        ResolverMap::iterator iter = resolutionContexts.find(request);
        if (iter != resolutionContexts.end()) {
            // Still valid
            resolveMeContext = iter->second;
            // Zero this so that the connection will be tried immediately.
            resolveMeContext->retryTime = CommoTime::ZERO_TIME;
            resolveMeContext->remoteEndpointAddr = addr->deriveNewAddress(resolveMeContext->remotePort);
            // Pull it from the resolver list to be moved to connection
            resolutionContexts.erase(iter);

            // If the context is valid still, move it to down list
            {
                Lock lock(downMutex);
                resolveMeContext->resolverRequest = NULL;
                downContexts.insert(resolveMeContext);
            }
        } // else no longer valid, so just ignore it
        
    }
    // We don't need the result, return false
    return false;
}



/*************************************************************************/
// Internal utility classes

StreamingSocketManagement::TxQueueItem::TxQueueItem() :
        ctx(NULL), msg(NULL), data(NULL), dataLen(0), 
        bytesSent(0), protoSwapRequest(false)
{
}

StreamingSocketManagement::TxQueueItem::TxQueueItem(
        ConnectionContext *ctx, CoTMessage *msg, int protoVersion)
            COMMO_THROW (std::invalid_argument) :
                ctx(ctx), msg(msg), data(NULL), dataLen(0), bytesSent(0),
                protoSwapRequest(false)
{
    reserialize(protoVersion);
}


StreamingSocketManagement::TxQueueItem::TxQueueItem(
        ConnectionContext *ctx, const std::string &rawMessage) :
            ctx(ctx), msg(NULL), data(NULL), dataLen(0), bytesSent(0),
            protoSwapRequest(false)
{
    dataLen = rawMessage.length();
    uint8_t *ndata = new uint8_t[dataLen];
    memcpy(ndata, rawMessage.c_str(), dataLen);
    data = ndata;
}

StreamingSocketManagement::TxQueueItem::~TxQueueItem()
{
}

void StreamingSocketManagement::TxQueueItem::implode()
{
    delete[] data;
    data = NULL;
    delete msg;
    msg = NULL;
}

void StreamingSocketManagement::TxQueueItem::reserialize(int protoVersion)
                                         COMMO_THROW (std::invalid_argument)
{
    if (!msg)
        throw std::invalid_argument("Cannot reserialize raw message");

    uint8_t *ndata;
    size_t len;
    if (!protoVersion)
        len = msg->serialize(&ndata);
    else {
        TakMessage tmsg(msg->getLogger(), msg, NULL);
        len = tmsg.serializeAsProtobuf(protoVersion, &ndata, 
                                       TakMessage::HEADER_LENGTH,
                                       true, false);
    }

    delete data;
    data = ndata;
    dataLen = len;
}


StreamingSocketManagement::RxQueueItem::RxQueueItem() :
        ctx(NULL), msg()
{
}

StreamingSocketManagement::RxQueueItem::RxQueueItem(
        ConnectionContext *ctx, CoTMessage *msg) :
                ctx(ctx), msg(msg)
{
}

StreamingSocketManagement::RxQueueItem::~RxQueueItem()
{
}

void StreamingSocketManagement::RxQueueItem::implode()
{
    delete msg;
    msg = NULL;
}

StreamingSocketManagement::ConnectionContext::ConnectionContext(
        const std::string &epString, const std::string &epAddrString,
        unsigned short int port, NetAddress *ep, ConnType type,
        SSLConnectionContext *ssl) :
        StreamingNetInterface(copyString(epString), epString.length()),
        remoteEndpoint(epString), remoteEndpointAddrString(epAddrString),
        remotePort(port),
        remoteEndpointAddr(ep), broadcastCoTTypes(),
        connType(type), ssl(ssl),
        socket(NULL), resolverRequest(NULL), retryTime(CommoTime::ZERO_TIME),
        lastRxTime(CommoTime::ZERO_TIME),
        txQueue(), txQueueProtoVersion(0), rxBufStart(0), rxBufOffset(0),
        protoState(PROTO_XML_NEGOTIATE),
        protoMagicSearchCount(0),
        protoLen(0),
        protoBlockedForResponse(false),
        protoTimeout(CommoTime::now() + PROTO_TIMEOUT_SECONDS)
{
}

StreamingSocketManagement::ConnectionContext::~ConnectionContext()
{
    while (!txQueue.empty()) {
        TxQueueItem txi = txQueue.back();
        txQueue.pop_back();
        txi.implode();
    }
    delete ssl;
    delete remoteEndpointAddr;
    delete socket;
    delete[] remoteEndpointId;
}




StreamingSocketManagement::SSLConnectionContext::SSLConnectionContext(
        const std::string &myuid,
        SSL_CTX* sslCtx, const uint8_t* clientCert, size_t clientCertLen,
        const uint8_t* caCertBuf, size_t caCertBufLen, const char* certPassword,
        const char *caCertPassword,
        const char *username, const char *password)
                COMMO_THROW (SSLArgException) :
                ssl(NULL),
                writeState(WANT_NONE),
                readState(WANT_NONE),
                cert(NULL),
                key(NULL),
                certChecker(),
                authMessage(),
                caCerts(NULL),
                fatallyErrored(false)
{
    if (!sslCtx)
        throw SSLArgException(COMMO_ILLEGAL_ARGUMENT, "SSL is unavailable - did you initialize SSL libraries?");

    // Attempt to parse cert info
    EVP_PKEY *privKey;
    X509 *cert;
    InternalUtils::readCert(clientCert, clientCertLen,
                            certPassword, &cert, &privKey);



    EVP_PKEY *caPrivKey = NULL;
    SSLCertChecker *certChecker = NULL;
    int nCaCerts = 0;
    
    try {
    InternalUtils::readCACerts(caCertBuf, caCertBufLen,
            caCertPassword, &caCerts, &nCaCerts);
    
    certChecker = new SSLCertChecker(caCerts, nCaCerts);

    if (username && password && username[0] != '\0') {
        xmlDoc *doc = xmlNewDoc((const xmlChar *)"1.0");
        xmlNode *authElement = xmlNewNode(NULL, (const xmlChar *)"auth");
        xmlDocSetRootElement(doc, authElement);
        xmlNode *cotElement = xmlNewChild(authElement, NULL, 
                                          (const xmlChar *)"cot", NULL);
        xmlNewProp(cotElement, (const xmlChar *)"username",
                   (const xmlChar *)username);
        xmlNewProp(cotElement, (const xmlChar *)"password",
                   (const xmlChar *)password);
        xmlNewProp(cotElement, (const xmlChar *)"uid",
                   (const xmlChar *)myuid.c_str());
        
        xmlChar *outPtr = NULL;
        int outSize;
        xmlDocDumpFormatMemory(doc, &outPtr, &outSize, 0);
        if (outPtr == NULL) {
            throw SSLArgException(COMMO_ILLEGAL_ARGUMENT, 
                    "unable to create auth document - unknown error");
        }
        if (outSize > 0 && outPtr[outSize - 1] == '\n')
            // Remove trailing newline - this is needed by at least streaming to TAK server
            outSize--;

        authMessage = std::string((const char *)outPtr, outSize);
        xmlFree(outPtr);
        xmlFreeDoc(doc);
    }

    } catch (SSLArgException &e) {
        if (certChecker)
            delete certChecker; 
        if (caCerts)
            sk_X509_pop_free(caCerts, X509_free);
        X509_free(cert);
        EVP_PKEY_free(caPrivKey);

        throw e;
    }

    this->certChecker = certChecker;
    this->cert = cert;
    this->key = privKey;
}

StreamingSocketManagement::SSLConnectionContext::~SSLConnectionContext()
{
    sk_X509_pop_free(caCerts, X509_free);
    delete certChecker;
    EVP_PKEY_free(key);
    X509_free(cert);
    if (ssl) {
        if (!fatallyErrored)
            SSL_shutdown(ssl);
        SSL_free(ssl);
        ssl = NULL;
    }
}
