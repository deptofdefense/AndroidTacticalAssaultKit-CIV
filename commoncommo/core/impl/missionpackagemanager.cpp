#include "missionpackagemanager.h"
#include "fileioprovider.h"
#include <Lock.h>
#include <utility>
#include <sstream>
#include <string.h>
#include <memory>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;


namespace {
    const int DEFAULT_HTTP_PORT = 8080;
    const int DEFAULT_HTTPS_PORT = 8443;
    const int MIN_TRY_COUNT = 1;
    const int DEFAULT_TRY_COUNT = 10;
    const int MIN_CONN_TIMEOUT_SEC = 5;
    const int DEFAULT_CONN_TIMEOUT_SEC = 90;
    const int MIN_XFER_TIMEOUT_SEC = 15;
    const int DEFAULT_XFER_TIMEOUT_SEC = 120;

    const char *THREAD_NAMES[] = {
        "cmompmgr.rx", 
        "cmompmgr.tx",
        "cmompmgr.txup",
        "cmompmgr.evnt",
    };
    
    // Seconds before a fail transfer is retried
    const float RETRY_SECONDS = 10.0f;

    struct WebserverFileContext{
        FileHandle* handle;
        std::shared_ptr<FileIOProvider> provider;
    };

    void webserverReaderFree(void *opaque) {
        auto context ((WebserverFileContext *)opaque);
        FileHandle *f = context->handle;
        std::shared_ptr<FileIOProvider> provider = context->provider;
        provider->close(f);
        delete context;
    }
    ssize_t webserverReaderCallback(void *opaque, uint64_t pos, char *buf, size_t max)
    {
        auto context ((WebserverFileContext *)opaque);
        FileHandle *f = context->handle;
        std::shared_ptr<FileIOProvider> provider = context->provider;
        // We do not re-use responses, so MHD assures pos is equal to our
        // cumulative file position; we can ignore it
        size_t n = provider->read(buf, 1, max, f);
        if (n == 0) {
            if (provider->eof(f))
                return MHD_CONTENT_READER_END_OF_STREAM;
            else
                return MHD_CONTENT_READER_END_WITH_ERROR;
        }
        return n;
    }
}

namespace atakmap {
namespace commoncommo {
namespace impl {
    struct InternalMissionPackageSendStatusUpdate : public MissionPackageSendStatusUpdate
    {
        InternalMissionPackageSendStatusUpdate(const int xferid,
                const ContactUID *contact,
                const MissionPackageTransferStatus status,
                const char *detail,
                uint64_t totalBytes) :
                    MissionPackageSendStatusUpdate(xferid, contact,
                                                   status, detail,
                                                   totalBytes)
        {

        }
    };

    struct InternalMissionPackageReceiveStatusUpdate : public MissionPackageReceiveStatusUpdate
    {
        InternalMissionPackageReceiveStatusUpdate(const char *localFile,
                const MissionPackageTransferStatus status,
                uint64_t totalBytesReceived,
                uint64_t totalBytesExpected,
                int attempt,
                int maxAttempts,
                const char *errorDetail) :
                    MissionPackageReceiveStatusUpdate(localFile,
                                                   status, totalBytesReceived,
                                                   totalBytesExpected,
                                                   attempt,
                                                   maxAttempts,
                                                   errorDetail)
        {

        }
    };

}
}
}



MissionPackageManager::MissionPackageManager(CommoLogger *logger,
        ContactManager *contactMgr,
        StreamingSocketManagement *streamMgr, HWIFScanner *hwIfScanner,
        MissionPackageIO *io, const ContactUID *ourUID,
        std::string ourCallsign,
        FileIOProviderTracker* factory)
        COMMO_THROW (std::invalid_argument) :
                ThreadedHandler(4, THREAD_NAMES), DatagramListener(),
                TcpMessageListener(),
                StreamingMessageListener(), logger(logger),
                clientio(io), contactMgr(contactMgr),
                streamMgr(streamMgr),
                hwIfScanner(hwIfScanner),
                ourContactUID(ourUID), ourCallsign(ourCallsign),
                callsignMutex(),
                curlMultiCtx(NULL),
                rxRequests(), rxRequestsMutex(),
                rxRequestsMonitor(), rxTransfers(),
                txTransfers(), txTransfersMutex(), acksToIds(),
                nextTxId(0), txAcks(), txAcksMutex(),
                txAcksMonitor(), uploadRequests(),
                uploadRequestsMutex(),
                uploadRequestsMonitor(),
                uploadCurlMultiCtx(NULL),
                webserver(NULL),
                webPort(MP_LOCAL_PORT_DISABLE),
                httpsProxyPort(MP_LOCAL_PORT_DISABLE),
                eventQueue(),
                eventQueueMutex(),
                eventQueueMonitor(),
                providerTracker(factory)
{
    curlMultiCtx = curl_multi_init();
    uploadCurlMultiCtx = curl_multi_init();

    startThreads();
}

MissionPackageManager::~MissionPackageManager()
{
    stopThreads();

    // Stop the web server
    if (webserver != NULL)
        MHD_stop_daemon(webserver);

    curl_multi_cleanup(uploadCurlMultiCtx);
    curl_multi_cleanup(curlMultiCtx);
    // The rx thread cleaned all in progress transfers, but we need
    // to clean pending ones
    while (!rxRequests.empty()) {
        RxRequestDeque::reference v = rxRequests.back();
        CoTFileTransferRequest *r = v.second;
        rxRequests.pop_back();
        delete r;
    }
    
    // Clean up outstanding acks
    while (!txAcks.empty()) {
        TxAckInfo *ack = txAcks.back();
        txAcks.pop_back();
        delete ack;
    }

    // Clean up uploads that were created but not picked up by the upload
    // thread yet.
    while (!uploadRequests.empty()) {
        TxUploadContext *upCtx = uploadRequests.back();
        uploadRequests.pop_back();
        delete upCtx;
    }

    // Clean up remaining transmits; no need to clean up the contained uploads
    // as they were cleaned by the upload thread exiting or the above;
    // contacts in the outstanding ack map do need cleanup
    TxTransferMap::iterator iter;
    for (iter = txTransfers.begin(); iter != txTransfers.end(); ++iter) {
        std::map<std::string, const InternalContactUID *>::iterator imap;
        for (imap = iter->second->outstandingAcks.begin();
                     imap != iter->second->outstandingAcks.end();
                     imap++)
            delete imap->second;
        delete iter->second;
    }
    
    // Clean up any pending transmits.
    // For these, we have to clean up the localContactToAck
    // and the uploadCtx's
    for (iter = pendingTxTransfers.begin(); iter != pendingTxTransfers.end(); ++iter) {
        TxTransferContext *ctx = iter->second;
        for (std::set<TxUploadContext *>::iterator i = ctx->uploadCtxs.begin();
                                        i != ctx->uploadCtxs.end(); ++i)
            delete *i;

        for (std::map<const InternalContactUID *, std::string>::iterator i =
                     ctx->localContactToAck.begin(); 
                     i != ctx->localContactToAck.end(); ++i)
            delete i->first;
        
        delete ctx;
    }
    
    // Destroy remaining events
    std::deque<MPStatusEvent *>::iterator evIter;
    for (evIter = eventQueue.begin(); evIter != eventQueue.end(); ++evIter)
        delete *evIter;
}


/***************************************************************************/
// CoT message listeners

void MissionPackageManager::datagramReceivedGeneric(
        const std::string *endpointId,
        const uint8_t *data, size_t len)
{
}

void MissionPackageManager::datagramReceived(
        const std::string *endpointId,
        const NetAddress *sender, const TakMessage *takmsg)
{
    const CoTMessage *msg = takmsg->getCoTMessage();
    if (!msg)
        return;

    messageReceivedImpl("", msg);
}

void MissionPackageManager::tcpMessageReceived(const NetAddress *sender,
                                               const std::string *endpoint,
                                               const CoTMessage *msg)
{
    messageReceivedImpl("", msg);
}

void MissionPackageManager::streamingMessageReceived(
        std::string streamingEndpoint, const CoTMessage *message)
{
    messageReceivedImpl(streamingEndpoint, message);
}

void MissionPackageManager::messageReceivedImpl(
        const std::string &streamingEndpoint, const CoTMessage *msg)
{
    std::string ackuid = msg->getFileTransferAckUid();
    if (!ackuid.empty()) {
        TxAckInfo *ack = new TxAckInfo(ackuid, msg->getFileTransferReason(),
                msg->getFileTransferSucceeded(),
                msg->getFileTransferAckSenderUid(),
                msg->getFileTransferAckSize());

        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, txAcksMutex);
        txAcks.push_front(ack);
        txAcksMonitor.broadcast(*lock);

        return;
    }

    const CoTFileTransferRequest *xferReq = msg->getFileTransferRequest();
    if (!xferReq)
        return;

    CoTFileTransferRequest *dupReq = new CoTFileTransferRequest(*xferReq);
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, rxRequestsMutex);
        rxRequests.push_front(RxRequestDeque::value_type(streamingEndpoint, 
                                                         dupReq));
        rxRequestsMonitor.broadcast(*lock);
    }
}

/***************************************************************************/
// Public API

void MissionPackageManager::setCallsign(const char *sign)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, callsignMutex);
    ourCallsign = sign;
}

void MissionPackageManager::setMPTransferSettings(const MPTransferSettings &settings)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, txTransfersMutex);
    
    xferSettings = settings;
}

void MissionPackageManager::setLocalPort(int localPort)
        COMMO_THROW (std::invalid_argument)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, txTransfersMutex);
    
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Changing local web server port to %d from current %d", localPort, webPort);
    if (localPort == webPort)
        // All good, nothing to change
        return;
    
    
    // Shut things down before we do anything else
    if (webserver) {
        MHD_stop_daemon(webserver);
        webserver = NULL;
        webPort = MP_LOCAL_PORT_DISABLE;
    }
    
    abortLocalTransfers();


    if (localPort == MP_LOCAL_PORT_DISABLE) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Web server disabled by request");
        // We are done
        return;
    }
    
    if (localPort < 0 || localPort > USHRT_MAX) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Desired web server port is invalid");
        throw std::invalid_argument("Web server port out of range");
    }

    webserver = MHD_start_daemon(MHD_USE_SELECT_INTERNALLY,
                                 (unsigned short)localPort,
                                 NULL, NULL,
                                 webThreadAccessHandlerCallbackRedir,
                                 this, MHD_OPTION_END);
    if (!webserver) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "Could not start web server on specified port %d", localPort);
        throw std::invalid_argument(
                "Could not start internal web server on specified port");
    }
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Local web server started successfully on port %d", localPort);
    
    // Success!
    webPort = localPort;
}

