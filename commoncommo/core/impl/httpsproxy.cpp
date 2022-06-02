#include "httpsproxy.h"
#include "missionpackage.h"
#include "commotime.h"
#include "commothread.h"

#include <set>
#include <openssl/err.h>
#include <string.h>


using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;
using namespace atakmap::commoncommo::impl::thread;


namespace {
    const char *THREAD_NAMES[] = {
        "cmohttps.io"
    };
    const size_t PROXY_BUF_SIZE = 1024 * 4;
    const int DEFAULT_CONN_TIMEOUT_SECONDS = 90;
    const int64_t SERVER_RETRY_TIME_MS = 60* 1000;
    const int LISTEN_BACKLOG = 15;
    const int MIN_CONN_TIMEOUT_SEC = 5;

    class ProxyContext
    {
      public:
        typedef enum {
            HTTP_READ = 0,
            HTTP_WRITE = 1,
            HTTPS_READ = 2,
            HTTPS_WRITE = 3,
            HTTP_CONNECT = 4
        } IOIdentifier;
    
        ProxyContext(CommoLogger *logger, SSL_CTX *sslCtx, 
                     std::unique_ptr<TcpSocket> httpSock,
                     uint16_t httpPort,
                     int connTimeoutSec);
        ~ProxyContext();

        // True if the identified io operation is ready
        // to go and needs to know if socket can support
        bool ioNeeded(IOIdentifier io);

        bool process(bool *stateChanged, const bool *ioReady);
        
        TcpSocket *getHttpsSocket();
        TcpSocket *getHttpSocket();

      private:
        typedef enum {
            STATE_SSL_HANDSHAKE,
            STATE_HTTP_CONNECT,
            STATE_IO
        } State;

        State state;
      
        CommoLogger *logger;

        std::unique_ptr<TcpSocket> httpsSocket;
        SSL *ssl;
        
        std::unique_ptr<TcpSocket> httpSocket;

        // Buffer for data moving https client through to http server
        uint8_t bufClientToServer[PROXY_BUF_SIZE];
        size_t offsetClientToServer;

        // Buffer for data moving http server through to https client
        uint8_t bufServerToClient[PROXY_BUF_SIZE];
        size_t offsetServerToClient;
        
        typedef enum {
            WANT_NONE,
            WANT_READ,
            WANT_WRITE
        } SSLWantState;
        // State returned by most recent write attempt
        // Only valid when there is data to write
        SSLWantState writeState;
        // State returned by most recent read attempt
        // or most recent handshake attempt.
        // Only valid when !sslAccepted or there is
        // room to read data.
        SSLWantState readState;
        
        bool ioNeeds[5];
        const uint16_t httpPort;
        const int connTimeoutSec;
        CommoTime connTimeoutTime;
        bool fatallyErrored;
        
    private:
        COMMO_DISALLOW_COPY(ProxyContext);

        // Returns if any changed
        bool updateIONeeds();
        
        bool processSSLHandshake(bool *done, bool *stateChanged, bool *ioReady);
        bool processHttpConnect(bool *done, bool *stateChanged, bool *ioReady);
        bool processIO(bool *done, bool *ioReady);


    };
    
    
}




/*********************************************************************/
// Internal class declaration


namespace atakmap {
namespace commoncommo {
namespace impl {

class HttpsProxy::IOThreadContext
{
public:
    IOThreadContext(CommoLogger *logger);
    ~IOThreadContext();

    // Shut everything down, kill all connections, clear params
    // Call from any thread
    void shutdown();
    // Call from any thread
    bool setServerParams(int newLocalPort, X509 *certificate,
                         EVP_PKEY *pkey,
                         STACK_OF(X509) *chain);


    // Call from any thread
    void setLocalHttpPort(int port) COMMO_THROW (std::invalid_argument);
    // Call from any thread
    void setConnTimeoutSec(int connTimeoutSec) COMMO_THROW (std::invalid_argument);
    // Call from any thread
    void signalStop();
  

    // true if socket acquired, false if interrupted
    // Call from IO thread only
    bool waitForServerReady();

    // do one iteration of processing of IO
    // Call from IO thread only
    void process();
    
private:
    CommoLogger *logger;

