#ifndef IMPL_STREAMINGSOCKETMANAGEMENT_H_
#define IMPL_STREAMINGSOCKETMANAGEMENT_H_


#include "commoutils.h"
#include "netinterface.h"
#include "commologger.h"
#include "commoresult.h"

#include "commotime.h"
#include "netsocket.h"
#include "threadedhandler.h"
#include "cotmessage.h"
#include "resolverqueue.h"
#include "internalutils.h"
#include "commothread.h"

#include "openssl/ssl.h"

#include <map>
#include <set>
#include <deque>

namespace atakmap {
namespace commoncommo {
namespace impl {


class StreamingMessageListener
{
public:
    // Parameters must be copied during event servicing if used after return
    virtual void streamingMessageReceived(std::string streamingEndpoint, const CoTMessage *message) = 0;

protected:
    StreamingMessageListener() {};
    virtual ~StreamingMessageListener() {};

private:
    COMMO_DISALLOW_COPY(StreamingMessageListener);

};



class StreamingSocketManagement : public ThreadedHandler, public ResolverListener
{
public:
    StreamingSocketManagement(CommoLogger *logger, const std::string &myuid);
    virtual ~StreamingSocketManagement();

    void setMonitor(bool enable);
    void setConnTimeout(float sec);

    StreamingNetInterface *addStreamingInterface(const char *hostname, int port,
                                                 const CoTMessageType *types,
                                                 size_t nTypes,
                                                 const uint8_t *clientCert, size_t clientCertLen,
                                                 const uint8_t *caCert, size_t caCertLen,
                                                 const char *certPassword,
                                                 const char *caCertPassword,
                                                 const char *username,
                                                 const char *password,
                                                 CommoResult *resultCode);
    CommoResult removeStreamingInterface(std::string *endpoint, StreamingNetInterface *iface);

    // Configures the given openssl context with ssl parameters associated with
    // the stream identifies by streamingEndpoint.  Throws if the endpoint
    // identifier does not correspond to an active endpoint or if said
    // endpoint is not SSL-based.
    void configSSLForConnection(std::string streamingEndpoint, SSL_CTX *ctx) COMMO_THROW (std::invalid_argument);
    bool isEndpointSSL(std::string streamingEndpoint) COMMO_THROW (std::invalid_argument);
    NetAddress *getAddressForEndpoint(std::string streamingEndpoint) COMMO_THROW (std::invalid_argument);

    // Msg should have destination contacts already set and endpoints
    // set for streaming.
    void sendMessage(std::string streamingEndpoint, const CoTMessage *msg) COMMO_THROW (std::invalid_argument);
    // Msg should have endpoints set for streaming
    // Only broadcast to those streams configured to support the message's type,
    // unless ignoreType is true, then send to all servers regardless
    void sendBroadcast(const CoTMessage *msg, bool ignoreType = false) COMMO_THROW (std::invalid_argument);

    void addStreamingMessageListener(StreamingMessageListener *listener);
    void removeStreamingMessageListener(StreamingMessageListener *listener);
    void addInterfaceStatusListener(InterfaceStatusListener *listener);
    void removeInterfaceStatusListener(InterfaceStatusListener *listener);
    
    // ResolverListener impl
    virtual bool resolutionComplete(ResolverQueue::Request *id,
                                    const std::string &hostAddr,
                                    NetAddress *result);
    virtual void resolutionAttemptFailed(ResolverQueue::Request *id,
                                    const std::string &hostAddr);

protected:
    // ThreadedHandler impl
    virtual void threadEntry(size_t threadNum);
    virtual void threadStopSignal(size_t threadNum);

private:
    enum { CONN_MGMT_THREADID, IO_THREADID, RX_QUEUE_THREADID };
    typedef enum { CONN_TYPE_TCP, CONN_TYPE_SSL } ConnType;

    struct ConnectionContext;

    struct TxQueueItem {
        ConnectionContext *ctx;
        // For cot events, msg is non-null. For raw messages, it is null
        // and the data representation is fixed to the raw message content
        CoTMessage *msg;
        const uint8_t *data;
        size_t dataLen;
        size_t bytesSent;
        bool protoSwapRequest;
        TxQueueItem();
        // Takes ownership of msg. Will be delete'd on implosion.
        TxQueueItem(ConnectionContext *ctx, CoTMessage *msg,
                    int protoVersion = 0) COMMO_THROW (std::invalid_argument);
        TxQueueItem(ConnectionContext *ctx, const std::string &rawMessage);
        // Copy is ok
        ~TxQueueItem();
        void implode();
        