void MissionPackageManager::setLocalHttpsPort(int localPort)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, txTransfersMutex);
    
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Changing local https server port to %d from current %d", localPort, httpsProxyPort);
    if (localPort == httpsProxyPort)
        // All good, nothing to change
        return;
    
    
    // Abort current local transfers
    abortLocalTransfers();


    if (localPort == MP_LOCAL_PORT_DISABLE) {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Use of https proxy server disabled by request");
    } else {
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Now using local https proxy on port %d", localPort);
    }
    
    httpsProxyPort = localPort;
}

atakmap::commoncommo::CommoResult MissionPackageManager::sendFileInit(int *xferId,
                     ContactList *destinations,
                     const char *filePath,
                     const char *fileName,
                     const char *name)
{
    std::vector<const ContactUID *> vdest;
    for (size_t i = 0; i < destinations->nContacts; ++i)
        vdest.push_back(destinations->contacts[i]);
    atakmap::commoncommo::CommoResult ret =
            sendFileInit(xferId, &vdest, filePath, fileName, name);
    destinations->nContacts = vdest.size();
    for (size_t i = 0; i < destinations->nContacts; ++i)
        destinations->contacts[i] = vdest[i];
    return ret;
}

atakmap::commoncommo::CommoResult MissionPackageManager::sendFileInit(
                     int *xferId,
                     std::vector<const ContactUID *> *destinations,
                     const char *filePath,
                     const char *fileName,
                     const char *name)
{
    std::string hash;
    auto provider(providerTracker->getCurrentProvider());
    if (!InternalUtils::computeSha256Hash(&hash, filePath, provider))
        return COMMO_ILLEGAL_ARGUMENT;

    uint64_t fileSize;
    try {
        fileSize = provider->getSize(filePath);
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }


    // Sort destinations by gone, tak servers, and local
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, txTransfersMutex);

        std::vector<const ContactUID *> ret;
        std::map<std::string, std::vector<const InternalContactUID *> *> streamToContacts;
        std::map<std::string, std::vector<const InternalContactUID *> *>::iterator siter;
        std::map<InternalContactUID *, std::string> local;
        for (size_t i = 0; i < destinations->size(); ++i) {
            std::string dbguid((const char *)destinations->at(i)->contactUID, destinations->at(i)->contactUIDLen);
            try {
                std::string sep = contactMgr->getStreamEndpointIdentifier(destinations->at(i), true);
                if (!xferSettings.isServerTransferEnabled()) {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send to %s would be routed via streaming ep %s, but streaming transfers are disabled", dbguid.c_str(), sep.c_str());
                } else {
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send to %s going via streaming ep %s", dbguid.c_str(), sep.c_str());

                    std::vector<const InternalContactUID *> *contacts;
                    siter = streamToContacts.find(sep);
                    if (siter != streamToContacts.end()) {
                        contacts = siter->second;
                    } else {
                        contacts = new std::vector<const InternalContactUID *>();
                        streamToContacts[sep] = contacts;
                    }
                    contacts->push_back(new InternalContactUID(destinations->at(i)));

                    continue;
                }
            } catch (std::invalid_argument &) {
                // No active streaming endpoint - consider it local
            }
            if (webPort != MP_LOCAL_PORT_DISABLE && contactMgr->hasContact(destinations->at(i))) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send to %s going via local transfer", dbguid.c_str());
                char uuidBuf[COMMO_UUID_STRING_BUFSIZE];
                clientio->createUUID(uuidBuf);
                uuidBuf[COMMO_UUID_STRING_BUFSIZE-1] = '\0';
                local[new InternalContactUID(destinations->at(i))] = uuidBuf;
            } else {
                // Truly "gone" or transfer methods disabled
                ret.push_back(destinations->at(i));
            }
        }

        // Create context
        bool hasAny = false;
        if (!local.empty() || !streamToContacts.empty()) {
            TxTransferContext *txCtx = new TxTransferContext(this, 
                    nextTxId++,
                    xferSettings,
                    filePath,
                    fileName,
                    name,
                    fileSize,
                    hash);
            *xferId = txCtx->id;

            for (siter = streamToContacts.begin(); siter != streamToContacts.end(); ++siter) {
                TxUploadContext *uploadCtx = new TxUploadContext(txCtx, provider, siter->first, siter->second);
                txCtx->uploadCtxs.insert(uploadCtx);
            }
            txCtx->localContactToAck.insert(local.begin(), local.end());
            
            hasAny = !txCtx->uploadCtxs.empty() || !txCtx->localContactToAck.empty();

            if (hasAny) {
                pendingTxTransfers[txCtx->id] = txCtx;
            } else
                // No destinations outstanding
                delete txCtx;
        }


        destinations->clear();
        destinations->insert(destinations->begin(), ret.begin(), ret.end());
        if (!hasAny)
            return COMMO_ILLEGAL_ARGUMENT;
        else if (ret.empty())
            return COMMO_SUCCESS;
        else
            return COMMO_CONTACT_GONE;
    }
}

atakmap::commoncommo::CommoResult MissionPackageManager::uploadFileInit(
                       int *xferId, 
                       const char *streamingRemoteId,
                       const char *filePath,
                       const char *fileName)
{
    // check if remoteid is valid
    try {
        NetAddress *na = streamMgr->getAddressForEndpoint(streamingRemoteId);
        delete na;
    } catch (std::invalid_argument &) {
        return COMMO_CONTACT_GONE;
    }

    std::string hash;
    auto provider(providerTracker->getCurrentProvider());
    if (!InternalUtils::computeSha256Hash(&hash, filePath, provider))
        return COMMO_ILLEGAL_ARGUMENT;

    uint64_t fileSize;
    try {
        fileSize = provider->getSize(filePath);
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
    
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, txTransfersMutex);

        TxTransferContext *txCtx = new TxTransferContext(this, 
                nextTxId++,
                xferSettings,
                filePath,
                fileName,
                // arbitrary
                "serverxfer",
                fileSize,
                hash);
        *xferId = txCtx->id;

        TxUploadContext *uploadCtx = new TxUploadContext(txCtx, provider, streamingRemoteId);
        txCtx->uploadCtxs.insert(uploadCtx);

        pendingTxTransfers[txCtx->id] = txCtx;
    }
    
    return COMMO_SUCCESS;
}

atakmap::commoncommo::CommoResult MissionPackageManager::sendFileStart(
                       int xferId)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, txTransfersMutex);
    
    // Try to find this transfer
    TxTransferMap::iterator iter;
    iter = pendingTxTransfers.find(xferId);
    if (iter == pendingTxTransfers.end())
        return COMMO_ILLEGAL_ARGUMENT;
    
    TxTransferContext *txCtx = iter->second;
    
    // No longer pending
    pendingTxTransfers.erase(iter);

    // Give any new uploads to the upload thread
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, uploadRequestsMutex);
        std::set<TxUploadContext *>::iterator upIter;
        for (upIter = txCtx->uploadCtxs.begin(); upIter != txCtx->uploadCtxs.end(); ++upIter) {
            TxUploadContext *upCtx = *upIter;
            uploadRequests.push_front(upCtx);

            if (upCtx->contacts) {
                std::vector<const InternalContactUID *>::const_iterator citer;
                for (citer = upCtx->contacts->begin();
                        citer != upCtx->contacts->end(); ++citer)
                {
                    queueEvent(MPStatusEvent::createTxProgress(txCtx->id,
                                            *citer,
                                            MP_TRANSFER_SERVER_UPLOAD_PENDING,
                                            NULL,
                                            0));
                }
            } else {
                queueEvent(MPStatusEvent::createTxProgress(txCtx->id,
                                        NULL,
                                        MP_TRANSFER_SERVER_UPLOAD_PENDING,
                                        NULL,
                                        0));
            }
        }
        uploadRequestsMonitor.broadcast(*lock);
    }

    // Send CoT for local transfers
    // and populate the outstanding acks with the local acks
    std::string localUrl;
    bool transfersDisabled = (webPort == MP_LOCAL_PORT_DISABLE);
    if (!transfersDisabled)
        localUrl = getLocalUrl(txCtx->id);

    std::map<const InternalContactUID *, std::string>::iterator ackIter;
    for (ackIter = txCtx->localContactToAck.begin(); 
                 ackIter != txCtx->localContactToAck.end(); ++ackIter) {
        if (localUrl.empty() || sendCoTRequest(localUrl, ackIter->second, txCtx, ackIter->first, httpsProxyPort) == COMMO_CONTACT_GONE) {
            if (transfersDisabled) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                    "MP Send to local contact failed - transfers disabled locally");
                queueEvent(MPStatusEvent::createTxFinal(txCtx->id,
                        ackIter->first,
                        MP_TRANSFER_FINISHED_DISABLED_LOCALLY,
                        NULL,
                        0));
            } else {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR,
                    "MP Send to local contact failed - unable to derive local download URL");
                queueEvent(MPStatusEvent::createTxFinal(txCtx->id,
                        ackIter->first,
                        MP_TRANSFER_FINISHED_CONTACT_GONE,
                        NULL,
                        0));
            }

        } else {
            txCtx->outstandingAcks[ackIter->second] = ackIter->first;
            txCtx->localAcks.insert(ackIter->second);
            acksToIds[ackIter->second] = txCtx->id;
            queueEvent(MPStatusEvent::createTxProgress(txCtx->id,
                ackIter->first,
                MP_TRANSFER_ATTEMPT_IN_PROGRESS,
                NULL,
                0));
        }
    }
    // All contacts passed off to either gone event dispatcher or 
    // outstandingAcks map, so we clear these away
    txCtx->localContactToAck.clear();

    if (!txCtx->uploadCtxs.empty() || !txCtx->outstandingAcks.empty()) {
        txTransfers[txCtx->id] = txCtx;
    } else
        // No destinations outstanding, done!
        delete txCtx;

    return COMMO_SUCCESS;
}