    // Mutex protects changes from external caller and use by IO thread
    // during normal processing.
    RWMutex ioMutex;
    // Used to coordinate serverSocket's validity and sleep/waits
    // when socket is down/not able to be created.
    Mutex mutex;
    CondVar monitor;

    SelectInterrupter *interrupter;
    

    // http port to proxy to on 127.0.0.1
    // Takes effect as new connections are made
    // If MP_LOCAL_PORT_DISABLE, the proxy will be disabled
    // Checked on set to be valid for downcast to uint16 if not DISABLE
    // sync non-io thread writes on both mutexes, iothread on main mutex
    int localHttpPort;
    
    // Configured local port for https server.
    // MP_LOCAL_PORT_DISABLE if not configured / configured for disable
    // If not DISABLE, sslCtx also has cert info loaded
    // Checked on set to be valid for downcast to uint16
    // sync non-io thread writes on both mutexes, iothread on either mutex
    int localHttpsPort;    

    // NULL if shut down/not running    
    // sync non-io thread writes on both mutexes, iothread on either mutex
    std::unique_ptr<TcpSocket> serverSocket;
    
    // Tracks if server socket is up *and* listening.  We may bind the local
    // port to hold it but not listen when some parameters are unavailable.
    bool serverSocketListening;
    
    // SSL Context for server
    // sync non-io thread writes on both mutexes, iothread on either mutex
    SSL_CTX *sslCtx;
    
    // Empty if shut down/not running
    // sync non-io thread writes on both mutexes, iothread on either mutex
    std::set<std::unique_ptr<ProxyContext>> connections;

    // Connection timeout in seconds
    // Sync on ioMutex
    int connTimeoutSeconds;

    // True to update selector's socket set next go around
    // sync non-io thread writes on both mutexes, iothread on either mutex
    bool updateSelector;
    
    // IO Thread use *only*
    NetSelector selector;

    // true to signal io thread should bail as soon as possible
    // sync on mutex
    bool stopSignalled;
    


    // Must hold locks as needed for these
    void terminateServerSocket();
    bool bindServerSocket(uint16_t newLocalPort);
    bool listenServerSocket();

    COMMO_DISALLOW_COPY(IOThreadContext);
};

}
}
}


/*********************************************************************/
// Constructor/destructor


HttpsProxy::HttpsProxy(CommoLogger *logger) : 
        ThreadedHandler(1, THREAD_NAMES),
        logger(logger),
        ioCtx(new IOThreadContext(logger))
{
    startThreads();
}


HttpsProxy::~HttpsProxy()
{
    stopThreads();
}




/*********************************************************************/
// Public API


CommoResult HttpsProxy::setServerParams(int localPort,
                                        const uint8_t *certData, size_t certLen,
                                        const char *certPassword)
{
    X509 *cert;
    EVP_PKEY *privKey;
    STACK_OF(X509) *caCerts;
    int nCaCerts;
    
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "https proxy - changing port to %d", localPort);
    ioCtx->shutdown();
    
    if (localPort == MP_LOCAL_PORT_DISABLE)
        return COMMO_SUCCESS;
    
    if (certData == NULL || certLen == 0 || certPassword == NULL)
        return COMMO_ILLEGAL_ARGUMENT;

    try {
        InternalUtils::readCert(certData, certLen,
                                certPassword,
                                &cert, 
                                &privKey, 
                                &caCerts,
                                &nCaCerts);
    } catch (SSLArgException &ex) {
        return ex.errCode;
    }

    bool serverOk = ioCtx->setServerParams(localPort, cert, privKey, caCerts);

    sk_X509_pop_free(caCerts, X509_free);
    X509_free(cert);
    EVP_PKEY_free(privKey);
    
    if (!serverOk)
        return COMMO_ILLEGAL_ARGUMENT;
    
    return COMMO_SUCCESS;
}


void HttpsProxy::setLocalHttpPort(int port) COMMO_THROW (std::invalid_argument)
{
    ioCtx->setLocalHttpPort(port);
}


void HttpsProxy::setConnTimeoutSec(int connTimeoutSec) COMMO_THROW (std::invalid_argument)
{
    ioCtx->setConnTimeoutSec(connTimeoutSec);
}



