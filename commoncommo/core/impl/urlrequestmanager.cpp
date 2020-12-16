#include "urlrequestmanager.h"
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
        "urlreq.io",
        "urlreq.stat"
    };
}

URLRequestManager::URLRequestManager(CommoLogger *logger,
        FileIOProviderTracker* factory) :
                ThreadedHandler(2, THREAD_NAMES), logger(logger),
                nextId(0),
                notRunningRequests(),
                notRunningRequestsMutex(),
                ioRequests(),
                ioCurRequests(),
                ioRequestsMutex(),
                ioRequestsMonitor(),
                statusUpdates(),
                statusUpdatesMutex(),
                statusUpdatesMonitor(),
                statusThreadWaiting(false),
                curlMultiCtx(NULL),
                providerTracker(factory)
{
    curlMultiCtx = curl_multi_init();

    startThreads();
}

URLRequestManager::~URLRequestManager()
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
        StatusUpdate up = statusUpdates.back();
        URLIOUpdate *ioUpdate = up.second;
        statusUpdates.pop_back();
        delete ioUpdate;
    }
    
}


/***************************************************************************/
// Public API


void URLRequestManager::initRequest(
                           int *xferId, URLRequestIO *io,
                           URLRequest *req)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, notRunningRequestsMutex);

    auto provider(providerTracker->getCurrentProvider());
    IOContext *ctx = new IOContext(this, req, io,
                                   nextId, provider);
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
        "URLReq: Creating %s transfer transaction - id %d URL %s local %s",
        ctx->isUpload ? "upload" : "download",
        ctx->id, req->url.c_str(),
        (req->type != URLRequest::BUFFER_DOWNLOAD) ? req->localFileName.c_str() : "mem");
    
    nextId++;
    
    // Place transfer in non-run queue until caller starts transfer
    notRunningRequests[ctx->id] = ctx;
    
    *xferId = ctx->id;
}

void URLRequestManager::cancelRequest(int xferId)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, notRunningRequestsMutex);
        std::map<int, IOContext *>::iterator iter;
        iter = notRunningRequests.find(xferId);
        if (iter != notRunningRequests.end()) {
            IOContext *ctx = iter->second;
            delete ctx;
            notRunningRequests.erase(iter);
            return;
        }
    }

    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ioRequestsMutex);

        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, statusUpdatesMutex);
            // Wait for status thread to finish its queue
            // to avoid in-progress-of-firing event getting fired after
            // this for transfer of interest
            while (!statusThreadWaiting) {
                statusUpdatesMonitor.wait(*lock);
            }
        }
        
        {
        std::deque<IOContext *>::iterator iter;
            for (iter = ioRequests.begin(); iter != ioRequests.end(); ++iter) {
                IOContext *ctx = *iter;
                if (ctx->id == xferId) {
                    delete ctx;
                    ioRequests.erase(iter);
                    return;
                }
            }
        }
        
        {
            std::map<int, IOContext *>::iterator iter;
            iter = ioCurRequests.find(xferId);
            if (iter != ioCurRequests.end()) {
                iter->second->io = NULL;
                return;
            }
        }

    }
}

void URLRequestManager::cancelRequestsForIO(
                           URLRequestIO *io)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, notRunningRequestsMutex);
        std::map<int, IOContext *>::iterator iter;
        iter = notRunningRequests.begin();
        while (iter != notRunningRequests.end()) {
            IOContext *ctx = iter->second;
            if (ctx->io == io) {
                std::map<int, IOContext *>::iterator eraseMe = iter;
                iter++;
                delete ctx;
                notRunningRequests.erase(eraseMe);
            } else {
                iter++;
            }
        }
    }

    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ioRequestsMutex);

        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, statusUpdatesMutex);
            // Wait for status thread to finish its queue
            // to avoid in-progress-of-firing event getting fired after
            // this for transfer of interest
            while (!statusThreadWaiting) {
                statusUpdatesMonitor.wait(*lock);
            }
        }
        
        {
            std::deque<IOContext *>::iterator iter = ioRequests.begin();
            while (iter != ioRequests.end()) {
                IOContext *ctx = *iter;
                if (ctx->io == io) {
                    std::deque<IOContext *>::iterator eraseMe = iter;
                    iter++;
                    delete ctx;
                    ioRequests.erase(eraseMe);
                } else {
                    iter++;
                }
            }
        }

        {        
            std::map<int, IOContext *>::iterator iter = ioCurRequests.begin();
            for (iter = ioCurRequests.begin(); iter != ioCurRequests.end();
                                               iter++) {
                IOContext *ctx = iter->second;
                if (ctx->io == io) {
                    // Clear the io pointer, let the io thread handle clean-up
                    ctx->io = NULL;
                }
            }
        }

    }
}

