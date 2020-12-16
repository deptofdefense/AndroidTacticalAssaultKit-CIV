#include "commo.h"
#include "hwifscanner.h"
#include "takmessage.h"
#include "cotmessage.h"
#include "datagramsocketmanagement.h"
#include "tcpsocketmanagement.h"
#include "streamingsocketmanagement.h"
#include "contactmanager.h"
#include "threadedhandler.h"
#include "missionpackagemanager.h"
#include "httpsproxy.h"
#include "simplefileiomanager.h"
#include "cloudiomanager.h"
#include "cryptoutil.h"

#include <string.h>
#include <Mutex.h>
#include <Cond.h>
#include <Lock.h>
#include <memory>

#include <deque>


using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

namespace {

const char *COTL_THREAD_NAMES[1] = {
    "cmo.cotlistn"
};

class CoTListenerManagement : public ThreadedHandler, public DatagramListener,
                              public TcpMessageListener,
                              public InterfaceStatusListener,
                              public StreamingMessageListener
{

public:
    CoTListenerManagement(CommoLogger *logger) :
            ThreadedHandler(1, COTL_THREAD_NAMES),
            DatagramListener(),
            InterfaceStatusListener(),
            StreamingMessageListener(), logger(logger),
            queueMutex(), queueMonitor(), queue(), listenerMutex(),
            listeners(), genListeners(),
            ifaceListeners(), ifaceListenersMutex()
    {
        startThreads();
    };
    ~CoTListenerManagement() {
        stopThreads();
        std::deque<QItem *>::iterator iter;
        for (iter = queue.begin(); iter != queue.end(); iter++)
            delete *iter;
    };

    CommoResult addCoTMessageListener(CoTMessageListener *listener)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, listenerMutex);
        if (!listeners.insert(listener).second)
            return COMMO_ILLEGAL_ARGUMENT;
        return COMMO_SUCCESS;
    }

    CommoResult removeCoTMessageListener(CoTMessageListener *listener)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, listenerMutex);
        if (listeners.erase(listener) != 1)
            return COMMO_ILLEGAL_ARGUMENT;
        return COMMO_SUCCESS;
    }

    CommoResult addGenericDataListener(GenericDataListener *listener)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, listenerMutex);
        if (!genListeners.insert(listener).second)
            return COMMO_ILLEGAL_ARGUMENT;
        return COMMO_SUCCESS;
    }

    CommoResult removeGenericDataListener(GenericDataListener *listener)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, listenerMutex);
        if (genListeners.erase(listener) != 1)
            return COMMO_ILLEGAL_ARGUMENT;
        return COMMO_SUCCESS;
    }

    virtual void threadStopSignal(size_t threadNum)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, queueMutex);
        queueMonitor.broadcast(*lock);
    }

    virtual void threadEntry(size_t threadNum)
    {
        while (!threadShouldStop(threadNum)) {
            QItem *qitem;
            {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, queueMutex);
                if (queue.empty()) {
                    queueMonitor.wait(*lock);
                    continue;
                }
                qitem = queue.back();
                queue.pop_back();
            }
            {
                PGSC::Thread::LockPtr lock(NULL, NULL);
                PGSC::Thread::Lock_create(lock, listenerMutex);
                if (qitem->generic) {
                    std::set<GenericDataListener *>::iterator iter;
                    for (iter = genListeners.begin(); iter != genListeners.end(); ++iter) {
                        GenericDataListener *listener = *iter;
                        listener->genericDataReceived(qitem->message, qitem->length, qitem->endpointId);
                    }
                } else {
                    std::set<CoTMessageListener *>::iterator iter;
                    for (iter = listeners.begin(); iter != listeners.end(); ++iter) {
                        CoTMessageListener *listener = *iter;
                        listener->cotMessageReceived((char *)qitem->message, qitem->endpointId);
                    }
                }
                delete qitem;
            }
        }
    }

    void queueCoTMessage(const CoTMessage *message, 
                         const std::string *endpointId)
    {
        // Serialize the message
        uint8_t *data = NULL;
        try {
            message->serialize(&data);
        } catch (std::invalid_argument &) {
            // Can't serialize this message - give up.
            logger->log(CommoLogger::LEVEL_DEBUG, "Unserializable CoT message?");
            return;
        }
        // Copy endpoint, if provided
        char *epCopy = NULL;
        if (endpointId) {
            epCopy = new char[endpointId->length() + 1];
            strcpy(epCopy, endpointId->c_str());
        }
        
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, queueMutex);
            queue.push_front(new QItem(data, epCopy));
            queueMonitor.broadcast(*lock);
        }
    }
    
    virtual void datagramReceivedGeneric(const std::string *endpointId,
                                     const uint8_t *data,
                                     size_t length)
    {
        uint8_t *dataCopy = new uint8_t[length];
        memcpy(dataCopy, data, length);

        // Copy endpoint
        char *epCopy = new char[endpointId->length() + 1];
        strcpy(epCopy, endpointId->c_str());
        
        {
            PGSC::Thread::LockPtr lock(NULL, NULL);
            PGSC::Thread::Lock_create(lock, queueMutex);
            queue.push_front(new QItem(dataCopy, length, epCopy));
            queueMonitor.broadcast(*lock);
        }
    }

    virtual void datagramReceived(const std::string *endpointId,
                                  const NetAddress *sender,
                                  const TakMessage *message)
    {
        const CoTMessage *cot = message->getCoTMessage();
        if (cot)
            queueCoTMessage(cot, endpointId);
    }

    virtual void tcpMessageReceived(const NetAddress *sender, 
                                    const std::string *endpointId,
                                    const CoTMessage *message)
    {
        queueCoTMessage(message, endpointId);
    }

    virtual void streamingMessageReceived(std::string streamingEndpoint, const CoTMessage *message)
    {
        queueCoTMessage(message, &streamingEndpoint);
    }

    CommoResult addInterfaceStatusListener(InterfaceStatusListener *listener)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ifaceListenersMutex);
        if (!ifaceListeners.insert(listener).second)
            return COMMO_ILLEGAL_ARGUMENT;
        return COMMO_SUCCESS;
    }

    CommoResult removeInterfaceStatusListener(InterfaceStatusListener *listener)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ifaceListenersMutex);
        if (ifaceListeners.erase(listener) != 1)
            return COMMO_ILLEGAL_ARGUMENT;
        return COMMO_SUCCESS;
    }

    virtual void interfaceUp(NetInterface *iface)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ifaceListenersMutex);
        std::set<InterfaceStatusListener *>::iterator iter;
        for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
            InterfaceStatusListener *listener = *iter;
            listener->interfaceUp(iface);
        }
    }

    virtual void interfaceDown(NetInterface *iface)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ifaceListenersMutex);
        std::set<InterfaceStatusListener *>::iterator iter;
         for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
             InterfaceStatusListener *listener = *iter;
             listener->interfaceDown(iface);
         }
    }

    virtual void interfaceError(NetInterface *iface, netinterfaceenums::NetInterfaceErrorCode err)
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, ifaceListenersMutex);
        std::set<InterfaceStatusListener *>::iterator iter;
         for (iter = ifaceListeners.begin(); iter != ifaceListeners.end(); ++iter) {
             InterfaceStatusListener *listener = *iter;
             listener->interfaceError(iface, err);
         }
    }