/*********************************************************************/
// ThreadedHandler


void HttpsProxy::threadEntry(size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        ioThreadProcess();
        break;
    }
}


void HttpsProxy::threadStopSignal(size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        ioCtx->signalStop();
        break;
    }
}




/*********************************************************************/
// IO thread


void HttpsProxy::ioThreadProcess()
{
    while (!threadShouldStop(IO_THREADID)) {
        if (ioCtx->waitForServerReady())
            ioCtx->process();
    }
}



/*********************************************************************/
// IOThreadContext


HttpsProxy::IOThreadContext::IOThreadContext(CommoLogger *logger) : 
        logger(logger),
        ioMutex(RWMutex::Policy_Fair),
        mutex(),
        monitor(),
        interrupter(NULL),
        localHttpPort(MP_LOCAL_PORT_DISABLE),
        localHttpsPort(MP_LOCAL_PORT_DISABLE),
        serverSocket(),
        serverSocketListening(false),
        sslCtx(NULL),
        connections(),
        connTimeoutSeconds(DEFAULT_CONN_TIMEOUT_SECONDS),
        updateSelector(true),
        selector(),
        stopSignalled(false)
{
    sslCtx = SSL_CTX_new(TLS_server_method());
    if (!sslCtx) {
        unsigned long errCode = ERR_get_error();
        char ebuf[1024];
        ERR_error_string_n(errCode, ebuf, 1024);
        ebuf[1023] = '\0';
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
            "Cannot create SSL Context! Https proxy will not be available (details: %s)",
            ebuf);
    }
    try {
        interrupter = new SelectInterrupter();
    } catch (SocketException &) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_WARNING,
            "Cannot create selector interrupter - some config changes may be slow");
    }
}


HttpsProxy::IOThreadContext::~IOThreadContext()
{
    shutdown();
    if (sslCtx) {
        SSL_CTX_free(sslCtx);
        sslCtx = NULL;
    }
    if (interrupter) {
        delete interrupter;
        interrupter = NULL;
    }
}


void HttpsProxy::IOThreadContext::signalStop()
{
    Lock lock(mutex);
    stopSignalled = true;
    monitor.broadcast(lock);
}


bool HttpsProxy::IOThreadContext::waitForServerReady()
{
    Lock lock(mutex);
    while (!stopSignalled) {
        if (serverSocketListening)
            return true;
        
        // See if we can try to create and listen
        if (sslCtx && localHttpPort != MP_LOCAL_PORT_DISABLE && 
                      localHttpsPort != MP_LOCAL_PORT_DISABLE) {
            if (serverSocket || bindServerSocket(localHttpsPort)) {
                if (listenServerSocket())
                    return true;
                else
                    terminateServerSocket();
            }
        }
        //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy waiting on server socket %d %d", localHttpPort, localHttpsPort);
        
        // Either not enough info/disabled or socket create failed
        // Sleep for retry or until new info comes in or we're done
        monitor.wait(lock, SERVER_RETRY_TIME_MS);
    }
    
    return false;
}


void HttpsProxy::IOThreadContext::shutdown()
{
    if (interrupter)
        interrupter->trigger();
    WriteLock ioLock(ioMutex);
    Lock lock(mutex);
    terminateServerSocket();
    
    localHttpsPort = MP_LOCAL_PORT_DISABLE;

    if (interrupter)
        interrupter->untrigger();
}


void HttpsProxy::IOThreadContext::terminateServerSocket()
{
    if (serverSocket == nullptr)
        return;

    // Clear selector sets
    updateSelector = true;
    
    // Kill off all connections
    connections.clear();

    // Shut down our socket
    serverSocket.reset();
    
    // Not listening anymore, of course
    serverSocketListening = false;
}