/***************************************************************************/
// TX related private functions


// NOTE: assumes holding of txTransfersMutex
void MissionPackageManager::abortLocalTransfers()
{
    // Abort outbound local transfers
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Aborting existing outbound transfers using local web server as port is changing...");
    TxTransferMap::iterator xferIter;
    TxTransferMap::iterator nextXferIter;
    xferIter = txTransfers.begin();
    while (xferIter != txTransfers.end())
    {
        nextXferIter = xferIter;
        nextXferIter++;
        
        std::set<std::string>::iterator ackIter;
        for (ackIter = xferIter->second->localAcks.begin(); 
                ackIter != xferIter->second->localAcks.end();
                ackIter++)
        {
            std::map<std::string, const InternalContactUID *>::iterator i;
            i = xferIter->second->outstandingAcks.find(*ackIter);
            if (i == xferIter->second->outstandingAcks.end())
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Unable to find destination contact to abort transfer!!");
            else {
                queueEvent(MPStatusEvent::createTxFinal(xferIter->second->id,
                        i->second,
                        MP_TRANSFER_FINISHED_DISABLED_LOCALLY,
                        NULL,
                        0));
                xferIter->second->outstandingAcks.erase(i);
            }
            acksToIds.erase(*ackIter);
        }
        
        xferIter->second->localAcks.clear();

        if (xferIter->second->isDone()) {
            delete xferIter->second;
            txTransfers.erase(xferIter);
        }
        
        xferIter = nextXferIter;
    }
}


atakmap::commoncommo::CommoResult MissionPackageManager::sendCoTRequest(
                    const std::string &downloadUrl,
                    const std::string &ackUid,
                    const TxTransferContext *ctx,
                    const ContactUID *contact,
                    const int localHttpsPort)
{
    char uuidBuf[COMMO_UUID_STRING_BUFSIZE];
    clientio->createUUID(uuidBuf);
    uuidBuf[COMMO_UUID_STRING_BUFSIZE-1] = '\0';


    CoTPointData point = clientio->getCurrentPoint();

    callsignMutex.lock();
    CoTFileTransferRequest filexfer(ctx->sha256hash,
                       ctx->transferName,
                       ctx->filename,
                       downloadUrl,
                       ctx->fileSize,
                       ourCallsign,
                       ourContactUID,
                       ackUid,
                       localHttpsPort == MP_LOCAL_PORT_DISABLE ? false : true,
                       localHttpsPort);
    callsignMutex.unlock();
    CoTMessage msg(logger, uuidBuf, point, filexfer);

    std::string dbguid((const char *)contact->contactUID, contact->contactUIDLen);
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
            "MP Send to %s - cot download request sending out for URL %s (localhttps = %d)",
            dbguid.c_str(), downloadUrl.c_str(),
            localHttpsPort);
    ContactList cList(1, &contact);
    return contactMgr->sendCoT(&cList, &msg);
}




/***************************************************************************/
// ThreadedHandler


void MissionPackageManager::threadStopSignal(
        size_t threadNum)
{
    switch (threadNum) {
    case RX_THREADID:
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, rxRequestsMutex);
        rxRequestsMonitor.broadcast(*lock);
        break;
    }
    case TX_THREADID:
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, txAcksMutex);
        txAcksMonitor.broadcast(*lock);
        break;
    }
    case TX_UPLOAD_THREADID:
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, uploadRequestsMutex);
        uploadRequestsMonitor.broadcast(*lock);
        break;
    }
    case EVENT_THREADID:
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, eventQueueMutex);
        eventQueueMonitor.broadcast(*lock);
        break;
    }
    }
}

void MissionPackageManager::threadEntry(
        size_t threadNum)
{
    switch (threadNum) {
    case RX_THREADID:
        receiveThreadProcess();
        break;
    case TX_THREADID:
        transmitThreadProcess();
        break;
    case TX_UPLOAD_THREADID:
        uploadThreadProcess();
        break;
    case EVENT_THREADID:
        eventThreadProcess();
        break;
    }
}


/***************************************************************************/
// Upload thread


void MissionPackageManager::uploadThreadProcess()
{
    std::set<TxUploadContext *> newRequests;
    std::set<TxUploadContext *> uploadingRequests;

    while (!threadShouldStop(TX_UPLOAD_THREADID)) {
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, uploadRequestsMutex);
            while (!uploadRequests.empty()) {
                TxUploadContext *ctx = uploadRequests.back();
                uploadRequests.pop_back();
                newRequests.insert(ctx);
            }
        }

        if (!newRequests.empty()) {
            std::set<TxUploadContext *>::iterator newIter;
            for (newIter = newRequests.begin(); newIter != newRequests.end(); ++newIter) {
                TxUploadContext *upCtx = *newIter;
                try {
                    uploadThreadInitCtx(upCtx);
                } catch (std::invalid_argument &) {
                    uploadThreadUploadCompleted(upCtx, false);
                    continue;
                }
                uploadingRequests.insert(upCtx);
                // Tell everyone we're in progress
                queueUploadProgressEvent(upCtx);
            }
            newRequests.clear();
        }

        if (uploadingRequests.empty()) {
            // Nap time - wait until a new request arrives
            while (!threadShouldStop(TX_UPLOAD_THREADID)) {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, uploadRequestsMutex);
                if (!uploadRequests.empty())
                    break;
                uploadRequestsMonitor.wait(*lock);
            }
            continue;
        }

        // We have at least one active transfer in the upload multi handle.
        // Wait for whatever curl thinks is interesting on any pending transfer
        // or, at most, 1 second allowing us to either pick up new transfer
        // requests or respond to request to terminate.
        int count = 0;
        curl_multi_wait(uploadCurlMultiCtx, NULL, 0, 1000, &count);

        // NOTE: see note in receive thread after curl_multi_wait() about
        // not relying on count to optimize when (not) to call 
        // curl_multi_perform()

        int activeTransferCount = (int)uploadingRequests.size();
        count = activeTransferCount;
        curl_multi_perform(uploadCurlMultiCtx, &count);
        if (count == activeTransferCount)
            continue;

        struct CURLMsg *cmsg;
        do {
            count = 0;
            cmsg = curl_multi_info_read(uploadCurlMultiCtx, &count);
            if (cmsg && cmsg->msg == CURLMSG_DONE) {
                CURL *easyHandle = cmsg->easy_handle;
                CURLcode result = cmsg->data.result;

                curl_multi_remove_handle(uploadCurlMultiCtx, easyHandle);

                TxUploadContext *upCtx = NULL;
                curl_easy_getinfo(easyHandle, CURLINFO_PRIVATE, &upCtx);

                // Clear this now since we'll be cleaning up the
                // easy handle below no matter what
                upCtx->curlCtx = NULL;
                
                bool error = false;
                std::stringstream errInfo;
                if (upCtx->state == TxUploadContext::TOOLSET) {
                    if (result == CURLE_OK) {
                        long httpRc;
                        curl_easy_getinfo(easyHandle, CURLINFO_RESPONSE_CODE, &httpRc);
                        if (httpRc == 200)
                            InternalUtils::logprintf(logger,
                                        CommoLogger::LEVEL_DEBUG,
                                            "MP Send: %s - successfully set \"tool\" attribute",
                                            upCtx->owner->fileToSend.c_str());
                        else
                            InternalUtils::logprintf(logger,
                                        CommoLogger::LEVEL_DEBUG,
                                            "MP Send: %s - failed to set \"tool\" attribute - http code was %ld",
                                            upCtx->owner->fileToSend.c_str(),
                                            httpRc);
                    } else {
                        InternalUtils::logprintf(logger,
                                    CommoLogger::LEVEL_DEBUG,
                                        "MP Send: %s - failed to set \"tool\" attribute - unexpected curl error",
                                        upCtx->owner->fileToSend.c_str());
                        
                    }
                    curl_easy_cleanup(easyHandle);

                    curl_slist_free_all(upCtx->headerList);
                    upCtx->headerList = NULL;
                    uploadingRequests.erase(upCtx);
                
                    // URL was previously obtained; Send out.
                    InternalUtils::logprintf(logger,
                                CommoLogger::LEVEL_DEBUG,
                                    "MP Send: %s - file on server at %s "
                                    "- sending URL to recipients (if any)", 
                                    upCtx->owner->fileToSend.c_str(), 
                                    upCtx->urlFromServer.c_str());
                    uploadThreadUploadCompleted(upCtx, true);
                    
                } else if (result == CURLE_OK) {
                    long httpRc;
                    curl_easy_getinfo(easyHandle, CURLINFO_RESPONSE_CODE, &httpRc);

                    curl_easy_cleanup(easyHandle);

                    uploadingRequests.erase(upCtx);
                    if (upCtx->state == TxUploadContext::UPLOAD) {
                        curl_formfree(upCtx->uploadData);
                        upCtx->uploadData = NULL;
                        if (upCtx->localFile) {
                            upCtx->ioProvider->close(upCtx->localFile);
                            upCtx->ioProvider = NULL;
                            upCtx->localFile = NULL;
                        }
                    }
                    
                    if (httpRc == 200) {
                        // URL obtained! Set "tool" attribute now
                        InternalUtils::logprintf(logger,
                                    CommoLogger::LEVEL_DEBUG,
                                    "MP Send: %s - file on server at %s "
                                    " - setting \"tool\" parameter",
                                    upCtx->owner->fileToSend.c_str(), 
                                    upCtx->urlFromServer.c_str());
                        try {
                            upCtx->state = TxUploadContext::TOOLSET;
                            uploadThreadInitCtx(upCtx);
                            uploadingRequests.insert(upCtx);
                        } catch (std::invalid_argument &) {
                            error = true;
                        }

                    } else if (httpRc == 404 && upCtx->state == TxUploadContext::CHECK) {
                        // File not there - let's upload
                        InternalUtils::logprintf(logger,
                                    CommoLogger::LEVEL_DEBUG, 
                                    "MP Send: %s - file NOT already on server "
                                    "- initiating upload",
                                    upCtx->owner->fileToSend.c_str());
                        try {
                            upCtx->state = TxUploadContext::UPLOAD;
                            uploadThreadInitCtx(upCtx);
                            uploadingRequests.insert(upCtx);
                        } catch (std::invalid_argument &) {
                            error = true;
                        }

                    } else {
                        // Something unexpected - give up
                        error = true;
                        errInfo << "http rc was " << httpRc;
                    }
                } else {
                    // Something terrible happened - give up
                    errInfo << "result code from curl was not OK - instead was " << result <<
                               " and error string was " << upCtx->curlErrBuf;
                    error = true;
                    curl_easy_cleanup(easyHandle);
                    uploadingRequests.erase(upCtx);
                    if (upCtx->state == TxUploadContext::UPLOAD) {
                        curl_formfree(upCtx->uploadData);
                        upCtx->uploadData = NULL;
                    }
                }

                if (error) {
                    InternalUtils::logprintf(logger,
                            CommoLogger::LEVEL_DEBUG,
                            "MP Send: Upload of %s encountered error (%s)",
                            upCtx->owner->fileToSend.c_str(),
                            errInfo.str().c_str());
                    uploadThreadUploadCompleted(upCtx, false);
                }

            }
        } while (cmsg);
    }

    uploadThreadCleanSet(&uploadingRequests);

}


