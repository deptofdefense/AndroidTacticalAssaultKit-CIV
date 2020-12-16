#include "simplefileiomanager.h"
#include "internalutils.h"
#include "platform.h"
#include <Lock.h>
#include <utility>
#include <sstream>
#include <cctype>
#include <set>
#include <algorithm>
#include <inttypes.h>
#include <string.h>
#include <memory>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

namespace {
    const char *THREAD_NAMES[] = {
        "cmosfio.io",
        "cmosfio.stat"
    };
}

SimpleFileIOManager::SimpleFileIOManager(CommoLogger *logger,
        SimpleFileIO *io,
        FileIOProviderTracker* factory) :
                ThreadedHandler(2, THREAD_NAMES), logger(logger),
                clientio(io),
                nextId(0),
                notRunningRequests(),
                notRunningRequestsMutex(),
                ioRequests(),
                ioRequestsMutex(),
                ioRequestsMonitor(),
                statusUpdates(),
                statusUpdatesMutex(),
                statusUpdatesMonitor(),
                curlMultiCtx(NULL),
                providerTracker(factory)
{
    curlMultiCtx = curl_multi_init();

    startThreads();
}

SimpleFileIOManager::~SimpleFileIOManager()
{
    stopThreads();

    curl_multi_cleanup(curlMultiCtx);

    // Clean up requests not yet picked up by IO thread
    std::map<int, IOContext *>::iterator iter;
    for (iter = notRunningRequests.begin(); iter != notRunningRequests.end();
              iter++)
    {
        IOContext *ioCtx = iter->second;
        delete ioCtx;
    }

    while (!ioRequests.empty()) {
        IOContext *ioCtx = ioRequests.back();
        ioRequests.pop_back();
        delete ioCtx;
    }
    
    // Clean up any remaining status callback requests
    while (!statusUpdates.empty()) {
        InternalFileIOUpdate *ioUpdate = statusUpdates.back();
        statusUpdates.pop_back();
        delete ioUpdate;
    }
    
}


/***************************************************************************/
// Public API

atakmap::commoncommo::CommoResult SimpleFileIOManager::uploadFile(
                           int *xferId,
                           const char *remoteURL,
                           const uint8_t *caCert, 
                           size_t caCertLen,
                           const char *caCertPassword,
                           const char *remoteUsername,
                           const char *remotePassword,
                           const char *localFileName)
{
    return queueFileIO(true, xferId, remoteURL, localFileName,
                       caCert, caCertLen, caCertPassword, remoteUsername,
                       remotePassword);
}

atakmap::commoncommo::CommoResult SimpleFileIOManager::downloadFile(
                           int *xferId, 
                           const char *localFileName,
                           const char *remoteURL,
                           const uint8_t *caCert, 
                           size_t caCertLen,
                           const char *caCertPassword,
                           const char *remoteUsername,
                           const char *remotePassword)
{
    return queueFileIO(false, xferId, remoteURL, localFileName,
                       caCert, caCertLen, caCertPassword, remoteUsername,
                       remotePassword);
}

atakmap::commoncommo::CommoResult SimpleFileIOManager::startTransfer(
                           int xferId)
{
    IOContext *ctx = NULL;
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, notRunningRequestsMutex);
        std::map<int, IOContext *>::iterator iter;
        iter = notRunningRequests.find(xferId);
        if (iter == notRunningRequests.end())
            return COMMO_ILLEGAL_ARGUMENT;
        ctx = iter->second;
        notRunningRequests.erase(iter);
    }

    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
            "SimpleIO: Starting transfer id %d",
            ctx->id);

    
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ioRequestsMutex);
    
        // Give upload to the upload thread
        ioRequests.push_front(ctx);
        ioRequestsMonitor.broadcast(*lock);
    }

    return COMMO_SUCCESS;
}


/***************************************************************************/
// ThreadedHandler


void SimpleFileIOManager::threadStopSignal(
        size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ioRequestsMutex);
        ioRequestsMonitor.broadcast(*lock);
        break;
    }
    case STATUS_THREADID:
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, statusUpdatesMutex);
        statusUpdatesMonitor.broadcast(*lock);
        break;
    }
    }
}