bool HttpsProxy::IOThreadContext::setServerParams(int newLocalPort,
                                      X509 *certificate,
                                      EVP_PKEY *pkey,
                                      STACK_OF(X509) *chain)
{
    if (!sslCtx)
        return false;
    
    if (interrupter)
        interrupter->trigger();

    WriteLock ioLock(ioMutex);
    Lock lock(mutex);
    
    // Clear this before we continue on; if anything fails here,
    // having this cleared prevents the IO thread from trying to
    // rebuild the socket with now-invalid parameters
    localHttpsPort = MP_LOCAL_PORT_DISABLE;

    if (serverSocket != nullptr)
        terminateServerSocket();
    
    if (interrupter)
        // Release here before any potential return
        interrupter->untrigger();

    if (newLocalPort <= 0 || newLocalPort > 65535)
        return false;

    if (newLocalPort == MP_LOCAL_PORT_DISABLE)
        // Done
        return true;    
    
    if (SSL_CTX_use_cert_and_key(sslCtx, certificate, pkey, chain, 1) != 1)
        return false;

    if (!bindServerSocket(newLocalPort))
        return false; 
    
    // Success - update params and notify iothread in case it was waiting       
    localHttpsPort = newLocalPort;
    monitor.broadcast(lock);

    return true;
}


bool HttpsProxy::IOThreadContext::bindServerSocket(uint16_t newLocalPort)
{
    try {
        std::unique_ptr<NetAddress> addr(NetAddress::create(NA_TYPE_INET4,
                                                            newLocalPort));
        serverSocket.reset(new TcpSocket(NA_TYPE_INET4, false));
        serverSocket->bind(addr.get());
    } catch (SocketException &) {
        return false;
    }
    return true;
}


bool HttpsProxy::IOThreadContext::listenServerSocket()
{
    try {
        serverSocket->listen(LISTEN_BACKLOG);
        serverSocketListening = true;
        updateSelector = true;
    } catch (SocketException &) {
        return false;
    }
    return true;
}


void HttpsProxy::IOThreadContext::setLocalHttpPort(int port)
        COMMO_THROW (std::invalid_argument)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy local port connection to http server changing from %d to %d", localHttpPort, port);

    if (port != MP_LOCAL_PORT_DISABLE && (port <= 0 || port > 65535))
        throw std::invalid_argument("");

    if (interrupter)
        interrupter->trigger();

    WriteLock ioLock(ioMutex);
    Lock lock(mutex);
    localHttpPort = port;
    if (port != MP_LOCAL_PORT_DISABLE)
        monitor.broadcast(lock);

    if (interrupter)
        interrupter->untrigger();
}
 
                                    
void HttpsProxy::IOThreadContext::setConnTimeoutSec(int connTimeoutSec)
        COMMO_THROW (std::invalid_argument)
{
    if (connTimeoutSec < MIN_CONN_TIMEOUT_SEC)
        throw std::invalid_argument("");

    if (interrupter)
        interrupter->trigger();

    WriteLock ioLock(ioMutex);
    this->connTimeoutSeconds = connTimeoutSec;

    if (interrupter)
        interrupter->untrigger();
}