atakmap::commoncommo::CommoResult URLRequestManager::startTransfer(
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
            "URLReq: Starting transfer id %d",
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


void URLRequestManager::threadStopSignal(
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

void URLRequestManager::threadEntry(
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


void URLRequestManager::statusThreadProcess()
{
    while (!threadShouldStop(STATUS_THREADID)) {
        StatusUpdate update;
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, statusUpdatesMutex);
            if (statusUpdates.empty()) {
                statusThreadWaiting = true;
                statusUpdatesMonitor.broadcast(*lock);
                statusUpdatesMonitor.wait(*lock);
                statusThreadWaiting = false;
                continue;
            }

            // Have at least one
            update = statusUpdates.back();
            statusUpdates.pop_back();
        }

        update.first->urlRequestUpdate(update.second);
        delete update.second;
    }

}


/***************************************************************************/
// IO thread


void URLRequestManager::ioThreadProcess()
{
    std::set<IOContext *> newRequests;

    while (!threadShouldStop(IO_THREADID)) {
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, ioRequestsMutex);
            while (!ioRequests.empty()) {
                IOContext *ctx = ioRequests.back();
                ioRequests.pop_back();
                newRequests.insert(ctx);
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
                    ioCurRequests[ioCtx->id] = ioCtx;
                }
                newRequests.clear();
            }
            
            ioThreadPurgeCancelled();

            if (ioCurRequests.empty()) {
                // Nap time - wait until a new request arrives
                while (!threadShouldStop(IO_THREADID)) {
                    if (!ioRequests.empty())
                        break;
                    ioRequestsMonitor.wait(*lock);
                }
                continue;
            }
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

        {
            // Grab lock here to protect against
            // cancels modifying the callback pointers of in-progress
            // transfers.
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, ioRequestsMutex);

            int activeTransferCount = (int)ioCurRequests.size();
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


                    
                    SimpleFileIOStatus status = FILEIO_OTHER_ERROR;
                    switch (result) {
                    case CURLE_OK: 
                        {
                            long resp;
                            curl_easy_getinfo(easyHandle,
                                              CURLINFO_RESPONSE_CODE, &resp);
                            status = ioCtx->request->statusForResponse((int)resp);
                            break;
                        }
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
                                "URLReq: transfer %d encountered error. cret %d info %s",
                                ioCtx->id, result,
                                ioCtx->curlErrBuf);

                        info = ioCtx->curlErrBuf;
                    }

                    ioCtx->curlCtx = NULL;
                    curl_easy_cleanup(easyHandle);
                    ioCurRequests.erase(ioCtx->id);

                    ioThreadTransferCompleted(ioCtx, status, info);

                }
            } while (cmsg);
        
        }
    }

    std::map<int, IOContext *>::iterator iter;
    for (iter = ioCurRequests.begin(); iter != ioCurRequests.end(); ++iter) {
        IOContext *ioCtx = iter->second;
        if (ioCtx->curlCtx) {
            curl_multi_remove_handle(curlMultiCtx, ioCtx->curlCtx);
            curl_easy_cleanup(ioCtx->curlCtx);
        }
        delete ioCtx;
    }

}


size_t URLRequestManager::curlWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    IOContext *ioCtx = (IOContext *)userCtx;
    size_t n;
    if (ioCtx->request->type == URLRequest::FILE_DOWNLOAD) {
        n = ioCtx->provider->write(buf, size, nmemb, ioCtx->localFile);
    } else {
        ioCtx->request->downloadedData((uint8_t *)buf, nmemb);
        n = nmemb;
    }

    return n * size;
}


size_t URLRequestManager::curlReadCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    IOContext *ioCtx = (IOContext *)userCtx;
    size_t n = ioCtx->provider->read(buf, size, nmemb, ioCtx->localFile);

    return n * size;
}



CURLcode URLRequestManager::curlSslCtxSetupRedir(CURL *curl, void *sslctx, void *privData)
{
    URLRequestManager::IOContext *ioCtx = (URLRequestManager::IOContext *)privData;
    return ioCtx->sslCtxSetup(curl, sslctx);
}