void SimpleFileIOManager::threadEntry(
        size_t threadNum)
{
    switch (threadNum) {
    case IO_THREADID:
        ioThreadProcess();
        break;
    case STATUS_THREADID:
        statusThreadProcess();
        break;
    }
}


/***************************************************************************/
// Status update thread


void SimpleFileIOManager::statusThreadProcess()
{
    while (!threadShouldStop(STATUS_THREADID)) {
        InternalFileIOUpdate *ioUpdate = NULL;
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, statusUpdatesMutex);
            if (statusUpdates.empty()) {
                statusUpdatesMonitor.wait(*lock);
                continue;
            }

            // Have at least one
            ioUpdate = statusUpdates.back();
            statusUpdates.pop_back();
        }

        clientio->fileTransferUpdate(ioUpdate);
        delete ioUpdate;
    }

}


/***************************************************************************/
// IO thread


void SimpleFileIOManager::ioThreadProcess()
{
    std::set<IOContext *> newRequests;
    std::set<IOContext *> curRequests;

    while (!threadShouldStop(IO_THREADID)) {
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, ioRequestsMutex);
            while (!ioRequests.empty()) {
                IOContext *ctx = ioRequests.back();
                ioRequests.pop_back();
                newRequests.insert(ctx);
            }
        }

        if (!newRequests.empty()) {
            std::set<IOContext *>::iterator newIter;
            for (newIter = newRequests.begin(); newIter != newRequests.end(); ++newIter) {
                IOContext *ioCtx = *newIter;
                try {
                    ioThreadInitCtx(ioCtx);
                } catch (IOStatusException &e) {
                    ioThreadTransferCompleted(ioCtx, e.errCode, e.what());
                    continue;
                }
                curRequests.insert(ioCtx);
            }
            newRequests.clear();
        }

        if (curRequests.empty()) {
            // Nap time - wait until a new request arrives
            while (!threadShouldStop(IO_THREADID)) {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, ioRequestsMutex);
                if (!ioRequests.empty())
                    break;
                ioRequestsMonitor.wait(*lock);
            }
            continue;
        }

        // We have at least one active transfer in the multi handle.
        // Wait for whatever curl thinks is interesting on any pending transfer
        // or, at most, 1 second allowing us to either pick up new transfer
        // requests or respond to request to terminate.
        int count = 0;
        curl_multi_wait(curlMultiCtx, NULL, 0, 1000, &count);

        // NOTE: see note in missionpackagemanager's receive thread
        // after curl_multi_wait() about not relying on count to
        // optimize when (not) to call curl_multi_perform()

        int activeTransferCount = (int)curRequests.size();
        count = activeTransferCount;
        curl_multi_perform(curlMultiCtx, &count);
        if (count == activeTransferCount)
            continue;


        struct CURLMsg *cmsg;
        do {
            count = 0;
            cmsg = curl_multi_info_read(curlMultiCtx, &count);
            if (cmsg && cmsg->msg == CURLMSG_DONE) {
                CURL *easyHandle = cmsg->easy_handle;
                CURLcode result = cmsg->data.result;

                curl_multi_remove_handle(curlMultiCtx, easyHandle);

                IOContext *ioCtx = NULL;
                curl_easy_getinfo(easyHandle, CURLINFO_PRIVATE, &ioCtx);

                ioCtx->curlCtx = NULL;
                curl_easy_cleanup(easyHandle);
                curRequests.erase(ioCtx);

                
                SimpleFileIOStatus status = FILEIO_OTHER_ERROR;
                switch (result) {
                case CURLE_OK: 
                    status = FILEIO_SUCCESS;
                    break;
                case CURLE_UNSUPPORTED_PROTOCOL:
                    status = FILEIO_URL_UNSUPPORTED;
                    break;
                case CURLE_URL_MALFORMAT:
                    status = FILEIO_URL_INVALID;
                    break;

                case CURLE_COULDNT_RESOLVE_HOST:
                case CURLE_FTP_CANT_GET_HOST:
                    status = FILEIO_HOST_RESOLUTION_FAIL;
                    break;
                
                case CURLE_COULDNT_CONNECT:
                    status = FILEIO_CONNECT_FAIL;
                    break;

                case CURLE_REMOTE_ACCESS_DENIED:
                case CURLE_UPLOAD_FAILED:
                    status = FILEIO_ACCESS_DENIED;
                    break;
                
                case CURLE_FTP_WEIRD_PASS_REPLY:
                case CURLE_LOGIN_DENIED:
                    status = FILEIO_AUTH_ERROR;
                    break;

                case CURLE_WRITE_ERROR:
                case CURLE_READ_ERROR:
                    status = FILEIO_LOCAL_IO_ERROR;
                    break;

                case CURLE_OPERATION_TIMEDOUT:
                    status = FILEIO_TRANSFER_TIMEOUT;
                    break;

                case CURLE_SSL_CONNECT_ERROR:
                case CURLE_USE_SSL_FAILED:
                case CURLE_PEER_FAILED_VERIFICATION:
                    status = FILEIO_SSL_OTHER_ERROR;
                    break;

                case CURLE_SSL_CACERT:
                    status = FILEIO_SSL_UNTRUSTED_SERVER;
                    break;
                
                case CURLE_REMOTE_FILE_NOT_FOUND:
                    status = FILEIO_URL_NO_RESOURCE;
                    break;
                
                default:
                    status = FILEIO_OTHER_ERROR;
                    break;
                }

                const char *info = NULL;
                if (result != CURLE_OK) {
                    InternalUtils::logprintf(logger,
                            CommoLogger::LEVEL_DEBUG,
                            "SimpleIO: transfer %d encountered error. cret %d info %s",
                            ioCtx->id, result,
                            ioCtx->curlErrBuf);

                    info = ioCtx->curlErrBuf;
                }
                ioThreadTransferCompleted(ioCtx, status, info);

            }
        } while (cmsg);
    }

    std::set<IOContext *>::iterator iter;
    for (iter = curRequests.begin(); iter != curRequests.end(); ++iter) {
        IOContext *ioCtx = *iter;
        if (ioCtx->curlCtx) {
            curl_multi_remove_handle(curlMultiCtx, ioCtx->curlCtx);
            curl_easy_cleanup(ioCtx->curlCtx);
        }
        delete ioCtx;
    }

}