void HttpsProxy::IOThreadContext::process()
{
    // Wait for clear interrupt before acquiring io lock
    if (interrupter)
        interrupter->waitUntilUntriggered();

    ReadLock ioLock(ioMutex);
    bool fatalError = false;

    if (!serverSocketListening)
        return;

    if (updateSelector) {
        //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy reconfiguring selector");
        std::vector<Socket *> readSockets;
        std::vector<Socket *> writeSockets;
        std::vector<Socket *> connectingSockets;

        // Always reading server socket        
        readSockets.push_back(serverSocket.get());
        
        // Always reading interrupter
        if (interrupter)
            readSockets.push_back(interrupter->getSocket());
        
        for (auto iter = connections.begin();
                  iter != connections.end(); iter++) {
            ProxyContext *pctx = iter->get();

            /*
              InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                "https proxy connection selector %d %d %d %d %d",
                 pctx->ioNeeded((ProxyContext::IOIdentifier)0),
                 pctx->ioNeeded((ProxyContext::IOIdentifier)1),
                 pctx->ioNeeded((ProxyContext::IOIdentifier)2),
                 pctx->ioNeeded((ProxyContext::IOIdentifier)3),
                 pctx->ioNeeded((ProxyContext::IOIdentifier)4));
            */
            
            // https side
            if (pctx->ioNeeded(ProxyContext::HTTPS_WRITE))
                writeSockets.push_back(pctx->getHttpsSocket());
            if (pctx->ioNeeded(ProxyContext::HTTPS_READ))
                readSockets.push_back(pctx->getHttpsSocket());

            // http side
            if (pctx->ioNeeded(ProxyContext::HTTP_CONNECT)) {
                connectingSockets.push_back(pctx->getHttpSocket());
            } else {
                if (pctx->ioNeeded(ProxyContext::HTTP_WRITE))
                    writeSockets.push_back(pctx->getHttpSocket());
                if (pctx->ioNeeded(ProxyContext::HTTP_READ))
                    readSockets.push_back(pctx->getHttpSocket());
            }
        }
        
        selector.setSockets(&readSockets, &writeSockets, &connectingSockets);
        updateSelector = false;
    }
    
    try {
        if (!selector.doSelect(5000))
            // timeout
            return;
    } catch (SocketException &) {
        // Unexpected error. Restart server
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "https proxy io select failed");
        fatalError = true;
    }
    
    if (!fatalError) {
        auto iter = connections.begin();
        while (iter != connections.end()) {
            ProxyContext *pctx = iter->get();
            
            bool ioReady[5] = { false };
            
            // https side
            if (pctx->ioNeeded(ProxyContext::HTTPS_WRITE) &&
                    selector.getLastWriteState(pctx->getHttpsSocket()) == 
                            NetSelector::WRITABLE)
                ioReady[ProxyContext::HTTPS_WRITE] = true;

            if (pctx->ioNeeded(ProxyContext::HTTPS_READ) &&  
                     selector.getLastReadState(pctx->getHttpsSocket()) ==
                            NetSelector::READABLE)
                ioReady[ProxyContext::HTTPS_READ] = true;

            if (pctx->ioNeeded(ProxyContext::HTTP_CONNECT)) {
                ioReady[ProxyContext::HTTP_CONNECT] =
                    selector.getLastConnectState(pctx->getHttpSocket()) ==
                        NetSelector::WRITABLE;
            } else {
                if (pctx->ioNeeded(ProxyContext::HTTP_WRITE) &&
                        selector.getLastWriteState(pctx->getHttpSocket()) == 
                                NetSelector::WRITABLE)
                    ioReady[ProxyContext::HTTP_WRITE] = true;

                if (pctx->ioNeeded(ProxyContext::HTTP_READ) &&  
                         selector.getLastReadState(pctx->getHttpSocket()) ==
                                NetSelector::READABLE)
                    ioReady[ProxyContext::HTTP_READ] = true;
            }
            //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy client io ready %d %d %d %d %d", ioReady[0],ioReady[1], ioReady[2], ioReady[3], ioReady[4]);
            
            bool changedState;
            if (!pctx->process(&changedState, ioReady)) {
                // errored - remove and destroy it
                iter = connections.erase(iter);
                updateSelector = true;
                //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy client process error - removing");
            } else {
                //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy client process donez - %s", changedState ? "changed state" : "state stable");
                updateSelector |= changedState;
                iter++;
            }
        }
        
        // Add any new connections last
        if (selector.getLastReadState(serverSocket.get()) ==
                        NetSelector::READABLE) {
            bool ioReady[5] = { false };
            do {
                std::unique_ptr<TcpSocket> newClient(nullptr);
                try {
                    TcpSocket *nc = serverSocket->accept(NULL);
                    if (!nc) {
                        // No more new clients
                        //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy accepting new clients completed");
                        break;
                    }
                    newClient.reset(nc);
                } catch (SocketException &) {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "https proxy accept of new client failed - restarting");
                    fatalError = true;
                    break;
                }
                //InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "https proxy accepting new client");

                std::unique_ptr<ProxyContext> newCtx(
                        new ProxyContext(logger, sslCtx, std::move(newClient),
                                         localHttpPort, connTimeoutSeconds));
                // Kick off initial processing
                bool dummy;
                if (newCtx->process(&dummy, ioReady)) {
                    connections.insert(std::move(newCtx));
                    // Update to get new client
                    updateSelector = true;
                } // else errored, don't add
            } while (true);
        }
        
        // And finally drain the interrupter
        if (interrupter &&
                selector.getLastReadState(interrupter->getSocket()) == 
                        NetSelector::READABLE)
            interrupter->drain();
    }
    
    if (fatalError)
        terminateServerSocket();
}