size_t MissionPackageManager::curlUploadWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    TxUploadContext *upCtx = (TxUploadContext *)userCtx;

    size_t n = nmemb * size;
    upCtx->urlFromServer.append(buf, n);
    return n;
}

size_t MissionPackageManager::curlReadCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    TxUploadContext *upCtx = (TxUploadContext *)userCtx;

    if (!upCtx->localFile)
        return 0;

    size_t n = upCtx->ioProvider->read(buf, size, nmemb, upCtx->localFile);

    return n * size;
}

CURLcode MissionPackageManager::curlSslCtxSetupRedirUpload(CURL *curl, void *sslctx, void *privData)
{
    MissionPackageManager::TxUploadContext *upCtx = (MissionPackageManager::TxUploadContext *)privData;
    return upCtx->sslCtxSetup(curl, sslctx);
}



void MissionPackageManager::uploadThreadInitCtx(TxUploadContext *upCtx) COMMO_THROW (std::invalid_argument)
{
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send: Initiating %s of package %s to %s ", 
            upCtx->state == TxUploadContext::CHECK ? "on server check" : 
                (upCtx->state == TxUploadContext::UPLOAD ? "upload" : "tool set"),
            upCtx->owner->fileToSend.c_str(),
            upCtx->streamEndpoint.c_str());

    if (upCtx->localFile) {
        upCtx->ioProvider->close(upCtx->localFile);
        upCtx->ioProvider = NULL;
        upCtx->localFile = NULL;
    }

#define CURL_CHECK(a) if ((a) != 0) throw std::invalid_argument("Failed to set curl option")
    std::string url;
    bool isSSL = false;
    NetAddress *addr = NULL;
    try {
        addr = streamMgr->getAddressForEndpoint(upCtx->streamEndpoint);
        isSSL = streamMgr->isEndpointSSL(upCtx->streamEndpoint);
    } catch (std::invalid_argument &e) {
        delete addr;
        throw e;
    }

    addr->getIPString(&url);
    delete addr;

    upCtx->curlCtx = curl_easy_init();
    if (upCtx->curlCtx == NULL)
        throw std::invalid_argument("Could not init curl ctx");

    try {
        if (isSSL) {
            url.insert(0, "https://");
            url += ":";
            url += InternalUtils::intToString(upCtx->owner->settings.getHttpsPort());
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_SSL_CTX_FUNCTION, curlSslCtxSetupRedirUpload));
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_SSL_CTX_DATA, upCtx));
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_SSL_VERIFYHOST, 0L));
        } else {
            url.insert(0, "http://");
            url += ":";
            url += InternalUtils::intToString(upCtx->owner->settings.getHttpPort());
        }
        url += "/Marti";

        switch (upCtx->state) {
        case TxUploadContext::CHECK:
          {
            // start clean
            upCtx->urlFromServer.clear();

            url += "/sync/missionquery";
            InternalUtils::urlAppendParam(upCtx->curlCtx, &url, "hash",
                                          upCtx->owner->sha256hash.c_str());

            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_HTTPGET, 1L));
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send: %s - Check URL is %s ", upCtx->owner->fileToSend.c_str(), url.c_str());
            break;
          }
        case TxUploadContext::UPLOAD:
          {
            // start clean
            upCtx->urlFromServer.clear();

            // XXX - might need to clear Expect: header - see CURLOPT_HTTPPOST docs
            struct curl_httppost *uploadData = NULL;
            struct curl_httppost *uploadDataTail = NULL;
            CURL_CHECK(curl_formadd(&uploadData, &uploadDataTail,
                    CURLFORM_COPYNAME, "assetfile",
                    CURLFORM_STREAM, upCtx,
                    CURLFORM_FILENAME, upCtx->owner->filename.c_str(),
                    CURLFORM_CONTENTSLENGTH, upCtx->owner->fileSize,
                    CURLFORM_CONTENTTYPE, "application/x-zip-compressed",
                    CURLFORM_END));
            upCtx->uploadData = uploadData;
            upCtx->localFile = upCtx->ioProvider->open(upCtx->owner->fileToSend.c_str(), "rb");

            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_READFUNCTION, curlReadCallback));

            url += "/sync/missionupload";
            InternalUtils::urlAppendParam(upCtx->curlCtx, &url, "hash",
                                          upCtx->owner->sha256hash.c_str());
            InternalUtils::urlAppendParam(upCtx->curlCtx, &url, "filename",
                                          upCtx->owner->filename.c_str());
            std::string ouruid((const char *)ourContactUID->contactUID,
                               ourContactUID->contactUIDLen);
            InternalUtils::urlAppendParam(upCtx->curlCtx, &url, "creatorUid",
                                          ouruid.c_str());

            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_HTTPPOST, uploadData));

            // For progress
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_XFERINFOFUNCTION,
                                        curlUploadXferCallback));
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_XFERINFODATA,
                                        upCtx));
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_NOPROGRESS, 0L));

            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send: %s - Upload URL is %s ", upCtx->owner->fileToSend.c_str(), url.c_str());
            break;
          }
        case TxUploadContext::TOOLSET:
          {
            url += "/api/sync/metadata/";
            url += upCtx->owner->sha256hash;
            url += "/tool";

            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_CUSTOMREQUEST, "PUT"));
            // Set to public for server-only upload, private for sends to contacts
            const char *toolData = upCtx->contacts ? "private" : "public";
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_POSTFIELDS, toolData));
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_POSTFIELDSIZE_LARGE, (curl_off_t)strlen(toolData)));
            upCtx->headerList = curl_slist_append(upCtx->headerList, "Content-Type: text/plain");
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_HTTPHEADER, upCtx->headerList));
            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "MP Send: %s - Toolset value is %s ", upCtx->owner->fileToSend.c_str(), toolData);
            break;
          }
        default:
            throw std::invalid_argument("Invalid state");
        }
        

        CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_NOSIGNAL, 1L));
        
        if (upCtx->state != TxUploadContext::TOOLSET) {
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_WRITEFUNCTION, curlUploadWriteCallback));
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_WRITEDATA, upCtx));
            // Don't use error buffer for toolset - we expect it to fail on older
            // server versions and won't consider it an overall failure anyway
            CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_ERRORBUFFER, upCtx->curlErrBuf));
        }
        
        CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_URL, url.c_str()));
        CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_PRIVATE, upCtx));
        CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_CONNECTTIMEOUT, (long)upCtx->owner->settings.getConnTimeoutSec()));
        CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_LOW_SPEED_LIMIT, 10L));
        CURL_CHECK(curl_easy_setopt(upCtx->curlCtx, CURLOPT_LOW_SPEED_TIME, (long)upCtx->owner->settings.getXferTimeoutSec()));
    } catch (std::invalid_argument &e) {
        curl_easy_cleanup(upCtx->curlCtx);
        upCtx->curlCtx = NULL;

        if (upCtx->localFile) {
            upCtx->ioProvider->close(upCtx->localFile);
            upCtx->ioProvider = NULL;
            upCtx->localFile = NULL;
        }

        throw e;
    }

    curl_multi_add_handle(uploadCurlMultiCtx, upCtx->curlCtx);

    //curl_easy_setopt(ftc->curlCtx, CURLOPT_USERNAME, "user");
    //curl_easy_setopt(ftc->curlCtx, CURLOPT_PASSWORD, "user");
    //curl_easy_setopt(ftc->curlCtx, CURLOPT_HTTPAUTH, CURLAUTH_BASIC | CURLAUTH_DIGEST ??);
}