size_t SimpleFileIOManager::curlWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    IOContext *ioCtx = (IOContext *)userCtx;
    size_t n = ioCtx->provider->write(buf, size, nmemb, ioCtx->localFile);

    return n * size;
}


size_t SimpleFileIOManager::curlReadCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    IOContext *ioCtx = (IOContext *)userCtx;
    size_t n = ioCtx->provider->read(buf, size, nmemb, ioCtx->localFile);

    return n * size;
}



CURLcode SimpleFileIOManager::curlSslCtxSetupRedir(CURL *curl, void *sslctx, void *privData)
{
    SimpleFileIOManager::IOContext *ioCtx = (SimpleFileIOManager::IOContext *)privData;
    return ioCtx->sslCtxSetup(curl, sslctx);
}

CURLcode SimpleFileIOManager::curlXferCallback(void *privData,
                                      curl_off_t dlTotal,
                                      curl_off_t dlNow,
                                      curl_off_t ulTotal,
                                      curl_off_t ulNow)
{
    SimpleFileIOManager::IOContext *ioCtx =
            (SimpleFileIOManager::IOContext *)privData;
    uint64_t oldVal = ioCtx->bytesTransferred;
    if (ioCtx->isUpload) {
        ioCtx->bytesTransferred = ulNow;
    } else {
        ioCtx->bytesTransferred = dlNow;
        ioCtx->localFileLen = dlTotal;
    }
    // Queue an update if #'s changed
    if (ioCtx->bytesTransferred != oldVal) {
        InternalFileIOUpdate *up = new InternalFileIOUpdate(
            ioCtx->id,
            FILEIO_INPROGRESS,
            NULL,
            ioCtx->bytesTransferred,
            ioCtx->localFileLen);
        ioCtx->owner->queueUpdate(up);
    }
    
    return CURLE_OK;
}


