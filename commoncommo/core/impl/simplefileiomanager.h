#ifndef IMPL_SIMPLEFILEIOMANAGER_H_
#define IMPL_SIMPLEFILEIOMANAGER_H_


#include "simplefileio.h"
#include "commoresult.h"
#include "urlrequestmanager.h"
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

struct InternalFileIOUpdate : public SimpleFileIOUpdate
{
    InternalFileIOUpdate(const int xferid,
        const SimpleFileIOStatus status,
        const char *additionalInfo,
        uint64_t bytesTransferred,
        uint64_t totalBytesToTransfer);
    ~InternalFileIOUpdate();
    
private:
    COMMO_DISALLOW_COPY(InternalFileIOUpdate);
};



class SimpleFileIOManager : public ThreadedHandler
{
public:
    SimpleFileIOManager(CommoLogger *logger, SimpleFileIO *io, FileIOProviderTracker* factory);
    virtual ~SimpleFileIOManager();

    CommoResult uploadFile(int *xferId, const char *remoteURL,
                           const uint8_t *caCert, 
                           size_t caCertLen,
                           const char *caCertPassword,
                           const char *remoteUsername,
                           const char *remotePassword,
                           const char *localFileName);
    CommoResult downloadFile(int *xferId, 
                           const char *localFileName,
                           const char *remoteURL,
                           const uint8_t *caCert, 
                           size_t caCertLen,
                           const char *caCertPassword,
                           const char *remoteUsername,
                           const char *remotePassword);
                           
    CommoResult startTransfer(int xferId);

protected:
    enum { IO_THREADID, STATUS_THREADID };
    virtual void threadStopSignal(size_t threadNum);
    virtual void threadEntry(size_t threadNum);

private:

    struct IOContext {
        IOContext(SimpleFileIOManager *owner,
                  bool isUpload, int id,
                  const char *remoteURL,
                  const char *localFileName,
                  const uint8_t *caCert,
                  const size_t caCertLen,
                  const char *caCertPassword,
                  const char *remoteUsername,
                  const char *remotePassword,
                  std::shared_ptr<FileIOProvider>& provider) COMMO_THROW (CommoResult);
        ~IOContext();

        void openLocalFile() COMMO_THROW (IOStatusException);
        void closeLocalFile();
        CURLcode sslCtxSetup(CURL *curl, void *sslctx);
        

        SimpleFileIOManager *owner;
        const bool isUpload;
        const int id;
        
        const std::string remoteURL;
        const std::string localFileName;
        bool useLogin;
        std::string remoteUser;
        std::string remotePass;
        bool isSSL;
        
        // These 2 might be NULL even for SSL, in which case
        //   we accept any cert from server
        // Not used and NULL/0 for non-ssl
        STACK_OF(X509) *caCerts;
        int nCaCerts;

        std::string curlCompatURL;

        CURL *curlCtx;

        // buffer for CURL to store errors
        char curlErrBuf[CURL_ERROR_SIZE];
        
        // Opened as up/download starts
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
    SimpleFileIO *clientio;

    int nextId;

    // New transfers initialized but not yet started
    std::map<int, IOContext *> notRunningRequests;
    PGSC::Thread::Mutex notRunningRequestsMutex;
    
    // New transfers that have been started but not picked up by io thread yet
    std::deque<IOContext *> ioRequests;
    PGSC::Thread::Mutex ioRequestsMutex;
    PGSC::Thread::CondVar ioRequestsMonitor;

    // Status dispatch queue
    std::deque<InternalFileIOUpdate *> statusUpdates;
    PGSC::Thread::Mutex statusUpdatesMutex;
    PGSC::Thread::CondVar statusUpdatesMonitor;


    CURLM *curlMultiCtx;

    FileIOProviderTracker *providerTracker;




    void statusThreadProcess();
    void ioThreadProcess();


    void ioThreadInitCtx(IOContext *ctx) COMMO_THROW (IOStatusException);
    void ioThreadTransferCompleted(IOContext *ioCtx, SimpleFileIOStatus status, const char *addlInfo);
    

    CommoResult queueFileIO(bool forUpload,
                            int *xferId,
                            const char *remoteURL,
                            const char *localFileName,
                            const uint8_t *caCert, 
                            size_t caCertLen,
                            const char *caCertPassword,
                            const char *remoteUsername,
                            const char *remotePassword);
    void queueUpdate(InternalFileIOUpdate *update);


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



#endif /* IMPL_SIMPLEFILEIOMANAGER_H_ */
