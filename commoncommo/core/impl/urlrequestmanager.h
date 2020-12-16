#ifndef IMPL_URLREQUESTMANAGER_H_
#define IMPL_URLREQUESTMANAGER_H_


#include "simplefileio.h"
#include "commoresult.h"
#include "internalutils.h"
#include "threadedhandler.h"
#include "commologger.h"
#include "fileioprovider.h"
#include "fileioprovidertracker.h"

#include <Mutex.h>
#include <Cond.h>
#include "curl/curl.h"

#include "openssl/ssl.h"

#include <deque>
#include <map>
#include <memory>
#include <stdexcept>


namespace atakmap {
namespace commoncommo {
namespace impl
{

#define CURL_CHECK(a) if ((a) != 0) throw IOStatusException(FILEIO_OTHER_ERROR, "Failed to set curl option")


class IOStatusException : public std::invalid_argument
{
public:
    explicit IOStatusException(SimpleFileIOStatus errCode,
                               const std::string &what) :
                     std::invalid_argument(what), errCode(errCode)
    {
    }

    const SimpleFileIOStatus errCode;
};


struct URLIOUpdate
{
    URLIOUpdate();
    virtual ~URLIOUpdate();
    
    virtual SimpleFileIOUpdate *getBaseUpdate() = 0;
    
private:
    COMMO_DISALLOW_COPY(URLIOUpdate);
};





struct URLRequest
{
    enum URLRequestType
    {
        FILE_DOWNLOAD,
        FILE_UPLOAD,
        BUFFER_DOWNLOAD,
    };

    const URLRequestType type;
    const std::string url;
    const std::string localFileName;

    bool isSSL;
        
    // These 2 might be NULL even for SSL, in which case
    //   we accept any cert from server
    // Not used and NULL for non-ssl
    STACK_OF(X509) *caCerts;

    // SSL parsed from given certs.  Can be NULL/0 and SSL
    // will just allow all certs.
    URLRequest(URLRequestType type, const std::string &url,
               const char *localFileName, 
               bool useSSL,
               const uint8_t *caCert,
               const size_t caCertLen,
               const char *caCertPassword) COMMO_THROW (CommoResult);
    // SSL with already read-in certs.
    URLRequest(URLRequestType type, const std::string &url,
               const char *localFileName, 
               bool useSSL,
               STACK_OF(X509) *caCerts);
    virtual ~URLRequest();
    void cleanup();
    
    virtual void curlExtraConfig(CURL *curlCtx) 
                           COMMO_THROW (IOStatusException) {};
    virtual SimpleFileIOStatus statusForResponse(int response) {
        return FILEIO_SUCCESS;
    }
    virtual URLIOUpdate *createUpdate(
        const int xferid,
        SimpleFileIOStatus status,
        const char *additionalInfo,
        uint64_t bytesTransferred,
        uint64_t totalBytesToTransfer) = 0;

    // Repeatedly invoked for data retrieved when type == BUFFER_DOWNLOAD
    virtual void downloadedData(uint8_t *data, size_t len) {};
};


class URLRequestIO
{
public:
    virtual void urlRequestUpdate(URLIOUpdate *update) = 0;

protected:
    URLRequestIO() {};
    virtual ~URLRequestIO() {};

private:
    COMMO_DISALLOW_COPY(URLRequestIO);
};



class URLRequestManager : public ThreadedHandler
{
public:
    URLRequestManager(CommoLogger *logger, FileIOProviderTracker* factory);
    virtual ~URLRequestManager();

    void initRequest(int *xferId, URLRequestIO *io,
                     URLRequest *req);
    void cancelRequestsForIO(URLRequestIO *io);
    void cancelRequest(int xferId);
    CommoResult startTransfer(int xferId);

protected:
    enum { IO_THREADID, STATUS_THREADID };
    virtual void threadStopSignal(size_t threadNum);
    virtual void threadEntry(size_t threadNum);

private:

    struct IOContext {
        IOContext(URLRequestManager *owner,
                  URLRequest *req,
                  URLRequestIO *io,
                  int id,
                  std::shared_ptr<FileIOProvider>& provider);
        ~IOContext();

        void openLocalFile() COMMO_THROW (IOStatusException);
        void closeLocalFile();
        CURLcode sslCtxSetup(CURL *curl, void *sslctx);

        URLRequestManager *owner;
        // io pointer can be nulled in a transfer that is active
        // to signify the transfer has been cancelled.
        // Lock use on ioRequestsMutex
        URLRequestIO *io;
        URLRequest *request;

        const bool isUpload;
        const int id;

        CURL *curlCtx;

        // buffer for CURL to store errors
        char curlErrBuf[CURL_ERROR_SIZE];

        // Opened as up/download starts. NULL for non-file requests
        FileHandle *localFile;

        // Uploads: Set as upload starts by us
        // Downloads: Set as download progresses once it is known
        // 0 if unknown
        uint64_t localFileLen;

        // 0 at start, updated as transfer is ongoing by io callbacks
        uint64_t bytesTransferred;

        std::shared_ptr<FileIOProvider> provider;
    };

    CommoLogger *logger;

    int nextId;

    // New transfers initialized but not yet started
    std::map<int, IOContext *> notRunningRequests;
    PGSC::Thread::Mutex notRunningRequestsMutex;
    
    // New transfers that have been started but not picked up by io thread yet
    std::deque<IOContext *> ioRequests;
    // All requests held by the io thread; only IO thread may modify structure,
    // even if holding lock
    std::map<int, IOContext *> ioCurRequests;
    // These protect the above 2
    PGSC::Thread::Mutex ioRequestsMutex;
    PGSC::Thread::CondVar ioRequestsMonitor;

    // Status dispatch queue
    typedef std::pair<URLRequestIO *, URLIOUpdate *> StatusUpdate;
    std::deque<StatusUpdate> statusUpdates;
    PGSC::Thread::Mutex statusUpdatesMutex;
    PGSC::Thread::CondVar statusUpdatesMonitor;
    bool statusThreadWaiting;

    


    CURLM *curlMultiCtx;

    FileIOProviderTracker* providerTracker;




    void statusThreadProcess();
    void ioThreadProcess();

    void ioThreadPurgeCancelled();

    void ioThreadInitCtx(IOContext *ctx) COMMO_THROW (IOStatusException);
    void ioThreadTransferCompleted(IOContext *ioCtx, SimpleFileIOStatus status, const char *addlInfo);
    

    void queueUpdate(URLRequestIO *receiver, URLIOUpdate *update);


    static CURLcode curlSslCtxSetupRedir(CURL *curl, void *sslctx, void *privData);
    static CURLcode curlXferCallback(void *privData, curl_off_t dlTotal,
                                      curl_off_t dlNow,
                                      curl_off_t ulTotal,
                                      curl_off_t ulNow);
    static size_t curlWriteCallback(char *buf, size_t size,
                                    size_t nmemb, void *userCtx);
    static size_t curlReadCallback(char *buf, size_t size,
                                    size_t nmemb, void *userCtx);


};



}
}
}



#endif /* IMPL_URLREQUESTMANAGER_H_ */
