#ifndef IMPL_MISSIONPACKAGEMANAGER_H_
#define IMPL_MISSIONPACKAGEMANAGER_H_


#include "missionpackage.h"
#include "commoresult.h"
#include "threadedhandler.h"
#include "contactmanager.h"
#include "datagramsocketmanagement.h"
#include "tcpsocketmanagement.h"
#include "streamingsocketmanagement.h"
#include "hwifscanner.h"
#include "commotime.h"
#include "commologger.h"
#include "fileioprovider.h"
#include "fileioprovidertracker.h"

#include <Mutex.h>
#include <Cond.h>
#include "curl/curl.h"
#include "microhttpd.h"

#include <string>
#include <memory>


namespace atakmap {
namespace commoncommo {
namespace impl
{

// Settings exposed in public commo api
struct MPTransferSettings
{
    MPTransferSettings();
    // Allow copies

    int getHttpPort();
    int getHttpsPort();
    int getNumTries();
    int getConnTimeoutSec();
    int getXferTimeoutSec();
    bool isServerTransferEnabled();

    void setHttpPort(int port) COMMO_THROW (std::invalid_argument);
    void setHttpsPort(int port) COMMO_THROW (std::invalid_argument);
    void setNumTries(int nTries) COMMO_THROW (std::invalid_argument);
    void setConnTimeoutSec(int connTimeoutSec) COMMO_THROW (std::invalid_argument);
    void setXferTimeoutSec(int xferTimeoutSec) COMMO_THROW (std::invalid_argument);
    void setServerTransferEnabled(bool en);

private:
    int httpPort;
    int httpsPort;
    int nTries;
    int connTimeoutSec;
    int xferTimeoutSec;
    bool serverTransferEnabled;
};


class MissionPackageManager : public ThreadedHandler, public DatagramListener,
                              public TcpMessageListener, 
                              public StreamingMessageListener
{
public:
    MissionPackageManager(CommoLogger *logger, ContactManager *contactMgr,
                          StreamingSocketManagement *streamMgr,
                          HWIFScanner *hwIfScanner,
                          MissionPackageIO *io,
                          const ContactUID *ourUID, std::string ourCallsign,
                          FileIOProviderTracker* factory)
                          COMMO_THROW (std::invalid_argument);
    virtual ~MissionPackageManager();

    // DatagramListener
    virtual void datagramReceivedGeneric(const std::string *endpointId, const uint8_t *data, size_t len);
    virtual void datagramReceived(const std::string *endpointId, const NetAddress *sender, const TakMessage *msg);
    // TcpMessageListener
    virtual void tcpMessageReceived(const NetAddress *sender, 
                                    const std::string *endpoint,
                                    const CoTMessage *msg);
    // StreamingMessageListener
    virtual void streamingMessageReceived(std::string streamingEndpoint, const CoTMessage *message);

    CommoResult sendFileInit(int *xferId, ContactList *destinations,
                         const char *filePath,
                         const char *fileName,
                         const char *name);
    CommoResult sendFileInit(int *xferId,
                         std::vector<const ContactUID *> *destinations,
                         const char *filePath,
                         const char *fileName,
                         const char *name);

    CommoResult uploadFileInit(int *xferId, 
                           const char *streamingRemoteId,
                           const char *filePath,
                           const char *fileName);

    CommoResult sendFileStart(int xferId);


    void setCallsign(const char *callsign);
    
    void setMPTransferSettings(const MPTransferSettings &settings);
    void setLocalPort(int localPort) COMMO_THROW (std::invalid_argument);
    void setLocalHttpsPort(int localPort);


protected:
    enum { RX_THREADID, TX_THREADID, TX_UPLOAD_THREADID, EVENT_THREADID };
    virtual void threadStopSignal(size_t threadNum);
    virtual void threadEntry(size_t threadNum);

private:
    struct MPStatusEvent {
        const MissionPackageSendStatusUpdate *tx;
        const MissionPackageReceiveStatusUpdate *rx;
        
        ~MPStatusEvent();

        static MPStatusEvent *createTxProgress(int id, 
            const InternalContactUID *recipient,
            MissionPackageTransferStatus status,
            const char *detail,
            uint64_t nbytes);

        static MPStatusEvent *createTxFinal(int id, 
            const InternalContactUID *recipient,
            MissionPackageTransferStatus status,
            const char *detail,
            uint64_t nbytes);

        static MPStatusEvent *createRx(const char *file, 
            MissionPackageTransferStatus status,
            uint64_t totalBytesReceived,
            uint64_t totalBytesExpected,
            int attempt,
            int maxAttempts,
            const char *detail);


      private:
        MPStatusEvent();
        COMMO_DISALLOW_COPY(MPStatusEvent);

        static MPStatusEvent *createTx(int id, 
            const InternalContactUID *recipient,
            MissionPackageTransferStatus status,
            const char *detail,
            uint64_t nbytes);

        // Only non-NULL for ones we should dispose of
        const InternalContactUID *txRecipient;
        std::string detail;
        std::string rxFile;

        FileIOProviderTracker* providerTracker;
    };

    struct TxTransferContext;

    struct TxAckInfo {
        TxAckInfo(const std::string &uid, const std::string &reason, const bool success, const std::string &senderuid, uint64_t transferSize);
        ~TxAckInfo();
        std::string uid;
        std::string reason;
        bool success;
        InternalContactUID *senderUid;
        uint64_t transferSize;
    };

    struct TxUploadContext {
        typedef enum {
            CHECK,
            UPLOAD,
            TOOLSET
        } State;

        TxUploadContext(TxTransferContext *owner,
                        std::shared_ptr<FileIOProvider>& ioProvider,
                        const std::string &streamEndpoint,
                        std::vector<const InternalContactUID *> *contacts = NULL);
        ~TxUploadContext();

        CURLcode sslCtxSetup(CURL *curl, void *sslctx);
        
        State state;

        std::string streamEndpoint;

        // Upload context completely owns this vector and the elements
        // If NULL, this is a server upload only (not a send to contacts)
        std::vector<const InternalContactUID *> *contacts;

        TxTransferContext * const owner;

        CURL *curlCtx;
        // buffer for CURL to store errors
        char curlErrBuf[CURL_ERROR_SIZE];
        // non-null during uploads
        struct curl_httppost *uploadData;
        struct curl_slist *headerList;

        std::string urlFromServer;
        uint64_t bytesTransferred;

        std::shared_ptr<FileIOProvider> ioProvider;
        FileHandle *localFile;
    };

    struct TxTransferContext {
        TxTransferContext(MissionPackageManager *owner, int id,
                const MPTransferSettings &settings,
                const std::string &fileToSend,
                const std::string &filename,
                const std::string &transferName,
                const uint64_t fileSize,
                const std::string &sha256hash);
        ~TxTransferContext();


        MissionPackageManager * const owner;
        const int id;
        
        // Settings when transfer was initiated
        MPTransferSettings settings;

        // Absolute path
        const std::string fileToSend;
        // Simple path
        const std::string filename;
        // "name" for the transfer
        const std::string transferName;
        // size of fileToSend
        const uint64_t fileSize;

        const std::string sha256hash;

        // uid->ack-uuid for local destinations
        // Only used while transfer is pending
        std::map<const InternalContactUID *, std::string> localContactToAck;
        // Outstanding uploads for this transfer context
        std::set<TxUploadContext *> uploadCtxs;
        // While size > 0, more acks might show up.
        std::map<std::string, const InternalContactUID *> outstandingAcks;
        // Acks for local transfers
        std::set<std::string> localAcks;
        

        // Can only be called holding txTransfersMutex
        bool isDone();
    };

    struct FileTransferContext {
        FileTransferContext(MissionPackageManager *owner,
                            const MPTransferSettings &settings,
                            const std::string &localFilename,
                            const std::string &sourceStreamEndpoint,
                            CoTFileTransferRequest *request,
                            std::shared_ptr<FileIOProvider>& provider);
        ~FileTransferContext();

        CURLcode sslCtxSetup(CURL *curl, void *sslctx);

        MissionPackageManager *owner;
        // Settings when transfer was initiated
        MPTransferSettings settings;
        std::string localFilename;
        // Empty string if not received from streaming source
        std::string sourceStreamEndpoint;
        CoTFileTransferRequest *request;

        // non-NULL if a transfer is in process with curlMultiCtx
        // NULL if not, in which case usingSenderURL and currentRetryTime
        // reflect what and when to try next.
        CURL *curlCtx;

        // buffer for CURL to store errors
        char curlErrBuf[CURL_ERROR_SIZE];
        
        // The original sender's URL, possibly modified from http
        // to https if our tranfer is a peer-hosted one and
        // https compatibility is present.  Otherwise just a copy
        // of the sender's URL as-is.
        std::string adjustedSenderUrl;

        // true if current request  (curlCtx != NULL) or next (curlCtx == NULL)
        // is (to be) using sender's original URL.  false if using our
        // own contact-path derived URL
        bool usingSenderURL;

        // True if the request URL from the sender is on a TAK server
        // false otherwise
        bool senderURLIsTAKServer;

        CommoTime nextRetryTime;

        // How many times we've tried (or are currently trying) to get the
        // remote file.  Counts pairs of attempts, against both sender original
        // URL and our contact-path derived URL
        int currentRetryCount;
        
        uint64_t bytesTransferred;

        FileHandle *outputFile;

        std::shared_ptr<FileIOProvider> provider;

    private:
        COMMO_DISALLOW_COPY(FileTransferContext);
    };

    CommoLogger *logger;
    MissionPackageIO *clientio;

    ContactManager *contactMgr;
    StreamingSocketManagement *streamMgr;
    HWIFScanner *hwIfScanner;
    const ContactUID *ourContactUID;
    std::string ourCallsign;
    PGSC::Thread::Mutex callsignMutex;

    MPTransferSettings xferSettings;


    CURLM *curlMultiCtx;

    // New transfers initiated but not picked up by RX thread yet
    // string is source server endpoint if from tak server, else empty string
    typedef std::deque<std::pair<std::string, CoTFileTransferRequest *>>
                                                              RxRequestDeque;
    RxRequestDeque rxRequests;
    PGSC::Thread::Mutex rxRequestsMutex;
    PGSC::Thread::CondVar rxRequestsMonitor;

    // local filename -> context
    typedef std::map<std::string, FileTransferContext *> RxTransferMap;
    RxTransferMap rxTransfers;

    // txtransferid -> txtransfercontext for the transfer
    typedef std::map<int, TxTransferContext *> TxTransferMap;

    // Initialized but not started transfers.
    // Protected by txTransfersMutex
    TxTransferMap pendingTxTransfers;

    // Map structure protected by below mutex.
    // Map txtransfercontext's themselves can only be destroyed
    // by TxThread.
    TxTransferMap txTransfers;
    PGSC::Thread::Mutex txTransfersMutex;

    typedef std::map<std::string, int> TxAcksMap;
    // Protected also by txTransfersMutex
    TxAcksMap acksToIds;

    int nextTxId;

    // acks received, to be processed by TX thread
    // uid's of the acks
    std::deque<TxAckInfo *> txAcks;
    PGSC::Thread::Mutex txAcksMutex;
    PGSC::Thread::CondVar txAcksMonitor;

    // New uploads initiated but not picked up by upload thread yet
    std::deque<TxUploadContext *> uploadRequests;
    PGSC::Thread::Mutex uploadRequestsMutex;
    PGSC::Thread::CondVar uploadRequestsMonitor;


    CURLM *uploadCurlMultiCtx;

    struct MHD_Daemon *webserver;
    int webPort;
    int httpsProxyPort;

    // Events to be fired by event thread in order
    std::deque<MPStatusEvent *> eventQueue;
    PGSC::Thread::Mutex eventQueueMutex;
    PGSC::Thread::CondVar eventQueueMonitor;


    static CURLcode curlSslCtxSetupRedir(CURL *curl, void *sslctx, void *privData);
    static CURLcode curlSslCtxSetupRedirUpload(CURL *curl, void *sslctx, void *privData);
    static size_t curlWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx);
    static size_t curlReadCallback(char *buf, size_t size, size_t nmemb, void *userCtx);
    static size_t curlUploadWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx);
    static CURLcode curlUploadXferCallback(void *privData,
                                          curl_off_t dlTotal,
                                          curl_off_t dlNow,
                                          curl_off_t ulTotal,
                                          curl_off_t ulNow);
    static CURLcode curlXferCallback(void *privData,
                                          curl_off_t dlTotal,
                                          curl_off_t dlNow,
                                          curl_off_t ulTotal,
                                          curl_off_t ulNow);