        // Throws if error serializing to the format given or if
        // this item was raw message based
        void reserialize(int protoVersion = 0) COMMO_THROW (std::invalid_argument);
    };

    struct RxQueueItem {
        ConnectionContext *ctx;
        CoTMessage *msg;
        RxQueueItem();
        // Takes ownership of msg. Will be delete'd on implosion.
        RxQueueItem(ConnectionContext *ctx, CoTMessage *msg);
        // Copy is ok
        ~RxQueueItem();
        void implode();
    };

    typedef std::map<std::string, ConnectionContext *> ContextMap;
    typedef std::set<ConnectionContext *> ContextSet;
    typedef std::map<ResolverQueue::Request *, ConnectionContext *> ResolverMap;
    typedef std::deque<RxQueueItem> RxQueue;
    typedef std::deque<TxQueueItem> TxQueue;
    typedef std::set<CoTMessageType> MessageTypeSet;

    struct SSLConnectionContext {
        // Initialized as needed
        SSL *ssl;

        typedef enum {
            WANT_NONE,
            WANT_READ,
            WANT_WRITE
        } SSLWantState;

        // The tx state of the ssl connection:
        // When the holding ConnectionContext is in a "connecting" state:
        //    WANT_NONE signifies no outstanding ssl connection
        //    WANT_READ/WANT_WRITE means an outstanding ssl connection
        //            needs read/write
        // When the holding ConnectionContext is in "up" state:
        //    WANT_NONE signifies no ongoing write operation
        //    WANT_READ/WANT_WRITE signifies that transmit operation
        //        is in progress, but needs data to read/write
        SSLWantState writeState;

        // The rx state of the ssl connection:
        // When the holding ConnectionContext is in a "connecting" state:
        //    WANT_NONE is the only used and legal value
        // When the holding ConnectionContext is in "up" state:
        //    WANT_NONE signifies no ongoing receive operation
        //    WANT_READ/WANT_WRITE signifies that receive operation
        //        is in progress, but needs data to read/write
        SSLWantState readState;

        // The client's certificate
        X509 *cert;

        // The private key for the client cert
        EVP_PKEY *key;

        // Checker for the user-supplied trust store.  Generally only
        // using a single cert in practice.
        SSLCertChecker *certChecker;

        // Zero length if auth is not in use
        std::string authMessage;

        // The ca certs, in order
        STACK_OF(X509) *caCerts;
        
        // Flag to track if fatal SSL-level error was seen. Controls
        // how teardown of this context is performed, particularly
        // if SSL_shutdown should be attempted
        bool fatallyErrored;

        SSLConnectionContext(const std::string &uid,
                SSL_CTX *sslCtx, const uint8_t *clientCert,
                size_t clientCertLen,
                const uint8_t *caCert, size_t caCertLen,
                const char *certPassword,
                const char *caCertPassword,
                const char *username, const char *password)
                    COMMO_THROW (SSLArgException);
        ~SSLConnectionContext();
    };

    struct ConnectionContext : public StreamingNetInterface {
        // Set equal to max udp message size because that's the practical
        // limit on UDP cot messages and is large enough for any reasonably
        // sized cot message.  This is used to prevent memory exhaustion
        // and DoS by any naughty remote clients
        static const size_t rxBufSize = maxUDPMessageSize;

        // The endpoint identifier - identical to superclass id, except
        // in string form and not char array.
        // Contains host/ip, protocol, and port # - everything unique about
        // the connection.
        const std::string remoteEndpoint;
        // String form of the remote endpoint hostname or IP address - no port
        // or protocol info.
        std::string remoteEndpointAddrString;
        // The remote port number
        uint16_t remotePort;
        // Endpoint address including port - NULL if Context has a hostname
        // that is not yet resolved.
        NetAddress *remoteEndpointAddr;
        MessageTypeSet broadcastCoTTypes;
        ConnType connType;

        // non-NULL for ssl connections in any state
        SSLConnectionContext *ssl;

        // Connection in "needs resolution" state: Always NULL
        // Connection in "down" state: non-NULL if connection is in progress,
        //                             NULL if connection not in progress.
        // Connection in "up" state. Always non-null; used for i/o
        TcpSocket *socket;
        
        // Only valid in "needs resolution" state - this is the request
        // object in the resolver
        ResolverQueue::Request *resolverRequest;

        // In "needs resolution" state: Not used - set to ZERO_TIME
        // In "down" state: time when connection will next be re-attempted
        // In "up" state: next time to send a "ping"
        CommoTime retryTime;

        // Only used in "up" state. Time at which we last received valid
        // data
        CommoTime lastRxTime;
        