/*********************************************************************/
// ProxyContext


ProxyContext::ProxyContext(CommoLogger *logger,
                           SSL_CTX *sslCtx,
                           std::unique_ptr<TcpSocket> httpsSock,
                           uint16_t httpPort,
                           int connTimeoutSec) : 
        state(STATE_SSL_HANDSHAKE),
        logger(logger),
        httpsSocket(std::move(httpsSock)),
        ssl(NULL),
        httpSocket(nullptr),
        bufClientToServer(),
        offsetClientToServer(0),
        bufServerToClient(),
        offsetServerToClient(0),
        writeState(WANT_NONE),
        readState(WANT_NONE),
        ioNeeds{false, false, false, false, false},
        httpPort(httpPort),
        connTimeoutSec(connTimeoutSec),
        connTimeoutTime(CommoTime::ZERO_TIME),
        fatallyErrored(false)
{
    ssl = SSL_new(sslCtx);
    SSL_set_fd(ssl, (int)httpsSocket->getFD());
}

ProxyContext::~ProxyContext()
{
    if (!fatallyErrored)
        SSL_shutdown(ssl);
    SSL_free(ssl);
}

bool ProxyContext::ioNeeded(IOIdentifier io)
{
    return ioNeeds[io];
}


TcpSocket *ProxyContext::getHttpsSocket()
{
    return httpsSocket.get();
}


TcpSocket *ProxyContext::getHttpSocket()
{
    return httpSocket.get();
}


bool ProxyContext::updateIONeeds()
{
    bool newNeeds[sizeof(ioNeeds)] = { false };
    // https reading
    switch (state) {
    case STATE_SSL_HANDSHAKE:
        newNeeds[HTTPS_READ] = (readState == WANT_READ);
        newNeeds[HTTPS_WRITE] = (readState == WANT_WRITE);
        break;
    case STATE_HTTP_CONNECT:
        newNeeds[HTTP_CONNECT] = true;
        break;
    case STATE_IO:
        newNeeds[HTTPS_READ] = ((offsetClientToServer != PROXY_BUF_SIZE &&
                                 readState != WANT_WRITE) ||
                                (offsetServerToClient > 0 && 
                                 writeState == WANT_READ));
        newNeeds[HTTP_READ] = offsetServerToClient != PROXY_BUF_SIZE;

        newNeeds[HTTPS_WRITE] = ((offsetServerToClient > 0 && 
                                  writeState != WANT_READ) ||
                                 (offsetClientToServer != PROXY_BUF_SIZE && 
                                  readState == WANT_WRITE));
        newNeeds[HTTP_WRITE] = offsetClientToServer > 0;
        break;
    }
    
    bool ret = false;
    for (size_t i = 0; i < sizeof(ioNeeds); ++i) {
        ret |= (ioNeeds[i] != newNeeds[i]);
        ioNeeds[i] = newNeeds[i];
    }
    return ret;
}


bool ProxyContext::process(bool *stateChanged, const bool *ioReady)
{
    bool processOk = true;
    bool done = false;
    
    bool ioReadyCopied[sizeof(ioNeeds)];
    std::copy(ioReady, ioReady + sizeof(ioNeeds), ioReadyCopied);
    *stateChanged = false;
    
    while (processOk && !done) {
        switch (state) {
        case STATE_SSL_HANDSHAKE:
          processOk = processSSLHandshake(&done, stateChanged, ioReadyCopied);
          break;
        case STATE_HTTP_CONNECT:
          processOk = processHttpConnect(&done, stateChanged, ioReadyCopied);
          break;
        case STATE_IO:
          processOk = processIO(&done, ioReadyCopied);
          break;
        }
    }
    
    if (processOk) {
        *stateChanged |= updateIONeeds();
    }
    return processOk;
}