private:
    struct QItem {
        uint8_t *message;
        size_t length;
        char *endpointId;
        bool generic;
        
        QItem(uint8_t *message, char *endpointId) :
                  message(message), length(0),
                  endpointId(endpointId), generic(false)
        {
        }
        QItem(uint8_t *data, size_t length, char *endpointId) :
                  message(data), length(length),
                  endpointId(endpointId), generic(true)
        {
        }
        ~QItem()
        {
            delete[] message;
            delete[] endpointId;
        }
        
      private:
        COMMO_DISALLOW_COPY(QItem);
    };

    CommoLogger *logger;
    PGSC::Thread::Mutex queueMutex;
    PGSC::Thread::CondVar queueMonitor;
    std::deque<QItem *> queue;
    PGSC::Thread::Mutex listenerMutex;
    std::set<CoTMessageListener *> listeners;
    std::set<GenericDataListener *> genListeners;

    std::set<InterfaceStatusListener *> ifaceListeners;
    PGSC::Thread::Mutex ifaceListenersMutex;
};


}

namespace atakmap {
namespace commoncommo {
namespace impl {


struct CommoImpl
{
    CommoImpl(CommoLogger *logger, const ContactUID *ourUID,
            const char *ourCallsign, netinterfaceenums::NetInterfaceAddressMode addrMode) :
                logger(logger), ourUID(NULL), ourCallsign(ourCallsign),
                scanner(NULL),
                dgMgmt(NULL), tcpMgmt(NULL), streamMgmt(NULL),
                contactMgmt(NULL), listenerMgmt(NULL),
                mpMutex(),
                missionPkgMgmt(NULL),
                httpsProxy(NULL),
                simpleIOMgmt(NULL),
                crypto(NULL),
                urlMgmt(NULL),
                cloudMgmt(NULL),
                providerTracker(NULL)
    {
        this->ourUID = new InternalContactUID(ourUID);
        scanner = new HWIFScanner(logger, addrMode);
        dgMgmt = new DatagramSocketManagement(logger, this->ourUID, scanner);
        tcpMgmt = new TcpSocketManagement(logger, this->ourUID);
        streamMgmt = new StreamingSocketManagement(logger,
                std::string((const char *)ourUID->contactUID,
                            ourUID->contactUIDLen));
        contactMgmt = new ContactManager(logger, dgMgmt, tcpMgmt, streamMgmt);
        listenerMgmt = new CoTListenerManagement(logger);
        crypto = new CryptoUtil(logger);
        providerTracker = new FileIOProviderTracker();
        urlMgmt = new URLRequestManager(logger, providerTracker);
        cloudMgmt = new CloudIOManager(logger, urlMgmt);

        scanner->addListener(dgMgmt);
        dgMgmt->addDatagramReceiver(listenerMgmt);
        tcpMgmt->addMessageReceiver(listenerMgmt);
        streamMgmt->addStreamingMessageListener(listenerMgmt);
        dgMgmt->addInterfaceStatusListener(listenerMgmt);
        tcpMgmt->addInterfaceStatusListener(listenerMgmt);
        streamMgmt->addInterfaceStatusListener(listenerMgmt);

    };
    ~CommoImpl()
    {
        // Remove listener connections first, in reverse order
        if (missionPkgMgmt) {
            streamMgmt->removeStreamingMessageListener(missionPkgMgmt);
            dgMgmt->removeDatagramReceiver(missionPkgMgmt);
            tcpMgmt->removeMessageReceiver(missionPkgMgmt);
        }
        dgMgmt->removeInterfaceStatusListener(listenerMgmt);
        tcpMgmt->removeInterfaceStatusListener(listenerMgmt);
        streamMgmt->removeInterfaceStatusListener(listenerMgmt);
        dgMgmt->removeDatagramReceiver(listenerMgmt);
        tcpMgmt->removeMessageReceiver(listenerMgmt);
        streamMgmt->removeStreamingMessageListener(listenerMgmt);
        scanner->removeListener(dgMgmt);

        // Delete top of stack down
        delete listenerMgmt;
        delete simpleIOMgmt;
        delete cloudMgmt;
        delete urlMgmt;
        delete httpsProxy;
        delete missionPkgMgmt;
        delete providerTracker;
        delete contactMgmt;
        delete dgMgmt;
        delete tcpMgmt;
        delete streamMgmt;
        delete scanner;
        delete ourUID;
    };