void SimpleFileIOManager::ioThreadInitCtx(IOContext *ioCtx) COMMO_THROW (IOStatusException)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
        "SimpleIO: IO Initiating %s - id %d URL %s username: %s local file %s",
        ioCtx->isUpload ? "upload" : "download",
        ioCtx->id, ioCtx->remoteURL.c_str(), ioCtx->useLogin ? ioCtx->remoteUser.c_str() : "(none)", ioCtx->localFileName.c_str());


    ioCtx->curlCtx = curl_easy_init();
    if (ioCtx->curlCtx == NULL)
        throw IOStatusException(FILEIO_OTHER_ERROR, "Could not init curl ctx");

    try {
        // Start by opening the local file; this can throw on failure
        ioCtx->openLocalFile();

        // SSL setup
        if (ioCtx->isSSL) {
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx,
                                        CURLOPT_SSL_CTX_FUNCTION,
                                        curlSslCtxSetupRedir));
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_SSL_CTX_DATA,
                                        ioCtx));
            // Never verify hostname per Shawn and current 
            // ATAK code at time of writing
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_SSL_VERIFYHOST, 
                                        0L));
            // If no cacerts were given, turn off ssl verification entirely
            // (accepts any cert from server)
            if (!ioCtx->caCerts)
                CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, 
                                            CURLOPT_SSL_VERIFYPEER, 0L));
            
            // Finally, inform curl to use SSL for everything
            // This is necessary for ftps
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_USE_SSL,
                                        CURLUSESSL_ALL)); 
        }

        // Login info if given
        if (ioCtx->useLogin) {
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_USERNAME,
                                        ioCtx->remoteUser.c_str()));
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_PASSWORD,
                                        ioCtx->remotePass.c_str()));
        }


        // Now basic transaction setup; first things that are
        // different for upload v. download
        if (ioCtx->isUpload) {
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx,
                                        CURLOPT_UPLOAD, 1L));
            
            
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_READFUNCTION,
                                        curlReadCallback));
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_READDATA,
                                        ioCtx));
            
            curl_off_t fileSize = ioCtx->localFileLen ? 
                                  ioCtx->localFileLen : -1;
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx,
                                        CURLOPT_INFILESIZE_LARGE,
                                        fileSize));
            
            
        } else {
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_WRITEFUNCTION,
                                        curlWriteCallback));
            CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_WRITEDATA,
                                        ioCtx));
        }


        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_URL,
                                    ioCtx->curlCompatURL.c_str()));
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_NOSIGNAL, 1L));
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_PRIVATE, ioCtx));
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_ERRORBUFFER,
                                    ioCtx->curlErrBuf));
        // 90seconds connect timeout; may want this configurable later
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_CONNECTTIMEOUT,
                                    90L));
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_LOW_SPEED_LIMIT, 10L));
        // 120seconds stall timeout; may want this configurable later
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_LOW_SPEED_TIME, 120L));
        
        // Progress callbacks
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_XFERINFOFUNCTION,
                                    curlXferCallback));
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_XFERINFODATA,
                                    ioCtx));
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_NOPROGRESS, 0L));
        
        // For FTP, we only want to use the connection ONCE; don't let multi
        // handle reuse the connection.  This prevents issues with SSL
        // connections to vsftpd with their config option require_ssl_reuse
        // set to YES (which is their default);  it wants to reuse the ssl
        // session, but curl doesn't support that really...
        CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_FORBID_REUSE, 1L));
        
    } catch (IOStatusException &e) {
        // Close local file if we opened it
        ioCtx->closeLocalFile();

        curl_easy_cleanup(ioCtx->curlCtx);
        ioCtx->curlCtx = NULL;
        throw e;
    }

    curl_multi_add_handle(curlMultiCtx, ioCtx->curlCtx);
}

void SimpleFileIOManager::
ioThreadTransferCompleted(IOContext *ioCtx, SimpleFileIOStatus status, 
                          const char *addlInfo)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
        "SimpleIO: id %d transfer %s - status %d (%s); transferred %" PRId64 " of %" PRId64,
        ioCtx->id, (status == FILEIO_SUCCESS) ? "succeeded" : "failed",
        status, addlInfo ? addlInfo : "",
        ioCtx->bytesTransferred, ioCtx->localFileLen);

    // Pass off to status update thread
    InternalFileIOUpdate *update = new InternalFileIOUpdate(
            ioCtx->id,
            status,
            addlInfo,
            ioCtx->bytesTransferred,
            ioCtx->localFileLen);

    queueUpdate(update);
    // Delete context, no longer needed
    delete ioCtx;
}