void MissionPackageManager::uploadThreadUploadCompleted(TxUploadContext *upCtx, bool success)
{
    std::vector<const InternalContactUID *>::const_iterator iter;

    // on fail, just notify clientio of those failing
    if (!success && upCtx->contacts) {
        for (iter = upCtx->contacts->begin(); iter != upCtx->contacts->end(); ++iter) {
            queueEvent(MPStatusEvent::createTxProgress(upCtx->owner->id,
                    *iter,
                    MP_TRANSFER_SERVER_UPLOAD_FAILED,
                    upCtx->state == TxUploadContext::UPLOAD ? 
                                 "Uploading package to TAK server failed" :
                                 "Unable to query TAK server to see if package exists",
                    upCtx->bytesTransferred));
            queueEvent(MPStatusEvent::createTxFinal(upCtx->owner->id,
                    *iter,
                    MP_TRANSFER_FINISHED_FAILED,
                    NULL,
                    0));
        }
        // events will clean these up
        upCtx->contacts->clear();
    } else if (!upCtx->contacts) {
        // Handle server-upload-only completion
        // For success or fail, always just fire events and call it a day
        queueEvent(MPStatusEvent::createTxProgress(upCtx->owner->id,
                NULL,
                success ? MP_TRANSFER_SERVER_UPLOAD_SUCCESS : 
                          MP_TRANSFER_SERVER_UPLOAD_FAILED,
                success ? upCtx->urlFromServer.c_str() :
                          "Uploading package to TAK server failed",
                upCtx->bytesTransferred));
        queueEvent(MPStatusEvent::createTxFinal(upCtx->owner->id,
                NULL,
                success ? MP_TRANSFER_FINISHED_SUCCESS :
                          MP_TRANSFER_FINISHED_FAILED,
                NULL,
                upCtx->bytesTransferred));
    } else {
        // on success, send out CoT; insert acksToIds and outstanding acks
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, txTransfersMutex);

        for (iter = upCtx->contacts->begin(); iter != upCtx->contacts->end(); iter++) {
            queueEvent(MPStatusEvent::createTxProgress(upCtx->owner->id,
                    *iter,
                    MP_TRANSFER_SERVER_UPLOAD_SUCCESS,
                    upCtx->urlFromServer.c_str(),
                    upCtx->bytesTransferred));

            char uuidBuf[COMMO_UUID_STRING_BUFSIZE];
            clientio->createUUID(uuidBuf);
            uuidBuf[COMMO_UUID_STRING_BUFSIZE-1] = '\0';
            if (sendCoTRequest(upCtx->urlFromServer, uuidBuf, upCtx->owner, *iter, MP_LOCAL_PORT_DISABLE) == COMMO_CONTACT_GONE) {
                queueEvent(MPStatusEvent::createTxFinal(upCtx->owner->id,
                    *iter,
                    MP_TRANSFER_FINISHED_CONTACT_GONE,
                    NULL,
                    0));

                
            } else {
                // Transfers the InternalContactUID to the owning context
                // for ack tracking purposes
                queueEvent(MPStatusEvent::createTxProgress(upCtx->owner->id,
                    *iter,
                    MP_TRANSFER_ATTEMPT_IN_PROGRESS,
                    NULL,
                    0));
                upCtx->owner->outstandingAcks[uuidBuf] = *iter;
                acksToIds[uuidBuf] = upCtx->owner->id;
            }
        }
        // Either a fail event or the owning context now
        // has every contact; remove from our management
        upCtx->contacts->clear();
    }

    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, txTransfersMutex);
        // in either case, remove upload from the owning txtransfercontext
        upCtx->owner->uploadCtxs.erase(upCtx);
        if (upCtx->owner->isDone()) {
            txTransfers.erase(upCtx->owner->id);
            delete upCtx->owner;
        }
        delete upCtx;
    }
}



void MissionPackageManager::uploadThreadCleanSet(std::set<TxUploadContext *> *upSet)
{
    std::set<TxUploadContext *>::iterator iter;
    for (iter = upSet->begin(); iter != upSet->end(); ++iter) {
        TxUploadContext *upCtx = *iter;
        if (upCtx->curlCtx) {
            curl_multi_remove_handle(uploadCurlMultiCtx, upCtx->curlCtx);
            curl_easy_cleanup(upCtx->curlCtx);
            if (upCtx->uploadData) {
                curl_formfree(upCtx->uploadData);
                upCtx->uploadData = NULL;
            }
            if (upCtx->headerList) {
                curl_slist_free_all(upCtx->headerList);
                upCtx->headerList = NULL;
            }
        }
        delete upCtx;
    }

}


CURLcode MissionPackageManager::curlUploadXferCallback(void *privData,
                                      curl_off_t dlTotal,
                                      curl_off_t dlNow,
                                      curl_off_t ulTotal,
                                      curl_off_t ulNow)
{
    MissionPackageManager::TxUploadContext *upCtx =
            (MissionPackageManager::TxUploadContext *)privData;
    uint64_t oldVal = upCtx->bytesTransferred;
    upCtx->bytesTransferred = ulNow;

    // Queue an update if #'s changed
    if (upCtx->bytesTransferred != oldVal) {
        upCtx->owner->owner->queueUploadProgressEvent(upCtx);
    }
    
    return CURLE_OK;
}


void MissionPackageManager::queueUploadProgressEvent(TxUploadContext *upCtx)
{
    std::vector<const InternalContactUID *>::iterator iter;
    if (upCtx->contacts) {
        for (iter = upCtx->contacts->begin(); iter != upCtx->contacts->end();
                                              ++iter) {
            queueEvent(MPStatusEvent::createTxProgress(upCtx->owner->id,
                    *iter,
                    MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS,
                    NULL,
                    upCtx->bytesTransferred));
        }
    } else {
        queueEvent(MPStatusEvent::createTxProgress(upCtx->owner->id,
                NULL,
                MP_TRANSFER_SERVER_UPLOAD_IN_PROGRESS,
                NULL,
                upCtx->bytesTransferred));
    }
}



/***************************************************************************/
// TX ack processing thread


void MissionPackageManager::transmitThreadProcess()
{
    while (!threadShouldStop(TX_THREADID)) {
        TxAckInfo *ackInfo;
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, txAcksMutex);
            if (txAcks.empty()) {
                txAcksMonitor.wait(*lock);
                continue;
            }

            ackInfo = txAcks.back();
            txAcks.pop_back();
        }

        const InternalContactUID *transferDest = NULL;
        int txId = 0;
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, txTransfersMutex);
            TxAcksMap::iterator iter = acksToIds.find(ackInfo->uid);
            if (iter != acksToIds.end()) {
                txId = iter->second;
                TxTransferContext *txCtx = txTransfers[txId];
                
                std::map<std::string, const InternalContactUID *>::iterator imap;
                imap = txCtx->outstandingAcks.find(iter->first);
                if (imap != txCtx->outstandingAcks.end()) {
                    transferDest = imap->second;
                    txCtx->outstandingAcks.erase(imap);
                    txCtx->localAcks.erase(iter->first);

                    if (txCtx->isDone()) {
                        delete txCtx;
                        txTransfers.erase(txId);
                    }
                }

                acksToIds.erase(iter);
            }
        }

        if (transferDest) {
            queueEvent(MPStatusEvent::createTxFinal(txId,
                    transferDest,
                    ackInfo->success? MP_TRANSFER_FINISHED_SUCCESS : 
                                      MP_TRANSFER_FINISHED_FAILED,
                    ackInfo->reason.c_str(),
                    ackInfo->success ? ackInfo->transferSize : 0));
        }
        delete ackInfo;
    }
}




/***************************************************************************/
// RX members


CURLcode MissionPackageManager::curlSslCtxSetupRedir(CURL *curl, void *sslctx, void *privData)
{
    MissionPackageManager::FileTransferContext *ftc = (MissionPackageManager::FileTransferContext *)privData;
    return ftc->sslCtxSetup(curl, sslctx);
}

size_t MissionPackageManager::curlWriteCallback(char *buf, size_t size, size_t nmemb, void *userCtx)
{
    FileTransferContext *ftc = (FileTransferContext *)userCtx;
    if (!ftc->outputFile) {
        ftc->outputFile = ftc->provider->open(ftc->localFilename.c_str(), "wb");
        if (!ftc->outputFile)
            return 0;
    }

    size_t n = ftc->provider->write(buf, size, nmemb, ftc->outputFile);
    if (n != nmemb)
        return 0;
    return nmemb * size;
}

CURLcode MissionPackageManager::curlXferCallback(void *privData,
                                      curl_off_t dlTotal,
                                      curl_off_t dlNow,
                                      curl_off_t ulTotal,
                                      curl_off_t ulNow)
{
    MissionPackageManager::FileTransferContext *ftc =
            (MissionPackageManager::FileTransferContext *)privData;
    uint64_t oldVal = ftc->bytesTransferred;
    ftc->bytesTransferred = dlNow;

    // Queue an update if #'s changed
    if (ftc->bytesTransferred != oldVal) {
        ftc->owner->queueRxEvent(ftc,
            MP_TRANSFER_ATTEMPT_IN_PROGRESS,
            NULL);
    }
    
    return CURLE_OK;
}

void MissionPackageManager::receiveSendAck(CoTFileTransferRequest *req,
                                           bool success,
                                           const char *errMsg)
{
    CoTPointData point = clientio->getCurrentPoint();
    CoTMessage msg(logger,
               std::string((const char *)ourContactUID->contactUID, ourContactUID->contactUIDLen),
               point,
               *req,
               ourContactUID,
               !success,
               errMsg);
    std::vector<const ContactUID *> dests;
    dests.push_back(req->senderuid);
    contactMgr->sendCoT(&dests, &msg);
}

void MissionPackageManager::queueRxEvent(FileTransferContext *ftc,
                                         MissionPackageTransferStatus status,
                                         const char *detail)
{
    queueEvent(MPStatusEvent::createRx(
            ftc->localFilename.c_str(),
            status,
            ftc->bytesTransferred,
            ftc->request->sizeInBytes,
            ftc->currentRetryCount,
            ftc->settings.getNumTries(),
            detail));
}