    void setupMPIO(MissionPackageIO *missionPackageIO) COMMO_THROW (std::invalid_argument) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, mpMutex);
        if (missionPkgMgmt)
            throw std::invalid_argument("Already initialized");
        missionPkgMgmt = new MissionPackageManager(logger, contactMgmt,
                                            streamMgmt,
                                            scanner, missionPackageIO,
                                            this->ourUID,
                                            this->ourCallsign,
                                            this->providerTracker);
        streamMgmt->addStreamingMessageListener(missionPkgMgmt);
        dgMgmt->addDatagramReceiver(missionPkgMgmt);
        tcpMgmt->addMessageReceiver(missionPkgMgmt);
        missionPkgMgmt->setMPTransferSettings(mpSettings);
        httpsProxy = new HttpsProxy(logger);
    }

    void enableSimpleIO(SimpleFileIO *simpleIO) COMMO_THROW (std::invalid_argument) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, mpMutex);
        if (simpleIOMgmt)
            throw std::invalid_argument("Already initialized");
        simpleIOMgmt = new SimpleFileIOManager(logger, simpleIO, this->providerTracker);
    }

    void setCallsign(const char *cs) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, mpMutex);
        ourCallsign = cs;
        if (missionPkgMgmt)
            missionPkgMgmt->setCallsign(cs);
    }
    
    void copyMPSettings() {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, mpMutex);
        if (missionPkgMgmt) {
            missionPkgMgmt->setMPTransferSettings(mpSettings);
            try {
                httpsProxy->setConnTimeoutSec(mpSettings.getConnTimeoutSec());
            } catch (std::invalid_argument &) {
                // Cannot happen as timeout is checked in mpsettings
            }
        }
    }

    void registerFileIOProvider(std::shared_ptr<FileIOProvider>& ioProvider) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, mpMutex);
        if (providerTracker) {
            providerTracker->registerProvider(ioProvider);
        }
    }

    void deregisterFileIOProvider(const FileIOProvider& ioProvider) {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, mpMutex);
        if (providerTracker) {
            providerTracker->deregisterProvider(ioProvider);
        }
    }



    CommoLogger *logger;
    InternalContactUID *ourUID;
    std::string ourCallsign;
    HWIFScanner *scanner;
    DatagramSocketManagement *dgMgmt;
    TcpSocketManagement *tcpMgmt;
    StreamingSocketManagement *streamMgmt;
    ContactManager *contactMgmt;
    CoTListenerManagement *listenerMgmt;
    PGSC::Thread::Mutex mpMutex;
    MissionPackageManager *missionPkgMgmt;
    HttpsProxy *httpsProxy;
    SimpleFileIOManager *simpleIOMgmt;
    MPTransferSettings mpSettings;
    CryptoUtil *crypto;
    URLRequestManager *urlMgmt;
    CloudIOManager *cloudMgmt;
    FileIOProviderTracker *providerTracker;
};




}
}
}