    void abortLocalTransfers();

    void receiveThreadProcess();
    void receiveSendAck(CoTFileTransferRequest *req, bool success,
                        const char *errMsg);

    void messageReceivedImpl(const std::string &streamingEndpoint, 
                             const CoTMessage *message);

    void transmitThreadProcess();
    void uploadThreadProcess();
    void eventThreadProcess();
    
    void queueEvent(MPStatusEvent *event);
    void queueRxEvent(FileTransferContext *ftc,
                      MissionPackageTransferStatus status,
                      const char *detail);
    void queueUploadProgressEvent(TxUploadContext *upCtx);

    static int webThreadAccessHandlerCallbackRedir(void *cls,
                                              struct MHD_Connection * connection,
                                              const char *url,
                                              const char *method,
                                              const char *version,
                                              const char *upload_data,
                                              size_t *upload_data_size,
                                              void **con_cls);
    int webThreadAccessHandlerCallback(struct MHD_Connection * connection,
                                              const char *url,
                                              const char *method,
                                              const char *version,
                                              const char *upload_data,
                                              size_t *upload_data_size,
                                              void **con_cls);


    CommoResult sendCoTRequest(const std::string &downloadUrl,
                        const std::string &ackUid,
                        const TxTransferContext *ctx,
                        const ContactUID *contact,
                        const int localHttpsPort);

    std::string getLocalUrl(int transferId);

    void uploadThreadCleanSet(std::set<TxUploadContext *> *upSet);
    void uploadThreadInitCtx(TxUploadContext *upCtx) COMMO_THROW (std::invalid_argument);
    void uploadThreadUploadCompleted(TxUploadContext *upCtx, bool success);

    FileIOProviderTracker* providerTracker;
};



}
}
}



#endif /* IMPL_MISSIONPACKAGEMANAGER_H_ */