void MissionPackageManager::receiveThreadProcess()
{
    const size_t fnBufSize = 4096;
    char fnBuf[fnBufSize];
    std::vector<RxRequestDeque::value_type> newRequests;
    int activeTransferCount = 0;

    while (!threadShouldStop(RX_THREADID)) {
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, rxRequestsMutex);
            while (!rxRequests.empty()) {
                RxRequestDeque::reference v = rxRequests.back();
                newRequests.push_back(v);
                rxRequests.pop_back();
            }
        }

        if (!newRequests.empty()) {
            for (size_t i = 0; i < newRequests.size(); ++i) {
                RxRequestDeque::value_type v = newRequests[i];
                CoTFileTransferRequest *r = v.second;
                if (r->senderFilename.length() >= fnBufSize) {
                    // cannot process this - the filename is insanely huge
                    delete r;
                    continue;
                }
                strcpy(fnBuf, r->senderFilename.c_str());

                // Ask client for a filename and inform it we are starting a
                // download
                switch (clientio->missionPackageReceiveInit(fnBuf, fnBufSize,
                        r->name.c_str(),
                        r->sha256hash.c_str(),
                        r->sizeInBytes,
                        r->senderCallsign.c_str())) {
                case MP_TRANSFER_FINISHED_SUCCESS:
                    break;
                case MP_TRANSFER_FINISHED_FILE_EXISTS:
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                                  "MPRX - mpio reports package %s from "
                                  "%s already exists, transfer not needed", 
                                  r->name.c_str(),
                                  r->senderCallsign.c_str());
                    receiveSendAck(r, true, "File already exists; transfer not needed");
                    delete r;
                    continue;
                case MP_TRANSFER_FINISHED_DISABLED_LOCALLY:
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                                  "MPRX - mpio reports transfers are disabled. "
                                  "Abandoning transfer of package %s from %s",
                                  r->name.c_str(),
                                  r->senderCallsign.c_str());
                    receiveSendAck(r, false, "File transfers disabled");
                    delete r;
                    continue;
                case MP_TRANSFER_FINISHED_FAILED:
                default:
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                                  "MPRX - mpio rxinit returns failure code, "
                                  "abandoning transfer of package %s from %s",
                                  r->name.c_str(),
                                  r->senderCallsign.c_str());
                    delete r;
                    continue;
                }
                std::string dstFile(fnBuf);

                RxTransferMap::iterator iter = rxTransfers.find(dstFile);
                if (iter != rxTransfers.end()) {
                    delete r;
                    continue;
                }

                auto provider (providerTracker->getCurrentProvider());
                FileTransferContext *ftc = new FileTransferContext(this, xferSettings, dstFile, v.first, r, provider);
                rxTransfers.insert(RxTransferMap::value_type(dstFile, ftc));
            }
            newRequests.clear();
        }

        // Traverse our map looking for entries with downloads not presently
        // being attempted whose time it is to be attempted
        CommoTime nowTime = CommoTime::now();
        CommoTime *earliestTime = NULL;
        RxTransferMap::iterator iter;
        for (iter = rxTransfers.begin(); iter != rxTransfers.end(); ++iter) {
            FileTransferContext *ftc = iter->second;
            if (ftc->curlCtx)
                continue;
            if (nowTime < ftc->nextRetryTime) {
                // Pending, but not yet time.
                if (!earliestTime || ftc->nextRetryTime < *earliestTime)
                    earliestTime = &ftc->nextRetryTime;
                continue;
            }

            std::string url = ftc->adjustedSenderUrl;
            if (!ftc->usingSenderURL) {
                // If a tak server URL and we have tak server endpoint, we
                // HAVE TO use the original url only.  Likewise, if we
                // don't have an active endpoint ip then we really can't
                // create our own url!
                std::string epip = contactMgr->getActiveEndpointHost(ftc->request->senderuid);
                bool replaced = false;
                if ((!ftc->senderURLIsTAKServer || !contactMgr->hasStreamingEndpoint(ftc->request->senderuid)) && !epip.empty()) {
                    replaced = InternalUtils::urlReplaceHost(&url, epip.c_str());
                }
                if (!replaced) {
                    // Increment number of retries and go back to using sender
                    // url as-is since we can't build an alternative.
                    // This allows one additional retry, but  keeps the
                    // code cleaner.
                    ftc->currentRetryCount++;
                    ftc->usingSenderURL = true;
                    InternalUtils::logprintf(logger, 
                                             CommoLogger::LEVEL_DEBUG,
                            "Downloading %s from %s; unable to synthesize "
                            "valid alternate URL, retrying with original URL "
                            "(attempt %d of %d)", ftc->request->name.c_str(), 
                            ftc->request->senderCallsign.c_str(), 
                            ftc->currentRetryCount, 
                            ftc->settings.getNumTries());
                }
            }

            InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                            "Initiating download of %s from %s at %s URL %s%s%s%s"
                            "(attempt %d of %d)", ftc->request->name.c_str(),
                            ftc->request->senderCallsign.c_str(),
                            ftc->usingSenderURL ? "sender" : "alternate",
                            url.c_str(), 
                            ftc->request->peerHosted ? " (peer hosted, url" : "",
                            ftc->request->peerHosted ? 
                             (ftc->request->httpsPort != MP_LOCAL_PORT_DISABLE ?
                                " protocol and port adjusted, orig URL " : 
                                " protocol adjusted, orig URL " ) : "",
                            ftc->request->peerHosted ? 
                                ftc->request->senderUrl.c_str() : "",
                            ftc->currentRetryCount,
                            ftc->settings.getNumTries());

            ftc->curlCtx = curl_easy_init();
            ftc->bytesTransferred = 0;

            queueRxEvent(ftc, MP_TRANSFER_ATTEMPT_IN_PROGRESS,
                         NULL);

            {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, callsignMutex);
                InternalUtils::urlAppendParam(ftc->curlCtx, &url,
                                              "receiver", ourCallsign.c_str());
            }

            curl_easy_setopt(ftc->curlCtx, CURLOPT_NOSIGNAL, 1L);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_WRITEFUNCTION, curlWriteCallback);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_WRITEDATA, ftc);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_HTTPGET, 1L);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_URL, url.c_str());
            curl_easy_setopt(ftc->curlCtx, CURLOPT_PRIVATE, ftc);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_CONNECTTIMEOUT, (long)ftc->settings.getConnTimeoutSec());
            curl_easy_setopt(ftc->curlCtx, CURLOPT_LOW_SPEED_LIMIT, 10L);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_LOW_SPEED_TIME, (long)ftc->settings.getXferTimeoutSec());
            curl_easy_setopt(ftc->curlCtx, CURLOPT_ERRORBUFFER, ftc->curlErrBuf);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_XFERINFOFUNCTION,
                                        curlXferCallback);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_XFERINFODATA,
                                        ftc);
            curl_easy_setopt(ftc->curlCtx, CURLOPT_NOPROGRESS, 0L);
            ftc->curlErrBuf[0] = 0;

            if (url.length() > 6 && url.substr(0, 6) == "https:") {
                if (ftc->request->peerHosted) {
                    // Don't verify certs for peer hosted transfers
                    curl_easy_setopt(ftc->curlCtx, CURLOPT_SSL_VERIFYPEER, 0L);
                } else {
                    curl_easy_setopt(ftc->curlCtx, CURLOPT_SSL_CTX_FUNCTION, curlSslCtxSetupRedir);
                    curl_easy_setopt(ftc->curlCtx, CURLOPT_SSL_CTX_DATA, ftc);
                }
                // Don't verify hostnames in certs
                curl_easy_setopt(ftc->curlCtx, CURLOPT_SSL_VERIFYHOST, 0L);
            }

            //curl_easy_setopt(ftc->curlCtx, CURLOPT_USERNAME, "user");
            //curl_easy_setopt(ftc->curlCtx, CURLOPT_PASSWORD, "user");
            //curl_easy_setopt(ftc->curlCtx, CURLOPT_HTTPAUTH, CURLAUTH_BASIC | CURLAUTH_DIGEST ??);

            curl_multi_add_handle(curlMultiCtx, ftc->curlCtx);
            activeTransferCount++;
        }

        if (!activeTransferCount) {
            // Nap time - wait until next retry time, if any, or forever
            // until a new request arrives
            while (!threadShouldStop(RX_THREADID)) {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, rxRequestsMutex);
                if (!rxRequests.empty())
                    break;
                if (earliestTime) {
                    CommoTime dt = *earliestTime - CommoTime::now();
                    if (dt == CommoTime::ZERO_TIME)
                        break;
                    int64_t millis = dt.getSeconds() * 1000;
                    millis += dt.getMillis();
                    rxRequestsMonitor.wait(*lock, millis);
                } else
                    rxRequestsMonitor.wait(*lock);
            }
            continue;
        }

        // We have at least one active transfer in the multi handle.
        // Wait for whatever curl things is interesting on any pending transfer
        // or, at most, 1 second allowing us to either pick up new transfer
        // requests or respond to request to terminate.
        int count = 0;
        curl_multi_wait(curlMultiCtx, NULL, 0, 1000, &count);

        // NOTE CAREFULLY: docs/api of curl is pretty ambiguous as to what the
        // count actually means when it comes back equal to zero!
        // Initially it was thought you could consider that as
        // "nothing is ready, the timeout was hit so we don't need to do
        // anything", but it turns out that some internal curl data
        // structures are not updated unless you call curl_multi_perform()
        // again! Easily manifested by feeding this code a URL
        // to an unreachable and non-ICMP-returning address (try something
        // you can't ping and that does not return ICMP replies)

        count = activeTransferCount;
        curl_multi_perform(curlMultiCtx, &count);
        if (count == activeTransferCount)
            continue;

        struct CURLMsg *cmsg;
        do {
            count = 0;
            cmsg = curl_multi_info_read(curlMultiCtx, &count);
            if (cmsg && cmsg->msg == CURLMSG_DONE) {
                const char *errorDetail = NULL;
                CURL *easyHandle = cmsg->easy_handle;
                activeTransferCount--;
                CURLcode result = cmsg->data.result;
                curl_multi_remove_handle(curlMultiCtx, easyHandle);
                FileTransferContext *ftc = NULL;
                curl_easy_getinfo(easyHandle, CURLINFO_PRIVATE, &ftc);
                ftc->curlCtx = NULL;
                if (ftc->outputFile) {
                    ftc->provider->close(ftc->outputFile);
                    ftc->outputFile = NULL;
                }

                if (result == CURLE_OK) {
                    long httpRc;
                    curl_easy_getinfo(easyHandle, CURLINFO_RESPONSE_CODE, &httpRc);

                    if (httpRc != 200) {
                        InternalUtils::logprintf(logger, 
                                        CommoLogger::LEVEL_DEBUG, 
                                        "Download of %s from %s "
                                        "failed; server returned unexpected "
                                        "response code %ld",
                                        ftc->request->name.c_str(),
                                        ftc->request->senderCallsign.c_str(),
                                        httpRc);
                        errorDetail = "Received unexpected http response code";

                    } else {
                        static const char successMsg[] = "File transferred successfully";
                        InternalUtils::logprintf(logger, 
                                        CommoLogger::LEVEL_DEBUG, 
                                        "Download of %s from %s "
                                        "succeeded; notifying client and "
                                        "sender",
                                        ftc->request->name.c_str(),
                                        ftc->request->senderCallsign.c_str());
                        queueRxEvent(ftc, 
                                     MP_TRANSFER_FINISHED_SUCCESS, successMsg);
                        receiveSendAck(ftc->request, true, successMsg);
                        rxTransfers.erase(ftc->localFilename);
                        delete ftc;
                        ftc = NULL;
                    }

                }
                
                if (ftc) {
                    // Only arrive here if the transfer failed; success
                    // was handled above.
                    
                    if (!errorDetail) {
                        InternalUtils::logprintf(logger, 
                                        CommoLogger::LEVEL_DEBUG, 
                                        "Download of %s from %s "
                                        "failed; response code = %d "
                                        "(%s)",
                                        ftc->request->name.c_str(),
                                        ftc->request->senderCallsign.c_str(),
                                        (int)result,
                                        ftc->curlErrBuf);
                        errorDetail = ftc->curlErrBuf;
                    }
                    
                    if (ftc->usingSenderURL && !ftc->senderURLIsTAKServer && ftc->currentRetryCount < ftc->settings.getNumTries()) {
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO,
                                            "Downloading %s from %s at URL %s "
                                            "failed; retrying with alternate url "
                                            "(attempt %d of %d)",
                                            ftc->request->name.c_str(),
                                            ftc->request->senderCallsign.c_str(),
                                            ftc->adjustedSenderUrl.c_str(),
                                            ftc->currentRetryCount,
                                            ftc->settings.getNumTries());
                        ftc->usingSenderURL = false;
                        ftc->nextRetryTime = CommoTime::ZERO_TIME;
                    } else if (++ftc->currentRetryCount >= ftc->settings.getNumTries()) {
                        // XXX - better error. Use the err msg from the
                        // last senderURL attempt
                        const char *msg = "Unable to download";
                        InternalUtils::logprintf(logger, 
                                        CommoLogger::LEVEL_DEBUG, 
                                        "Download of %s from %s failed too many "
                                        "times; giving up",
                                        ftc->request->name.c_str(),
                                        ftc->request->senderCallsign.c_str());
                        queueRxEvent(ftc,
                                MP_TRANSFER_FINISHED_FAILED,
                                errorDetail);
                        receiveSendAck(ftc->request, false, msg);
                        rxTransfers.erase(ftc->localFilename);
                        delete ftc;
                    } else {
                        InternalUtils::logprintf(logger,
                                        CommoLogger::LEVEL_INFO,
                                        "Downloading %s from %s at %s URL failed; "
                                        "retrying with original url (attempt %d "
                                        "of %d)",
                                        ftc->request->name.c_str(),
                                        ftc->request->senderCallsign.c_str(),
                                        ftc->usingSenderURL ? "" : "alternate ",
                                        ftc->currentRetryCount,
                                        ftc->settings.getNumTries());
                        ftc->usingSenderURL = true;
                        ftc->nextRetryTime = CommoTime::now() + RETRY_SECONDS;
                        queueRxEvent(ftc, MP_TRANSFER_ATTEMPT_FAILED,
                                     errorDetail);

                    }
                }
                curl_easy_cleanup(easyHandle);
            }
        } while (cmsg);
    }

    RxTransferMap::iterator iter;
    for (iter = rxTransfers.begin(); iter != rxTransfers.end(); ++iter) {
        FileTransferContext *ftc = iter->second;
        if (ftc->curlCtx) {
            curl_multi_remove_handle(curlMultiCtx, ftc->curlCtx);
            curl_easy_cleanup(ftc->curlCtx);
            if (ftc->outputFile) {
                ftc->provider->close(ftc->outputFile);
                ftc->outputFile = NULL;
            }
        }
        delete ftc;
    }
    rxTransfers.clear();
}