Commo::Commo(CommoLogger *logger, const ContactUID *ourUID,
        const char *ourCallsign, netinterfaceenums::NetInterfaceAddressMode addrMode) : impl(NULL)
{
    impl = new impl::CommoImpl(logger, ourUID, ourCallsign, addrMode);
}

Commo::~Commo()
{
    shutdown();
}

CommoResult Commo::setupMissionPackageIO(MissionPackageIO *missionPackageIO)
{
    if (!missionPackageIO)
        return COMMO_ILLEGAL_ARGUMENT;

    InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_DEBUG, "Setting up MPIO using %p", missionPackageIO);

    try {
        impl->setupMPIO(missionPackageIO);
    } catch (std::invalid_argument &e) {
        InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_ERROR, "Could not set up mission package IO: %s", e.what());
        return COMMO_ILLEGAL_ARGUMENT;
    }
    return COMMO_SUCCESS;
}


CommoResult Commo::enableSimpleFileIO(SimpleFileIO *simpleIO)
{
    if (!simpleIO)
        return COMMO_ILLEGAL_ARGUMENT;

    try {
        impl->enableSimpleIO(simpleIO);
    } catch (std::invalid_argument &e) {
        InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_ERROR, "Could not enable simple file IO: %s", e.what());
        return COMMO_ILLEGAL_ARGUMENT;
    }

    return COMMO_SUCCESS;
}


void Commo::shutdown()
{
    delete impl;
    impl = NULL;
}

void Commo::setCallsign(const char *cs)
{
    impl->setCallsign(cs);
}

void Commo::registerFileIOProvider(std::shared_ptr<FileIOProvider>& provider)
{
    impl->registerFileIOProvider(provider);
}

void Commo::deregisterFileIOProvider(const FileIOProvider& provider)
{
    impl->deregisterFileIOProvider(provider);
}

void Commo::setWorkaroundQuirks(int quirksMask) {
    if (quirksMask & QUIRK_MAGTAB) {
        impl->logger->log(CommoLogger::LEVEL_INFO, "Magtab Quirk enabled");
        impl->dgMgmt->setMagtabWorkaroundEnabled(true);
    } else {
        impl->logger->log(CommoLogger::LEVEL_INFO, "Magtab Quirk disabled");
        impl->dgMgmt->setMagtabWorkaroundEnabled(false);
    }
}


CommoResult Commo::addInterfaceStatusListener(InterfaceStatusListener *listener)
{
    return impl->listenerMgmt->addInterfaceStatusListener(listener);
}

CommoResult Commo::removeInterfaceStatusListener(InterfaceStatusListener *listener)
{
    return impl->listenerMgmt->removeInterfaceStatusListener(listener);
}