bool ProxyContext::processSSLHandshake(bool *done, bool *stateChanged, 
                                       bool *ioReady)
{
    bool ret = true;

    if (readState == WANT_NONE || 
            (readState == WANT_READ && ioReady[HTTPS_READ]) ||
            (readState == WANT_WRITE && ioReady[HTTPS_WRITE])) {
        ERR_clear_error();
        int r = SSL_accept(ssl);
        std::unique_ptr<NetAddress> addr(nullptr);
        
        switch (r) {
        case 0:
            // Error
            ret = false;
            *done = true;
            break;
        case 1:
            // success; clear readState
            readState = WANT_NONE;
            // Initiate http-side connection
            httpSocket.reset(new TcpSocket(NA_TYPE_INET4, false));
            addr.reset(NetAddress::create("127.0.0.1", httpPort));
            if (!httpSocket->connect(addr.get())) {
                // Now need to wait
                state = STATE_HTTP_CONNECT;
                connTimeoutTime = CommoTime::now() + static_cast<float>(connTimeoutSec);
                *done = true;
            } else {
                // Connection made immediately
                state = STATE_IO;
                // Update IO needs before letting IO processing take over
                *stateChanged |= updateIONeeds();
                // Also force "reads" as ready so that reads on each
                // side are tried immediately
                std::fill(ioReady, ioReady + sizeof(ioNeeds), false);
                ioReady[HTTPS_READ] = ioReady[HTTP_READ] = true;
            }
            break;
        default:
            r = SSL_get_error(ssl, r);
            if (r == SSL_ERROR_WANT_READ)
                readState = WANT_READ;
            else if (r == SSL_ERROR_WANT_WRITE)
                readState = WANT_WRITE;
            else {
                // Actual error
                if (r == SSL_ERROR_SSL || r == SSL_ERROR_SYSCALL)
                    fatallyErrored = true;
                ret = false;
            }
            *done = true;
            break;
        }
    } else {
        // Need to wait
        *done = true;
    }
    return ret;
}


bool ProxyContext::processHttpConnect(bool *done, bool *stateChanged,
                                      bool *ioReady)
{
    bool ret = true;

    if (ioReady[HTTP_CONNECT]) {
        if (httpSocket->isSocketErrored()) {
            // Connection failed
            ret = false;
            *done = true;
        } else {
            // connected
            state = STATE_IO;
            // Update IO needs before letting IO processing take over
            *stateChanged |= updateIONeeds();
            // Also force "reads" as ready so that reads on each
            // side are tried immediately
            ioReady[HTTPS_READ] = ioReady[HTTP_READ] = true;
        }
    } else {
        if (CommoTime::now() > connTimeoutTime)
            ret = false;

        // Not ready, so nothing we can do
        *done = true;
    }
    return ret;
}