/***************************************************************************/
// Webserver callbacks, and url synthesis


std::string MissionPackageManager::getLocalUrl(int transferId)
{
    // ATAK example: http://192.168.167.167:8080/getfile?file=9&amp;sender=GEMINI
    std::stringstream ss;
    ss << "http://";
    std::string ip = hwIfScanner->getPrimaryAddressString();
    if (ip.empty())
        return "";
    
    std::string csLocal;
    ss << ip
       << ":"
       << webPort
       << "/getfile?file="
       << transferId;

    std::string url = ss.str();
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, callsignMutex);

        csLocal = ourCallsign;
    }

    CURL *curlCtx = curl_easy_init();
    InternalUtils::urlAppendParam(curlCtx, &url,
                                   "sender", csLocal.c_str());
    if (curlCtx)
        curl_easy_cleanup(curlCtx);

    return url;
}


int MissionPackageManager::webThreadAccessHandlerCallbackRedir(void *cbContext,
                                          struct MHD_Connection *connection,
                                          const char *url,
                                          const char *method,
                                          const char *version,
                                          const char *upload_data,
                                          size_t *upload_data_size,
                                          void **connectionContext)
{
    MissionPackageManager *mpMgr = (MissionPackageManager *)cbContext;
    return mpMgr->webThreadAccessHandlerCallback(connection, url, method,
                                          version,
                                          upload_data, upload_data_size,
                                          connectionContext);
}

int MissionPackageManager::webThreadAccessHandlerCallback(
                                          struct MHD_Connection *connection,
                                          const char *url,
                                          const char *method,
                                          const char *version,
                                          const char *upload_data,
                                          size_t *upload_data_size,
                                          void **connectionContext)
{
    static int dummy = 1;
    WebserverFileContext* context (NULL);

    if (strcmp(method, "GET") != 0)
        return MHD_NO;

    if (&dummy != *connectionContext) {
        // first call for this request. Do not respond
        *connectionContext = &dummy;
        return MHD_YES;
    }

    MHD_Response *response;
    unsigned int code;

    try {
        static const std::string fileSpecifier("/getfile");
        static const std::string infoSpecifier("/getinfo");
        std::string urlStr(url);
        FileHandle *f = NULL;
        if (urlStr == infoSpecifier) {
            static char infoResponse[] = "Commo file server";
            response = MHD_create_response_from_buffer(
                sizeof(infoResponse) - 1, infoResponse, MHD_RESPMEM_PERSISTENT);

        } else {
            if (urlStr != fileSpecifier)
                throw std::invalid_argument("Invalid URL requested");

            const char *file = MHD_lookup_connection_value(connection,
                MHD_GET_ARGUMENT_KIND, "file");
            if (!file)
                throw std::invalid_argument("No file id specified");

            int xferId = InternalUtils::intFromString(file);
            std::string fileString;
            {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, txTransfersMutex);
                TxTransferMap::iterator iter = txTransfers.find(xferId);
                if (iter == txTransfers.end())
                    throw std::invalid_argument("Unknown transfer id");
                fileString = iter->second->fileToSend;
            }

            auto provider(providerTracker->getCurrentProvider());
            uint64_t size = provider->getSize(fileString.c_str());
            f = providerTracker->getCurrentProvider()->open(fileString.c_str(), "rb");
            if (!f) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                        "MP webserver: File ID %d mapped to file %s "
                        "could not be opened.", xferId, fileString.c_str());
                throw std::invalid_argument(
                        "Could not open file to serve on web");
            }
            context = new WebserverFileContext;
            context->handle = f;
            context->provider = providerTracker->getCurrentProvider();
            response = MHD_create_response_from_callback(size, 4096,
                    webserverReaderCallback, context, webserverReaderFree);
        }

        if (!response) {
            if (context)
                delete context;
            if (f)
                providerTracker->getCurrentProvider()->close(f);
            throw std::invalid_argument("Could not create web response");
        }
        code = MHD_HTTP_OK;
    } catch (std::invalid_argument &e) {
        static char errPageStr[] = "<html><head><title>File not found</title></head><body>Could not locate specified file</body></html>";
        response = MHD_create_response_from_buffer(sizeof(errPageStr) - 1,
                errPageStr, MHD_RESPMEM_PERSISTENT);
        code = MHD_HTTP_NOT_FOUND;
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                "MP webserver: Error serving request: %s",
                e.what());
    }
    int ret = MHD_queue_response(connection, code, response);
    MHD_destroy_response(response);
    return ret;
}



/***************************************************************************/
// Event thread and related methods


void MissionPackageManager::eventThreadProcess()
{
    while (!threadShouldStop(EVENT_THREADID)) {
        MPStatusEvent *event;
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, eventQueueMutex);
            if (eventQueue.empty()) {
                eventQueueMonitor.wait(*lock);
                continue;
            }

            event = eventQueue.back();
            eventQueue.pop_back();
        }

        if (event->tx) {
            clientio->missionPackageSendStatusUpdate(event->tx);
        } else {
            clientio->missionPackageReceiveStatusUpdate(event->rx);
        }

        delete event;
    }
}


void MissionPackageManager::queueEvent(MPStatusEvent *event)
{
    PGSC::Thread::LockPtr lock(NULL, NULL);
    PGSC::Thread::Lock_create(lock, eventQueueMutex);

    eventQueue.push_front(event);
    eventQueueMonitor.broadcast(*lock);
}



/***************************************************************************/
// Internal structs/classes


MissionPackageManager::FileTransferContext::FileTransferContext(
        MissionPackageManager *mgr,
        const MPTransferSettings &settings,
        const std::string &localFilename, 
        const std::string &sourceStreamEndpoint,
        CoTFileTransferRequest *request,
        std::shared_ptr<FileIOProvider>& provider) :
                owner(mgr),
                settings(settings),
                localFilename(localFilename),
                sourceStreamEndpoint(sourceStreamEndpoint),
                request(request), curlCtx(NULL),
                curlErrBuf(),
                adjustedSenderUrl(request->senderUrl),
                usingSenderURL(true), senderURLIsTAKServer(false),
                nextRetryTime(CommoTime::ZERO_TIME),
                currentRetryCount(1), bytesTransferred(0), outputFile(NULL),
                provider(provider)
{
    if (request->peerHosted && request->httpsPort != MP_LOCAL_PORT_DISABLE) {
        if (request->senderUrl.find("http://") == 0) {
            std::string s = "https://";
            s += request->senderUrl.substr(7);
            if (InternalUtils::urlReplacePort(&s, request->httpsPort))
                adjustedSenderUrl = s;
        }
    }
    if (request->senderUrl.find("/Marti/") != std::string::npos)
        senderURLIsTAKServer = true;
}