PhysicalNetInterface *Commo::addInboundInterface(const HwAddress *hwAddress, int port, const char **mcastAddrs, size_t nMcastAddrs, bool asGeneric)
{
    PhysicalNetInterface *iface = impl->dgMgmt->addInboundInterface(hwAddress, port, mcastAddrs, nMcastAddrs, asGeneric);
    return iface;
}

CommoResult Commo::removeInboundInterface(PhysicalNetInterface *iface)
{
    return impl->dgMgmt->removeInboundInterface(iface);
}

TcpInboundNetInterface *Commo::addTcpInboundInterface(int port)
{
    return impl->tcpMgmt->addInboundInterface(port);
}

CommoResult Commo::removeTcpInboundInterface(TcpInboundNetInterface *iface)
{
    return impl->tcpMgmt->removeInboundInterface(iface);
}

PhysicalNetInterface* Commo::addBroadcastInterface(
        const HwAddress *hwAddress, const CoTMessageType *types, size_t nTypes,
        const char *mcastAddr, int destPort)
{
    return impl->dgMgmt->addBroadcastInterface(hwAddress, types, nTypes, mcastAddr, destPort);
}

PhysicalNetInterface* Commo::addBroadcastInterface(
        const CoTMessageType *types, size_t nTypes,
        const char *unicastAddr, int destPort)
{
    return impl->dgMgmt->addBroadcastInterface(NULL, types, nTypes, unicastAddr, destPort);
}

CommoResult Commo::removeBroadcastInterface(PhysicalNetInterface *iface)
{
    return impl->dgMgmt->removeBroadcastInterface(iface);
}

void Commo::setPreferStreamEndpoint(bool preferStream)
{
    impl->contactMgmt->setPreferStreamEndpoint(preferStream);
}

void Commo::setAdvertiseEndpointAsUdp(bool en)
{
    impl->dgMgmt->setAdvertiseEndpointAsUdp(en);
}

CommoResult Commo::setCryptoKeys(const uint8_t *authKey, const uint8_t *cryptoKey)
{
    if ((authKey == NULL) ^ (cryptoKey == NULL)) {
        InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_ERROR,
                                 "setCryptoKeys called with bad args. Both or neither key must be null");
        
        return COMMO_ILLEGAL_ARGUMENT;
    }
    if (authKey != NULL && memcmp(authKey, cryptoKey, 
                                  MeshNetCrypto::KEY_BYTE_LEN) == 0) {
        InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_ERROR,
                                 "setCryptoKeys called with bad args; keys must be unique");
        
        return COMMO_ILLEGAL_ARGUMENT;
    }
    InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_INFO,
                           "Mesh network encryption %s", authKey ? "ENABLED" : "DISABLED");

    impl->dgMgmt->setCryptoKeys(authKey, cryptoKey);
    impl->tcpMgmt->setCryptoKeys(authKey, cryptoKey);
    return COMMO_SUCCESS;
}

void Commo::setEnableAddressReuse(bool en)
{
    impl->dgMgmt->setEnableAddressReuse(en);
}

void Commo::setMulticastLoopbackEnabled(bool en)
{
    impl->dgMgmt->setMulticastLoopbackEnabled(en);
}

void Commo::setTTL(int ttl)
{
    impl->dgMgmt->setTTL(ttl);
}

void Commo::setUdpNoDataTimeout(int seconds)
{
    impl->dgMgmt->setRxTimeoutSecs(seconds);
}

void Commo::setTcpConnTimeout(int seconds)
{
    if (seconds < 2)
        seconds = 2;
    impl->tcpMgmt->setConnTimeout((float)seconds);
    impl->streamMgmt->setConnTimeout((float)seconds);
}

void Commo::setStreamMonitorEnabled(bool enable)
{
    impl->streamMgmt->setMonitor(enable);
}

int Commo::getBroadcastProto()
{
    return impl->contactMgmt->getProtoVersion();
}