        // Valid when "up" only; this is empty otherwise. Protected by main
        // upMutex
        TxQueue txQueue;
        // Valid when "up"; indicates if this connection's tx queue is
        // protobuf (>0) or xml (0). Protected by main upMutex
        int txQueueProtoVersion;
        
        // Next 2 valid only when "up" and only on io thread
        uint8_t rxBuf[rxBufSize];
        size_t rxBufStart;
        size_t rxBufOffset;

        // All proto* state vars valid only when "up" and only on io thread
        typedef enum {
            // Impl depends on ordering here!
            // Don't reorder without verifying impl!
            PROTO_XML_NEGOTIATE, // XML for now, negotiation may happen
            PROTO_WAITRESPONSE,  // Sent a request for proto, waiting on reply
            PROTO_XML_ONLY,  // XML only from here on out
            PROTO_HDR_MAGIC, // Expecting next byte to be header's magic #
            PROTO_HDR_LEN,   // Looking for length varint
            PROTO_DATA       // Reading in data
        } ProtoDecodeState;
        // Protobuf decoder state
        ProtoDecodeState protoState;
        unsigned int protoMagicSearchCount;
        // Valid only when protoState == PROTO_DATA; length of protobuf msg
        size_t protoLen;
        // Only ever true in PROTO_WAITRESPONSE - true if the request
        // was actually sent (not still in tx queue) and we are truly
        // awaiting reply now.
        bool protoBlockedForResponse;
        // Valid only for protoState is...
        //    PROTO_XML_NEGOTIATE - time when we no longer look
        //                          for proto version support message
        //    PROTO_WAITRESPONSE - time when we give up waiting for response
        CommoTime protoTimeout;

        ConnectionContext(const std::string &epString,
                const std::string &epAddrString,
                uint16_t port,
                NetAddress *ep, ConnType type, SSLConnectionContext *ssl);
        ~ConnectionContext();

    private:
        COMMO_DISALLOW_COPY(ConnectionContext);

    };

    CommoLogger *logger;
    ResolverQueue *resolver;
    float connTimeoutSec;
    bool monitor;

    SSL_CTX *sslCtx;

    ContextMap contexts;
    thread::RWMutex contextMutex;

    bool ioNeedsRebuild;
    ContextSet upContexts;
    thread::Mutex upMutex;
    ContextSet downContexts;
    bool downNeedsRebuild;
    thread::Mutex downMutex;
    // resolutionContexts protected by contextMutex write lock
    ResolverMap resolutionContexts;
    
    std::string myuid;
    std::string myPingUid;

    RxQueue rxQueue;
    thread::Mutex rxQueueMutex;
    thread::CondVar rxQueueMonitor;

    std::set<InterfaceStatusListener *> ifaceListeners;
    thread::Mutex ifaceListenersMutex;

    std::set<StreamingMessageListener *> listeners;
    thread::Mutex listenersMutex;

    COMMO_DISALLOW_COPY(StreamingSocketManagement);
    void connectionThreadProcess();
    void ioThreadProcess();
    void recvQueueThreadProcess();
    void resolutionThreadProcess();

    void sendPing(ConnectionContext *ctx);
    void convertTxToProtoVersion(ConnectionContext *ctx, int protoVersion);
    bool checkProtoTimeout(ConnectionContext *ctx, const CommoTime &nowTime);
    bool protoNegotiation(ConnectionContext *ctx, CoTMessage *msg);
    bool dispatchRxMessage(ConnectionContext *ctx, size_t len, bool isXml);
    bool scanStreamData(ConnectionContext *ctx, size_t nNewBytes);

    std::string getEndpointString(const char *addr, int port, ConnType connType);
    // Must be holding relevant mutexes. Give clearIo as true to clean all
    // I/O related members to default state (as if freshly disconnected)
    void resetConnection(ConnectionContext *ctx, CommoTime nextConnTime, bool clearIo);

    bool connectionThreadCheckSsl(ConnectionContext *ctx) COMMO_THROW (SocketException);
    size_t ioThreadSslRead(ConnectionContext *ctx) COMMO_THROW (SocketException);
    size_t ioThreadSslWrite(ConnectionContext *ctx, const uint8_t *data, size_t n) COMMO_THROW (SocketException);
    size_t ioThreadWrite(ConnectionContext *ctx, const uint8_t *data, size_t n) COMMO_THROW (SocketException);

    void fireInterfaceChange(ConnectionContext *ctx, bool up);
    void fireInterfaceErr(ConnectionContext *ctx,
                          netinterfaceenums::NetInterfaceErrorCode errCode);

};

}
}
}

#endif /* IMPL_STREAMINGSOCKETMANAGEMENT_H_ */