#if 0
void SimpleFileIOManager::ioThreadCleanSet(std::set<IOContext *> *upSet)
{
    std::set<IOContext *>::iterator iter;
    for (iter = upSet->begin(); iter != upSet->end(); ++iter) {
        IOContext *upCtx = *iter;
        if (upCtx->curlCtx) {
            curl_multi_remove_handle(uploadCurlMultiCtx, upCtx->curlCtx);
            curl_easy_cleanup(upCtx->curlCtx);
            if (upCtx->uploadData) {
                curl_formfree(upCtx->uploadData);
                upCtx->uploadData = NULL;
            }
        }
        delete upCtx;
    }

}
#endif



/***************************************************************************/
// TRASH FOR REFERENCE ONLY

#if 0
CURLcode SimpleFileIOManager::curlSslCtxSetupRedir(CURL *curl, void *sslctx, void *privData)
{
    SimpleFileIOManager::FileTransferContext *ftc = (SimpleFileIOManager::FileTransferContext *)privData;
    return ftc->sslCtxSetup(curl, sslctx);
}

size_t SimpleFileIOManager::curlWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    FileTransferContext *ftc = (FileTransferContext *)userCtx;
    if (!ftc->outputFile) {
        ftc->outputFile = fopen(ftc->localFilename.c_str(), "wb");
        if (!ftc->outputFile)
            return 0;
    }

    size_t n = fwrite(buf, size, nmemb, ftc->outputFile);
    if (n != nmemb)
        return 0;
    return nmemb * size;
}

#endif





/***************************************************************************/
// Private support functions


atakmap::commoncommo::CommoResult SimpleFileIOManager::queueFileIO(
                           bool forUpload,
                           int *xferId,
                           const char *remoteURL,
                           const char *localFileName,
                           const uint8_t *caCert, 
                           size_t caCertLen,
                           const char *caCertPassword,
                           const char *remoteUsername,
                           const char *remotePassword)
{
    try {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, notRunningRequestsMutex);

        auto provider(providerTracker->getCurrentProvider());
        IOContext *ctx = new IOContext(this, forUpload, nextId,
                                       remoteURL, localFileName,
                                       caCert, caCertLen, caCertPassword,
                                       remoteUsername, remotePassword,
                                       provider);
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
            "SimpleIO: Creating %s transfer transaction - id %d URL %s local file %s",
            ctx->isUpload ? "upload" : "download",
            ctx->id, ctx->remoteURL.c_str(), ctx->localFileName.c_str());

        nextId++;

        // Place transfer in non-run queue until caller starts transfer
        notRunningRequests[ctx->id] = ctx;

        *xferId = ctx->id;

    } catch (CommoResult &e) {
        return e;
    }
    return COMMO_SUCCESS;
}


void SimpleFileIOManager::
queueUpdate(InternalFileIOUpdate *update)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
        "SimpleIO: StatusUpdate id %d status %d xfercount %" PRIu64,
        update->xferid, update->status, update->bytesTransferred);
    
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, statusUpdatesMutex);

        // Give upload to the upload thread
        statusUpdates.push_front(update);
        statusUpdatesMonitor.broadcast(*lock);
    }
}





/***************************************************************************/
// Internal structs/classes