CURLcode URLRequestManager::curlXferCallback(void *privData,
                                      curl_off_t dlTotal,
                                      curl_off_t dlNow,
                                      curl_off_t ulTotal,
                                      curl_off_t ulNow)
{
    URLRequestManager::IOContext *ioCtx =
            (URLRequestManager::IOContext *)privData;
    uint64_t oldVal = ioCtx->bytesTransferred;
    if (ioCtx->isUpload) {
        ioCtx->bytesTransferred = ulNow;
    } else {
        ioCtx->bytesTransferred = dlNow;
        ioCtx->localFileLen = dlTotal;
    }
    // Queue an update if #'s changed *and* not cancelled
    if (ioCtx->bytesTransferred != oldVal && ioCtx->io) {
        URLIOUpdate *up = ioCtx->request->createUpdate(
            ioCtx->id,
            FILEIO_INPROGRESS,
            NULL,
            ioCtx->bytesTransferred,
            ioCtx->localFileLen);
        ioCtx->owner->queueUpdate(ioCtx->io, up);
    }
    
    return CURLE_OK;
}

void URLRequestManager::ioThreadPurgeCancelled()
{
    std::map<int, IOContext *>::iterator iter = ioCurRequests.begin();
    while (iter != ioCurRequests.end()) {
        if (!iter->second->io) {
            std::map<int, IOContext *>::iterator eraseMe = iter;
            iter++;
            // Cancelled requests don't get event fires for completion.
            IOContext *ioCtx = eraseMe->second;
            curl_multi_remove_handle(curlMultiCtx, ioCtx->curlCtx);
            curl_easy_cleanup(ioCtx->curlCtx);
            ioCtx->curlCtx = NULL;
            
            delete ioCtx;
            ioCurRequests.erase(eraseMe);

        } else {
            iter++;
        }
    }
}

void URLRequestManager::ioThreadInitCtx(IOContext *ioCtx) COMMO_THROW (IOStatusException)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
        "URLReq: IO Initiating %s - id %d URL %s local file %s",
        ioCtx->isUpload ? "upload" : "download",
        ioCtx->id, ioCtx->request->url.c_str(),
        ioCtx->request->type == URLRequest::BUFFER_DOWNLOAD ? "mem" : ioCtx->request->localFileName.c_str());

    ioCtx->curlCtx = curl_easy_init();
    if (ioCtx->curlCtx == NULL)
        throw IOStatusException(FILEIO_OTHER_ERROR, "Could not init curl ctx");

    try {
        // Start by opening the local file if needed; this can throw
        // on failure
        ioCtx->openLocalFile();

        // SSL setup
        if (ioCtx->request->isSSL) {
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
            if (!ioCtx->request->caCerts)
                CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, 
                                            CURLOPT_SSL_VERIFYPEER, 0L));
            
            // Finally, inform curl to use SSL for everything
            // This is necessary for ftps
            ///  XXXX - this needs to exist for FTP
            //CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_USE_SSL,
            //                            CURLUSESSL_ALL)); 
        }

        // Login info if given
        // XXXX - FTP only
        //if (ioCtx->useLogin) {
        //    CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_USERNAME,
        //                                ioCtx->remoteUser.c_str()));
        //    CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_PASSWORD,
        //                                ioCtx->remotePass.c_str()));
        //}


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
                                    ioCtx->request->url.c_str()));
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
        // XXXXX - FTP only
        //CURL_CHECK(curl_easy_setopt(ioCtx->curlCtx, CURLOPT_FORBID_REUSE, 1L));

        ioCtx->request->curlExtraConfig(ioCtx->curlCtx);
        
    } catch (IOStatusException &e) {
        // Close local file if we opened it
        ioCtx->closeLocalFile();

        curl_easy_cleanup(ioCtx->curlCtx);
        ioCtx->curlCtx = NULL;
        throw e;
    }

    curl_multi_add_handle(curlMultiCtx, ioCtx->curlCtx);
}

void URLRequestManager::
ioThreadTransferCompleted(IOContext *ioCtx, SimpleFileIOStatus status, 
                          const char *addlInfo)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
        "URLReq: id %d transfer %s - status %d (%s); transferred %" PRId64 " of %" PRId64,
        ioCtx->id, (status == FILEIO_SUCCESS) ? "succeeded" : "failed",
        status, addlInfo ? addlInfo : "",
        ioCtx->bytesTransferred, ioCtx->localFileLen);

    // Pass off to status update thread
    if (ioCtx->io) {
        URLIOUpdate *update = ioCtx->request->createUpdate(
                ioCtx->id,
                status,
                addlInfo,
                ioCtx->bytesTransferred,
                ioCtx->localFileLen);

        queueUpdate(ioCtx->io, update);
    }
    // Delete context, no longer needed
    delete ioCtx;
}