MissionPackageManager::FileTransferContext::~FileTransferContext()
{
    delete request;
}

CURLcode MissionPackageManager::FileTransferContext::sslCtxSetup(CURL *curl, void *vsslctx)
{
    SSL_CTX *sslCtx = (SSL_CTX *)vsslctx;
    std::string ep;
    try {
        ep = owner->contactMgr->getStreamEndpointIdentifier(request->senderuid);
    } catch (std::invalid_argument &) {
        // If they didn't have stream id, then use the source stream ep,
        // if given
        std::string uidLogString((const char *)request->senderuid->contactUID,
                                 request->senderuid->contactUIDLen);
        if (sourceStreamEndpoint.length() > 0) {
            InternalUtils::logprintf(owner->logger,
                CommoLogger::LEVEL_WARNING,
                "https transfer request from uid %s, but no known ssl streaming endpoint known for them. Using source stream endpoint's certificates instead",
                uidLogString.c_str());
            ep = sourceStreamEndpoint;
        } else {
            InternalUtils::logprintf(owner->logger,
                CommoLogger::LEVEL_ERROR,
                "https transfer request from uid %s, but no known ssl streaming endpoint known for them and request was not received from a streaming source, so no certificates can be selected",
                uidLogString.c_str());
            return CURLE_SSL_CERTPROBLEM;
        }
    }
    try {
        owner->streamMgr->configSSLForConnection(ep, sslCtx);
        return CURLE_OK;
    } catch (std::invalid_argument &) {
        std::string uidLogString((const char *)request->senderuid->contactUID,
                                 request->senderuid->contactUIDLen);
        InternalUtils::logprintf(owner->logger,
            CommoLogger::LEVEL_ERROR,
            "https transfer request from uid %s was mapped to source stream %s, but endpoint is not valid for ssl configuration",
            uidLogString.c_str(), ep.c_str());
        return CURLE_SSL_CERTPROBLEM;
    }
}




MissionPackageManager::TxAckInfo::TxAckInfo(const std::string &uid,
        const std::string &reason, const bool success,
        const std::string &senderuid,
        uint64_t transferSize) : uid(uid), reason(reason),
                success(success),
                senderUid(new InternalContactUID((const uint8_t *)senderuid.c_str(),
                                                 senderuid.length())),
                transferSize(transferSize)
{
}

MissionPackageManager::TxAckInfo::~TxAckInfo()
{
    delete senderUid;
}

MissionPackageManager::TxUploadContext::TxUploadContext(
        TxTransferContext *owner,
        std::shared_ptr<FileIOProvider>& provider,
        const std::string &streamEndpoint,
        std::vector<const InternalContactUID *> *contacts) :
                state(CHECK),
                streamEndpoint(streamEndpoint),
                contacts(contacts),
                owner(owner),
                curlCtx(NULL),
                uploadData(NULL),
                headerList(NULL),
                urlFromServer(),
                bytesTransferred(0),
                ioProvider(provider),
                localFile(0)
{
}



MissionPackageManager::TxUploadContext::~TxUploadContext()
{
    if (contacts != NULL) {
        std::vector<const InternalContactUID *>::const_iterator iter;
        for (iter = contacts->begin(); iter != contacts->end(); ++iter) {
            delete *iter;
        }
        delete contacts;
    }
    if (localFile) {
        ioProvider->close(localFile);
        ioProvider = NULL;
        localFile = NULL;
    }
}

CURLcode MissionPackageManager::TxUploadContext::sslCtxSetup(CURL *curl, void *vsslctx)
{
    SSL_CTX *sslCtx = (SSL_CTX *)vsslctx;
    try {
        owner->owner->streamMgr->configSSLForConnection(streamEndpoint, sslCtx);
        return CURLE_OK;
    } catch (std::invalid_argument &) {
        return CURLE_SSL_CERTPROBLEM;
    }
}


MissionPackageManager::TxTransferContext::TxTransferContext(
        MissionPackageManager *owner, int id, 
        const MPTransferSettings &settings,
        const std::string &fileToSend,
        const std::string &filename, const std::string &transferName,
        const uint64_t fileSize, const std::string &sha256hash) :
                owner(owner), id(id), settings(settings),
                fileToSend(fileToSend),
                filename(filename), transferName(transferName),
                fileSize(fileSize), sha256hash(sha256hash),
                localContactToAck(),
                uploadCtxs(), outstandingAcks()
{

}

MissionPackageManager::TxTransferContext::~TxTransferContext()
{
}

bool MissionPackageManager::TxTransferContext::isDone()
{
    return uploadCtxs.empty() && outstandingAcks.empty();
}



MissionPackageManager::MPStatusEvent::MPStatusEvent() : tx(NULL), rx(NULL), txRecipient(NULL),
                                 detail(), rxFile()
{
}

MissionPackageManager::MPStatusEvent::~MPStatusEvent()
{
    if (tx)
        delete (InternalMissionPackageSendStatusUpdate *)tx;
    if (rx)
        delete (InternalMissionPackageReceiveStatusUpdate *)rx;
    if (txRecipient)
        delete txRecipient;
}

MissionPackageManager::MPStatusEvent *
MissionPackageManager::MPStatusEvent::createTxFinal(int id,
                   const InternalContactUID *recipient,
                   MissionPackageTransferStatus status,
                   const char *detail,
                   uint64_t nbytes)
{
    MPStatusEvent *ret = createTx(id, recipient, status, detail, nbytes);
    ret->txRecipient = recipient;
    return ret;
}

MissionPackageManager::MPStatusEvent *
MissionPackageManager::MPStatusEvent::createTxProgress(int id,
                   const InternalContactUID *recipient,
                   MissionPackageTransferStatus status,
                   const char *detail,
                   uint64_t nbytes)
{
    return createTx(id, recipient, status, detail, nbytes);
}

MissionPackageManager::MPStatusEvent *
MissionPackageManager::MPStatusEvent::createTx(int id,
                   const InternalContactUID *recipient,
                   MissionPackageTransferStatus status,
                   const char *detail,
                   uint64_t nbytes)
{
    MPStatusEvent *ret = new MPStatusEvent();
    if (detail) {
        ret->detail = detail;
        detail = ret->detail.c_str();
    }

    ret->tx = 
        new InternalMissionPackageSendStatusUpdate(id, recipient, 
                                              status, 
                                              detail, 
                                              nbytes);
    return ret;
}

MissionPackageManager::MPStatusEvent *
MissionPackageManager::MPStatusEvent::createRx(const char *file, 
                   MissionPackageTransferStatus status,
                   uint64_t totalBytesReceived,
                   uint64_t totalBytesExpected,
                   int attempt,
                   int maxAttempts,
                   const char *detail)
{
    MPStatusEvent *ret = new MPStatusEvent();
    if (detail) {
        ret->detail = detail;
        detail = ret->detail.c_str();
    }
    if (file) {
        ret->rxFile = file;
        file = ret->rxFile.c_str();
    }

    ret->rx = 
        new InternalMissionPackageReceiveStatusUpdate(
                file,
                status,
                totalBytesReceived,
                totalBytesExpected,
                attempt,
                maxAttempts,
                detail);
    return ret;
}



MPTransferSettings::MPTransferSettings() : 
    httpPort(DEFAULT_HTTP_PORT),
    httpsPort(DEFAULT_HTTPS_PORT),
    nTries(DEFAULT_TRY_COUNT),
    connTimeoutSec(DEFAULT_CONN_TIMEOUT_SEC),
    xferTimeoutSec(DEFAULT_XFER_TIMEOUT_SEC),
    serverTransferEnabled(true)
{
}

int MPTransferSettings::getHttpPort()
{
    return httpPort;
}

int MPTransferSettings::getHttpsPort()
{
    return httpsPort;
}

int MPTransferSettings::getNumTries()
{
    return nTries;
}

int MPTransferSettings::getConnTimeoutSec()
{
    return connTimeoutSec;
}

int MPTransferSettings::getXferTimeoutSec()
{
    return xferTimeoutSec;
}

bool MPTransferSettings::isServerTransferEnabled()
{
    return serverTransferEnabled;
}

void MPTransferSettings::setHttpPort(int port) COMMO_THROW (std::invalid_argument)
{
    if (port <= 0 || port > 65535)
        throw std::invalid_argument("");
    this->httpPort = port;
}

void MPTransferSettings::setHttpsPort(int port) COMMO_THROW (std::invalid_argument)
{
    if (port <= 0 || port > 65535)
        throw std::invalid_argument("");
    this->httpsPort = port;
}

void MPTransferSettings::setNumTries(int nTries) COMMO_THROW (std::invalid_argument)
{
    if (nTries < MIN_TRY_COUNT)
        throw std::invalid_argument("");
    this->nTries = nTries;
}

void MPTransferSettings::setConnTimeoutSec(int connTimeoutSec) COMMO_THROW (std::invalid_argument)
{
    if (connTimeoutSec < MIN_CONN_TIMEOUT_SEC)
        throw std::invalid_argument("");
    this->connTimeoutSec = connTimeoutSec;
}

void MPTransferSettings::setXferTimeoutSec(int xferTimeoutSec) COMMO_THROW (std::invalid_argument)
{
    if (xferTimeoutSec < MIN_XFER_TIMEOUT_SEC)
        throw std::invalid_argument("");
    this->xferTimeoutSec = xferTimeoutSec;
}

void MPTransferSettings::setServerTransferEnabled(bool en)
{
    serverTransferEnabled = en;
}