SimpleFileIOManager::IOContext::IOContext(
                  SimpleFileIOManager *owner,
                  bool isUpload, int id,
                  const char *remoteURL,
                  const char *localFileName,
                  const uint8_t *caCert,
                  const size_t caCertLen,
                  const char *caCertPassword,
                  const char *remoteUsername,
                  const char *remotePassword,
                  std::shared_ptr<FileIOProvider>& provider) COMMO_THROW (CommoResult) :
                          owner(owner),
                          isUpload(isUpload),
                          id(id),
                          remoteURL(remoteURL),
                          localFileName(localFileName),
                          useLogin(false),
                          remoteUser(""),
                          remotePass(""),
                          isSSL(false),
                          caCerts(NULL),
                          nCaCerts(0),
                          curlCompatURL(remoteURL),
                          curlCtx(NULL),
                          curlErrBuf(),
                          localFile(NULL),
                          localFileLen(0),
                          bytesTransferred(0),
                          provider(provider)
{
    // Check URL and see if protocol is supported and if it is ssl based
    size_t n = this->remoteURL.find(':');
    if (n == std::string::npos)
        throw COMMO_ILLEGAL_ARGUMENT;

    std::string proto = this->remoteURL.substr(0, n);
    std::transform(proto.begin(), proto.end(), proto.begin(), toupper);
    
    isSSL = false;
    if (proto.compare("FTPS") == 0) {
        isSSL = true;
        // ATAK as of time of this writing uses "ftps" for SSL-ftp,
        // and expects what is known as "implicit ssl ftp", which is basically
        // plain ftp inside an SSL session from the moment of connection.
        // Curl supports this using "ftps" URLs.
        // There is also "explicit ssl ftp", which works a lot like
        // SSL smtp where initially the connection is not using SSL
        // but then via simple command negotiation the session is swapped over
        // to SSL. For this, curl wants a simple ftp URL with
        // additional parameters set denoting the use of ssl
        //
        // Use this line to turn ftps into ftp if we want client-side
        // "ftps://" to mean explicit ssl support in normal ftp transactions
        // (curl is smart and tries to swap to ssl ftp based on options we set
        //  in curl, later, when isSSL is true)
        // curlCompatURL.erase(3, 1);
    } else if (proto.compare("FTP") != 0)
        throw COMMO_ILLEGAL_ARGUMENT;
    
    if (!remoteUsername && remotePassword)
        throw COMMO_ILLEGAL_ARGUMENT;
    if (remoteUsername) {
        remoteUser = remoteUsername;
        useLogin = true;
    }
    if (remotePassword)
        remotePass = remotePassword;
    
    // If ssl and a cert data buffer was given, try to parse
    if (isSSL && caCert) {
        try {
            InternalUtils::readCACerts(caCert, 
                                       caCertLen,
                                       caCertPassword,
                                       &caCerts,
                                       &nCaCerts);
            
        } catch (SSLArgException &e) {
            throw e.errCode;
        }
    }
    
}


SimpleFileIOManager::IOContext::~IOContext()
{
    closeLocalFile();

    if (caCerts) {
        sk_X509_pop_free(caCerts, X509_free);
    }
}

void SimpleFileIOManager::IOContext::openLocalFile() COMMO_THROW (IOStatusException)
{
    if (isUpload) {
        // Get file size
        if (!getFileSize(&localFileLen, localFileName.c_str()))
            throw IOStatusException(FILEIO_LOCAL_FILE_OPEN_FAILURE, "Could not determine file size - check path and permissions");

        localFile = provider->open(localFileName.c_str(), "rb");
    } else {
        localFile = provider->open(localFileName.c_str(), "wb");
    }
    if (!localFile)
        throw IOStatusException(FILEIO_LOCAL_FILE_OPEN_FAILURE, "Could not open file - check path and permissions");
}

void SimpleFileIOManager::IOContext::closeLocalFile()
{
    if (localFile != NULL) {
        provider->close(localFile);
        localFile = NULL;
    }
}

CURLcode SimpleFileIOManager::IOContext::sslCtxSetup(CURL *curl, void *vsslctx)
{
    SSL_CTX *sslCtx = (SSL_CTX *)vsslctx;

    if (caCerts) {
        X509_STORE *store = X509_STORE_new();
        if (!store)
            return CURLE_SSL_CERTPROBLEM;

        int nCaCerts = sk_X509_num(caCerts);
        for (int i = 0; i < nCaCerts; ++i)
            X509_STORE_add_cert(store, sk_X509_value(caCerts, i));

        SSL_CTX_set_cert_store(sslCtx, store);
    }

    return CURLE_OK;
}



InternalFileIOUpdate::InternalFileIOUpdate(
        const int xferid,
        const SimpleFileIOStatus status,
        const char *additionalInfo,
        uint64_t bytesTransferred,
        uint64_t totalBytesToTransfer) :
                SimpleFileIOUpdate(xferid, status,
                    additionalInfo ? new char[strlen(additionalInfo) + 1] : NULL,
                    bytesTransferred, totalBytesToTransfer)
{
    if (additionalInfo)
        strcpy(const_cast<char * const>(this->additionalInfo), additionalInfo);
}

InternalFileIOUpdate::~InternalFileIOUpdate()
{
    delete[] additionalInfo;
}