/***************************************************************************/
// Private support functions


void URLRequestManager::
queueUpdate(URLRequestIO *receiver, URLIOUpdate *update)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
        "URLReq: StatusUpdate id %d status %d xfercount %" PRIu64,
        update->getBaseUpdate()->xferid, 
        update->getBaseUpdate()->status,
        update->getBaseUpdate()->bytesTransferred);
    
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, statusUpdatesMutex);

        // Give upload to the upload thread
        statusUpdates.push_front(StatusUpdate(receiver, update));
        statusUpdatesMonitor.broadcast(*lock);
    }
}





/***************************************************************************/
// Internal structs/classes

URLRequestManager::IOContext::IOContext(
                  URLRequestManager *owner,
                  URLRequest *req,
                  URLRequestIO *io,
                  int id,
                  std::shared_ptr<FileIOProvider>& provider) :
                          owner(owner),
                          io(io),
                          request(req),
                          isUpload(req->type == URLRequest::FILE_UPLOAD),
                          id(id),
                          curlCtx(NULL),
                          curlErrBuf(),
                          localFile(NULL),
                          localFileLen(0),
                          bytesTransferred(0),
                          provider(provider)
{
}


URLRequestManager::IOContext::~IOContext()
{
    closeLocalFile();
    delete request;
}

void URLRequestManager::IOContext::openLocalFile() COMMO_THROW (IOStatusException)
{
    if (isUpload) {
        // Get file size
        if (!getFileSize(&localFileLen, request->localFileName.c_str()))
            throw IOStatusException(FILEIO_LOCAL_FILE_OPEN_FAILURE, "Could not determine file size - check path and permissions");

        localFile = provider->open(request->localFileName.c_str(), "rb");
    } else if (request->type == URLRequest::FILE_DOWNLOAD) {
        localFile = provider->open(request->localFileName.c_str(), "wb");
    } else {
        // No local file needed/used
        return;
    }
    if (!localFile)
        throw IOStatusException(FILEIO_LOCAL_FILE_OPEN_FAILURE, "Could not open file - check path and permissions");
}

void URLRequestManager::IOContext::closeLocalFile()
{
    if (localFile != NULL) {
        provider->close(localFile);
        localFile = NULL;
    }
}

CURLcode URLRequestManager::IOContext::sslCtxSetup(CURL *curl, void *vsslctx)
{
    SSL_CTX *sslCtx = (SSL_CTX *)vsslctx;

    if (request->caCerts) {
        X509_STORE *store = X509_STORE_new();
        if (!store)
            return CURLE_SSL_CERTPROBLEM;

        int nCaCerts = sk_X509_num(request->caCerts);
        for (int i = 0; i < nCaCerts; ++i)
            X509_STORE_add_cert(store, sk_X509_value(request->caCerts, i));

        SSL_CTX_set_cert_store(sslCtx, store);
    }

    return CURLE_OK;
}

URLIOUpdate::URLIOUpdate()
{
}

URLIOUpdate::~URLIOUpdate()
{
}

URLRequest::URLRequest(URLRequestType type, const std::string &url,
               const char *localFileName, 
               bool useSSL,
               const uint8_t *caCert,
               const size_t caCertLen,
               const char *caCertPassword) COMMO_THROW (CommoResult) :
                           type(type),
                           url(url),
                           localFileName(localFileName),
                           isSSL(useSSL),
                           caCerts(NULL)
{
    if (useSSL && caCert) {
        try {
            int nCaCerts = 0;
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

URLRequest::URLRequest(URLRequestType type, const std::string &url,
                       const char *localFileName,
                       bool useSSL,
                       STACK_OF(X509) *caCerts) :
                           type(type),
                           url(url),
                           localFileName(localFileName),
                           isSSL(useSSL),
                           caCerts(NULL)
{
    if (isSSL && caCerts)
        this->caCerts = X509_chain_up_ref(caCerts);
}

URLRequest::~URLRequest()
{
    cleanup();
}

void URLRequest::cleanup()
{
    if (caCerts) {
        sk_X509_pop_free(caCerts, X509_free);
        caCerts = NULL;
    }
}