CommoResult Commo::setMissionPackageLocalPort(int localWebPort)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);
        InternalUtils::logprintf(impl->logger, CommoLogger::LEVEL_DEBUG, "Checking MP %p for port set", impl->missionPkgMgmt);

        if (!impl->missionPkgMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }

    try {
        impl->missionPkgMgmt->setLocalPort(localWebPort);
        impl->httpsProxy->setLocalHttpPort(localWebPort);
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

void Commo::setMissionPackageViaServerEnabled(bool enabled)
{
    impl->mpSettings.setServerTransferEnabled(enabled);
    impl->copyMPSettings();
}

CommoResult Commo::setMissionPackageHttpPort(int port)
{
    try {
        impl->mpSettings.setHttpPort(port);
        impl->copyMPSettings();
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::setMissionPackageHttpsPort(int port)
{
    try {
        impl->mpSettings.setHttpsPort(port);
        impl->copyMPSettings();
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::setMissionPackageNumTries(int nTries)
{
    try {
        impl->mpSettings.setNumTries(nTries);
        impl->copyMPSettings();
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::setMissionPackageConnTimeout(int seconds)
{
    try {
        impl->mpSettings.setConnTimeoutSec(seconds);
        impl->copyMPSettings();
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::setMissionPackageTransferTimeout(int seconds)
{
    try {
        impl->mpSettings.setXferTimeoutSec(seconds);
        impl->copyMPSettings();
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}


CommoResult Commo::setMissionPackageLocalHttpsParams(int port,
                           const uint8_t *cert,
                           size_t certLen, const char *certPass)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);

        if (!impl->missionPkgMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }
    
    CommoResult ret = impl->httpsProxy->setServerParams(port, cert, 
                                                        certLen, certPass);
    if (ret == COMMO_SUCCESS)
        impl->missionPkgMgmt->setLocalHttpsPort(port);
    else
        impl->missionPkgMgmt->setLocalHttpsPort(MP_LOCAL_PORT_DISABLE);

    return ret;
}




StreamingNetInterface* Commo::addStreamingInterface(
        const char *hostname, int port, const CoTMessageType *types,
        size_t nTypes, const uint8_t *clientCert, size_t clientCertLen,
        const uint8_t *caCert, size_t caCertLen,
        const char *certPassword,
        const char *caCertPassword,
        const char *username, const char *password,
        CommoResult *result)
{
    return impl->streamMgmt->addStreamingInterface(hostname, port, types,
            nTypes, clientCert, clientCertLen,
            caCert, caCertLen, certPassword, caCertPassword,
            username, password, result);
}

CommoResult Commo::removeStreamingInterface(StreamingNetInterface *iface)
{
    std::string ep = iface->remoteEndpointId;
    CommoResult rc = impl->streamMgmt->removeStreamingInterface(iface);
    if (rc == COMMO_SUCCESS)
        impl->contactMgmt->removeStream(ep);
    return rc;
}



const ContactList *Commo::getContactList()
{
    return impl->contactMgmt->getAllContacts();
}

void Commo::freeContactList(const ContactList *contactList)
{
    ContactManager::freeContactList(contactList);
}

CommoResult Commo::configKnownEndpointContact(const ContactUID *contact,
                                              const char *callsign,
                                              const char *ipAddr,
                                              int destPort)
{
    return impl->contactMgmt->configKnownEndpointContact(contact,
                                                         callsign,
                                                         ipAddr, destPort);
}


CommoResult Commo::addContactPresenceListener(ContactPresenceListener *listener)
{
    try {
        impl->contactMgmt->addContactPresenceListener(listener);
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
    return COMMO_SUCCESS;
}

CommoResult Commo::removeContactPresenceListener(ContactPresenceListener *listener)
{
    try {
        impl->contactMgmt->removeContactPresenceListener(listener);
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
    return COMMO_SUCCESS;
}






CommoResult Commo::broadcastCoT(const char* cotMessage, CoTSendMethod sendMethod)
{
    try {
        static const std::vector<std::string> takServerDests;
        size_t len = strlen(cotMessage);
        if (len > maxUDPMessageSize)
            return COMMO_ILLEGAL_ARGUMENT;
        CoTMessage msg(impl->logger, (const uint8_t *)cotMessage,
                                     len);
        if (sendMethod & SEND_POINT_TO_POINT)
            impl->dgMgmt->sendMulticast(&msg);
        if (sendMethod & SEND_TAK_SERVER) {
            msg.setEndpoint(ENDPOINT_STREAMING, "");
            msg.setTAKServerRecipients(&takServerDests);
            impl->streamMgmt->sendBroadcast(&msg);
        }
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}


CommoResult Commo::sendCoT(ContactList *destinations, const char *cotMessage, CoTSendMethod sendMethod)
{
    try {
        size_t len = strlen(cotMessage);
        if (len > maxUDPMessageSize)
            return COMMO_ILLEGAL_ARGUMENT;
        CoTMessage msg(impl->logger, (const uint8_t *)cotMessage,
                                     len);
        return impl->contactMgmt->sendCoT(destinations, &msg, sendMethod);
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::sendCoTTcpDirect(const char *host, int port, const char *cotMessage)
{
    std::string hostStr(host);
    try {
        size_t len = strlen(cotMessage);
        if (len > maxUDPMessageSize)
            return COMMO_ILLEGAL_ARGUMENT;
        CoTMessage msg(impl->logger,
                       (const uint8_t *)cotMessage, len);
        impl->tcpMgmt->sendMessage(hostStr, port, &msg, 0);
        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::sendCoTServerControl(const char *streamingRemoteId,
                                        const char *cotMessage)
{
    /*
     * Use all F's (bogus callsign) so server will not route out
     * to other users, rather just store in server DB.
     */
    static const char *SERVER_CTRL_CS = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    try {
        std::vector<std::string> takServerDests;
        takServerDests.push_back(std::string(SERVER_CTRL_CS));

        size_t len = strlen(cotMessage);
        if (len > maxUDPMessageSize)
            return COMMO_ILLEGAL_ARGUMENT;
        CoTMessage msg(impl->logger,
                       (const uint8_t *)cotMessage, len);
        msg.setEndpoint(ENDPOINT_STREAMING, "");
        msg.setTAKServerRecipients(&takServerDests);
        if (streamingRemoteId)
            impl->streamMgmt->sendMessage(streamingRemoteId, &msg);
        else
            impl->streamMgmt->sendBroadcast(&msg, true);

        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::sendCoTToServerMissionDest(const char *streamingRemoteId,
                                              const char *mission,
                                              const char *cotMessage)
{
    try {
        size_t len = strlen(cotMessage);
        if (len > maxUDPMessageSize)
            return COMMO_ILLEGAL_ARGUMENT;
        CoTMessage msg(impl->logger,
                       (const uint8_t *)cotMessage, len);
        msg.setEndpoint(ENDPOINT_STREAMING, "");
        msg.setTAKServerMissionRecipient(mission);
        if (streamingRemoteId)
            impl->streamMgmt->sendMessage(streamingRemoteId, &msg);
        else
            impl->streamMgmt->sendBroadcast(&msg, true);

        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::addCoTMessageListener(CoTMessageListener *listener)
{
    return impl->listenerMgmt->addCoTMessageListener(listener);
}

CommoResult Commo::removeCoTMessageListener(CoTMessageListener *listener)
{
    return impl->listenerMgmt->removeCoTMessageListener(listener);
}


CommoResult Commo::addGenericDataListener(GenericDataListener *listener)
{
    return impl->listenerMgmt->addGenericDataListener(listener);
}

CommoResult Commo::removeGenericDataListener(GenericDataListener *listener)
{
    return impl->listenerMgmt->removeGenericDataListener(listener);
}


CommoResult Commo::addCoTSendFailureListener(CoTSendFailureListener *listener)
{
    return impl->tcpMgmt->addCoTSendFailureListener(listener);
}

CommoResult Commo::removeCoTSendFailureListener(CoTSendFailureListener *listener)
{
    return impl->tcpMgmt->removeCoTSendFailureListener(listener);
}


CommoResult Commo::simpleFileTransferInit(int *xferId,
                                      bool isUpload,
                                      const char *remoteURL,
                                      const uint8_t *caCert, size_t caCertLen,
                                      const char *caCertPassword,
                                      const char *remoteUsername,
                                      const char *remotePassword,
                                      const char *localFileName)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);
        if (!impl->simpleIOMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }

    if (isUpload)
        return impl->simpleIOMgmt->uploadFile(xferId, remoteURL,
                                              caCert, caCertLen,
                                              caCertPassword,
                                              remoteUsername,
                                              remotePassword,
                                              localFileName);
    else
        return impl->simpleIOMgmt->downloadFile(xferId, localFileName, 
                                              remoteURL,
                                              caCert, caCertLen,
                                              caCertPassword,
                                              remoteUsername,
                                              remotePassword);
}


CommoResult Commo::simpleFileTransferStart(int xferId)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);
        if (!impl->simpleIOMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }

    return impl->simpleIOMgmt->startTransfer(xferId);
}

CommoResult Commo::createCloudClient(CloudClient **result, 
                         CloudIO *io,
                         CloudIOProtocol proto,
                         const char *host,
                         int port,
                         const char *basePath,
                         const char *user,
                         const char *pass,
                         const uint8_t *caCerts,
                         size_t caCertsLen,
                         const char *caCertPass)
{
    return impl->cloudMgmt->createCloudClient(result, io, proto, 
                                              host, port, basePath,
                                              user, pass,
                                              caCerts, caCertsLen,
                                              caCertPass);
}

CommoResult Commo::destroyCloudClient(CloudClient *client)
{
    return impl->cloudMgmt->destroyCloudClient(client);
}

CommoResult Commo::sendMissionPackageInit(int *xferId, ContactList *destinations,
                                      const char *filePath,
                                      const char *fileName,
                                      const char *name)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);
        if (!impl->missionPkgMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }

    return impl->missionPkgMgmt->sendFileInit(xferId, destinations,
                                          filePath, fileName, name);
}


CommoResult Commo::sendMissionPackageInit(int *xferId, 
                                      const char *streamingRemoteId,
                                      const char *filePath,
                                      const char *fileName)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);
        if (!impl->missionPkgMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }

    return impl->missionPkgMgmt->uploadFileInit(xferId, streamingRemoteId,
                                            filePath, fileName);
}


CommoResult Commo::sendMissionPackageStart(int xferId)
{
    {
        PGSC::Thread::LockPtr lock(NULL, NULL);
        PGSC::Thread::Lock_create(lock, impl->mpMutex);
        if (!impl->missionPkgMgmt)
            return COMMO_ILLEGAL_ARGUMENT;
    }

    return impl->missionPkgMgmt->sendFileStart(xferId);
}


char *Commo::generateKeyCryptoString(const char *password, const int keyLen)
{
    return impl->crypto->generateKeyCryptoString(password, keyLen);
}

char *Commo::generateCSRCryptoString(const char **entryKeys, 
                                     const char **entryValues, 
                                     size_t nEntries, 
                                     const char* pem, 
                                     const char* password)
{
    return impl->crypto->generateCSRCryptoString(entryKeys,
                            entryValues, nEntries, pem, password);
}

char *Commo::generateKeystoreCryptoString(const char *certPem,
                       const char **caPem, size_t nCa,
                       const char *pkeyPem, const char *password, 
                       const char *friendlyName)
{
    return impl->crypto->generateKeystoreCryptoString(
        certPem, caPem, nCa,
        pkeyPem, password,
        friendlyName);
}

void Commo::freeCryptoString(char *cryptoString)
{
    impl->crypto->freeCryptoString(cryptoString);
}

size_t Commo::generateSelfSignedCert(uint8_t **cert, const char *password)
{
    return impl->crypto->generateSelfSignedCert(cert, password);
}

void Commo::freeSelfSignedCert(uint8_t *cert)
{
    impl->crypto->freeSelfSignedCert(cert);
}




CommoResult Commo::cotXmlToTakproto(char **protodata, size_t *dataLen,
                                    const char *cotXml, int desiredVersion)
{
    try {
        CoTMessage msg(impl->logger, (const uint8_t *)cotXml,
                                     strlen(cotXml));

        TakMessage takmsg(impl->logger, &msg, NULL, false);
        size_t ret = takmsg.serializeAsProtobuf(desiredVersion,
                               (uint8_t **)protodata, 
                               TakMessage::HEADER_TAKPROTO,
                               true,
                               false); 
        *dataLen = ret;

        return COMMO_SUCCESS;
    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

CommoResult Commo::takprotoToCotXml(char **cotXml,
                                    const char *protodata,
                                    const size_t dataLen)
{
    try {
        TakMessage takmsg(impl->logger, (const uint8_t *)protodata,
                          dataLen, true, true);
        const CoTMessage *msg = takmsg.getCoTMessage();
        if (!msg)
            throw std::invalid_argument("");
        
        msg->serialize((uint8_t **)cotXml);

        return COMMO_SUCCESS;

    } catch (std::invalid_argument &) {
        return COMMO_ILLEGAL_ARGUMENT;
    }
}

void Commo::takmessageFree(char *takmessage)
{
    delete[] takmessage;
}