bool ProxyContext::processIO(bool *done, bool *ioReady)
{
    // We know what IO was ready when we got here
    // for each thing we previously wanted.
    // But fulfilling IOs we did need can create needs as we go, thus
    // this array tracks initial state and lets us
    // give one try at each type of IO we didn't externally
    // check readiness of before entry because we didn't need it then
    bool ioOneTry[sizeof(ioNeeds)];
    for (size_t i = 0; i < sizeof(ioNeeds); ++i)
        ioOneTry[i] = !ioNeeds[i];
        
    bool hadError = false;
    
    do {
        // HTTP side read
        if (ioReady[HTTP_READ] && offsetServerToClient < PROXY_BUF_SIZE) {
            try {
                size_t n = httpSocket->read(
                        bufServerToClient + offsetServerToClient,
                        PROXY_BUF_SIZE - offsetServerToClient);
                if (n) {
                    if (offsetServerToClient == 0 && ioOneTry[HTTPS_WRITE]) {
                        // Had nothing to write, but we do now
                        ioOneTry[HTTPS_WRITE] = false;
                        ioReady[HTTPS_WRITE] = true;
                    }
                    offsetServerToClient += n;
                } else {
                    // Nothing could be read
                    ioReady[HTTP_READ] = false;
                }
                
            } catch (SocketException &) {
                hadError = true;
                break;
            }
        }
        
        // HTTP side write
        if (ioReady[HTTP_WRITE] && offsetClientToServer > 0) {
            try {
                size_t n = httpSocket->write(bufClientToServer, 
                                             offsetClientToServer);
                if (n) {
                    if (offsetClientToServer == PROXY_BUF_SIZE &&
                            ioOneTry[HTTPS_READ]) {
                        // Had no space to read, but we do now
                        ioOneTry[HTTPS_READ] = false;
                        ioReady[HTTPS_READ] = true;
                    }
                    size_t rn = offsetClientToServer - n;
                    memmove(bufClientToServer, bufClientToServer + n, rn);
                    offsetClientToServer = rn;
                } else {
                    // Nothing could be written
                    ioReady[HTTP_WRITE] = false;
                }
                
            } catch (SocketException &) {
                hadError = true;
                break;
            }
        }
        
        // HTTPS side read
        if (offsetClientToServer < PROXY_BUF_SIZE &&
                ((ioReady[HTTPS_READ] && readState != WANT_WRITE) ||
                 (ioReady[HTTPS_WRITE] && readState == WANT_WRITE))) {
            
            ERR_clear_error();
            int n = SSL_read(ssl, bufClientToServer + offsetClientToServer,
                         (int)(PROXY_BUF_SIZE - offsetClientToServer));
            if (n <= 0) {
                // Nothing was read
                n = SSL_get_error(ssl, n);
                switch (n) {
                case SSL_ERROR_WANT_READ:
                    readState = WANT_READ;
                    ioOneTry[HTTPS_READ] = ioReady[HTTPS_READ] = false;
                    break; 
                case SSL_ERROR_WANT_WRITE:
                    readState = WANT_WRITE;
                    ioOneTry[HTTPS_WRITE] = ioReady[HTTPS_WRITE] = false;
                    break; 
                case SSL_ERROR_SSL:
                case SSL_ERROR_SYSCALL:
                    fatallyErrored = true;
                    // Intentionally fall through
                default:
                    hadError = true;
                    break;
                }
                if (hadError)
                    break;
            } else {
                if (offsetClientToServer == 0 && ioOneTry[HTTP_WRITE]) {
                    // Had nothing to write, but we do now
                    ioOneTry[HTTP_WRITE] = false;
                    ioReady[HTTP_WRITE] = true;
                }

                offsetClientToServer += n;
                readState = WANT_NONE;
            }
        }
        
        // HTTPS side write
        if (offsetServerToClient > 0 &&
                ((ioReady[HTTPS_WRITE] && writeState != WANT_READ) ||
                 (ioReady[HTTPS_READ] && writeState == WANT_READ))) {
            ERR_clear_error();
            int n = SSL_write(ssl, bufServerToClient, (int)offsetServerToClient);
            if (n <= 0) {
                // Nothing was written
                n = SSL_get_error(ssl, n);
                switch (n) {
                case SSL_ERROR_WANT_READ:
                    writeState = WANT_READ;
                    ioOneTry[HTTPS_READ] = ioReady[HTTPS_READ] = false;
                    break; 
                case SSL_ERROR_WANT_WRITE:
                    writeState = WANT_WRITE;
                    ioOneTry[HTTPS_WRITE] = ioReady[HTTPS_WRITE] = false;
                    break; 
                case SSL_ERROR_SSL:
                case SSL_ERROR_SYSCALL:
                    fatallyErrored = true;
                    // Intentionally fall through
                default:
                    hadError = true;
                    break;
                }
                if (hadError)
                    break;
            } else {
                if (offsetServerToClient == PROXY_BUF_SIZE &&
                        ioOneTry[HTTP_READ]) {
                    // Had no space to read, but we do now
                    ioOneTry[HTTP_READ] = false;
                    ioReady[HTTP_READ] = true;
                }
                int rn = (int)(offsetServerToClient - n);
                memmove(bufServerToClient, bufServerToClient + n, rn);
                offsetServerToClient = rn;
                writeState = WANT_NONE;
            }

        }
    
    } while ((ioReady[HTTP_READ] && offsetServerToClient != PROXY_BUF_SIZE) ||
             (ioReady[HTTP_WRITE] && offsetClientToServer != 0) ||
             (readState == WANT_NONE && offsetClientToServer != PROXY_BUF_SIZE) ||
             (writeState == WANT_NONE && offsetServerToClient != 0));

    *done = true;
    return !hadError;
}

