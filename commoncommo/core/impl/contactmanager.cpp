#include "contactmanager.h"
#include "commothread.h"
#include <string.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;


namespace {
    // Time in seconds where a direct connection to a contact must be stagnant
    // (no rx'd data) to prefer any known streaming endpoint.
    const float CONTACT_DIRECT_TIMEOUT = 60.0f;

    const char *THREAD_NAMES[] = {
        "cmoctact.evnt", 
        "cmotcp.proto",
    };
}


/**********************************************************************/
// Constructor/destructor


ContactManager::ContactManager(CommoLogger* logger,
        DatagramSocketManagement *dgMgmt,
        TcpSocketManagement *tcpMgmt,
        StreamingSocketManagement *streamMgmt) : 
                ThreadedHandler(2, THREAD_NAMES),
                DatagramListener(),
                StreamingMessageListener(),
                logger(logger),
                dgMgr(dgMgmt), tcpMgmt(tcpMgmt), streamMgmt(streamMgmt),
                contacts(), contactMapMutex(thread::RWMutex::Policy::Policy_Fair),
                protoVMutex(), protoVMonitor(),
                protoVersion(TakProtoInfo::SELF_MAX),
                protoVDirty(false),
                preferStreaming(false),
                listeners(), listenerMutex(), eventQueue(),
                eventQueueMutex(), eventQueueMonitor()
{
    // Register as a listener with the datagram layer, tcp layer,
    // and streaming layer
    dgMgr->addDatagramReceiver(this);
    dgMgr->protoLevelChange(protoVersion);
    streamMgmt->addStreamingMessageListener(this);
    streamMgmt->addInterfaceStatusListener(this);

    startThreads();
}

ContactManager::~ContactManager()
{
    // De-register as a listener
    dgMgr->removeDatagramReceiver(this);
    streamMgmt->removeStreamingMessageListener(this);

    stopThreads();

    // Clean up all the contact states
    ContactMap::iterator iter;
    for (iter = contacts.begin(); iter != contacts.end(); iter++) {
        delete iter->second;
        delete (const InternalContactUID *)iter->first;
    }

    // Clean any remaining queue items
    std::deque<std::pair<InternalContactUID *, bool> >::iterator qiter;
    for (qiter = eventQueue.begin(); qiter != eventQueue.end(); ++qiter)
        delete qiter->first;
}


/**********************************************************************/
// ThreadedHandler stuff


void ContactManager::threadStopSignal(size_t threadNum)
{
    switch (threadNum) {
    case EVENT_THREADID:
        {
            thread::Lock lock(eventQueueMutex);
            eventQueueMonitor.broadcast(lock);
            break;
        }
    case PROTOV_THREADID:
        {
            thread::Lock lock(protoVMutex);
            protoVMonitor.broadcast(lock);
            break;
        }
    }
}

void ContactManager::threadEntry(size_t threadNum)
{
    switch (threadNum) {
    case EVENT_THREADID:
        queueThread();
        break;
    case PROTOV_THREADID:
        protoVThreadProcess();
        break;
    }
}

/**********************************************************************/
// Protocol version management thread

void ContactManager::protoVThreadProcess()
{
    int fireVersion = 0;

    while (!threadShouldStop(PROTOV_THREADID)) {
        bool fireUpdate = false;
        CommoTime nowTime = CommoTime::now();
        {
            thread::Lock lock(protoVMutex);
            
            unsigned int uberMin = TakProtoInfo::SELF_MIN;
            unsigned int uberMax = TakProtoInfo::SELF_MAX;
            int newProtoV = uberMax;
            {
                thread::ReadLock lock(contactMapMutex);
                
                ContactMap::iterator iter;
                for (iter = contacts.begin(); iter != contacts.end(); iter++) {
                    ContactState *state = iter->second;
                    thread::Lock stateLock(state->mutex);
                    
                    std::string dbgUid((const char *)iter->first->contactUID,
                                       iter->first->contactUIDLen);
                    bool epStale = nowTime >= state->meshEndpointExpireTime;
#if 0
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                        "ProtoScan: %s %s EpExp %s ProtoInfExp %s min %d max %d fromep %d",
                        dbgUid.c_str(), state->callsign.c_str(), 
                        !epStale ? InternalUtils::intToString((state->meshEndpointExpireTime - nowTime).getSeconds()).c_str() : "StaleOrNever",
                        state->protoInfValid ? InternalUtils::intToString((state->protoInfExpireTime - nowTime).getSeconds()).c_str() : "NoProtoInf",
                        state->protoInf.first,
                        state->protoInf.second,
                        state->lastMeshEndpointProto);
#endif
                    
                    if (state->protoInfValid &&
                            nowTime >= state->protoInfExpireTime) {
                        state->protoInfValid = false;
                        state->protoInf.first = state->protoInf.second = 
                                  state->lastMeshEndpointProto;
                    }

                    if (epStale) {
                        // Don't factor this guy into protov calc
                        // He's disappeared or never had a mesh endpoint
                        continue;
                    }
                    
                    if (newProtoV) {
                        uberMin = 
                            uberMin > state->protoInf.first ?
                                uberMin : state->protoInf.first;
                        uberMax = 
                            uberMax < state->protoInf.second ?
                                uberMax : state->protoInf.second;
                        if (uberMin > uberMax)
                            // No overlap; give up, go to 0
                            newProtoV = 0;
                    }
                }
            }
            
            if (newProtoV)
                newProtoV = uberMax;
            if (newProtoV != protoVersion) {
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                    "ProtoScan: Switching broadcast protocol version from %d to %d",
                    protoVersion, newProtoV);
                protoVersion = fireVersion = newProtoV;
                fireUpdate = true;
            } else {
#if 0
                InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG,
                    "ProtoScan: No change to broadcast protocol version (%d)",
                    protoVersion);
#endif
            }
            protoVDirty = false;
        }

        if (fireUpdate) {
            protoVThreadFireVersionChange(protoVersion);
        }
        
        {
            thread::Lock lock(protoVMutex);

            if (!protoVDirty && !threadShouldStop(PROTOV_THREADID)) {
                protoVMonitor.wait(lock, 
                    TakMessage::PROTOINF_TIMEOUT_SEC / 4 * 1000);
            }
        }
        
    }
}

void ContactManager::protoVThreadFireVersionChange(int newVersion)
{
    dgMgr->protoLevelChange(newVersion);
}

int ContactManager::getProtoVersion()
{
    return protoVersion;
}


/**********************************************************************/
// Rx data


void ContactManager::datagramReceivedGeneric(
        const std::string *endpointId,
        const uint8_t *data, size_t length)
{
}

void ContactManager::datagramReceived(
        const std::string *endpointId,
        const NetAddress *sender,
        const TakMessage *takmsg)
{
    const CoTMessage *msg = takmsg->getCoTMessage();
    const TakProtoInfo *protoInf = takmsg->getProtoInfo();
    const ContactUID *uid = takmsg->getContactUID();
    unsigned int msgVer = takmsg->getProtoVersion();

    std::string ep;
    EndpointType type = ENDPOINT_TCP;
    if (msg) {
        ep = msg->getEndpointHost();
        type = msg->getEndpointType();
    }
    switch (type) {
        case ENDPOINT_UDP:
            msgRxImpl(ep, uid, protoInf, msg, msgVer, CoTEndpoint::DATAGRAM);
            break;
        case ENDPOINT_TCP_USESRC:
            if (sender) {
                // Use sender's source address
                sender->getIPString(&ep);
                msgRxImpl(ep, uid, protoInf, msg, msgVer, CoTEndpoint::TCP);
            }
            break;
        case ENDPOINT_UDP_USESRC:
            if (sender) {
                // Use sender's source address
                sender->getIPString(&ep);
                msgRxImpl(ep, uid, protoInf, msg,
                          msgVer, CoTEndpoint::DATAGRAM);
            }
            break;
        case ENDPOINT_TCP:
            msgRxImpl(ep, uid, protoInf, msg, msgVer, CoTEndpoint::TCP);
            break;
        default:
            break;
    }
}

void ContactManager::streamingMessageReceived(
        std::string streamingEndpoint, const CoTMessage *msg)
{
    EndpointType msgEpType = msg->getEndpointType();
    if (msgEpType == ENDPOINT_NONE)
        // If no valid endpoint (even though we don't need or use the value),
        // it is not to be considered a contact
        return;
    msgRxImpl(streamingEndpoint, msg->getContactUID(), NULL,
              msg, 0, CoTEndpoint::STREAMING);
}


void ContactManager::msgRxImpl(
        const std::string &endpoint, 
        const ContactUID *uid,
        const TakProtoInfo *protoInf,
        const CoTMessage *msg, unsigned int msgVer,
        CoTEndpoint::InterfaceType type)
{
    if (!uid)
        return;

    std::string callsign;
    bool protoOnly = msg == NULL;
    int port = 0;
    if (!protoOnly) {
        callsign = msg->getCallsign();
        if (!endpoint.empty())
            port = msg->getEndpointPort();
        else if (protoInf)
            // No valid endpoint/contact update,
            // but should still update proto info if present
            protoOnly = true;
        else
            return;
    }
    
    processContactUpdate(protoOnly, msgVer, uid, protoInf, 
                         false, type, endpoint, callsign,
                         port);
}

CommoResult ContactManager::processContactUpdate(
        bool protoInfOnly,
        unsigned int msgVer,
        const ContactUID *uid,
        const TakProtoInfo *protoInf,
        bool knownEP,
        CoTEndpoint::InterfaceType type,
        const std::string &endpoint,
        const std::string &callsign,
        int port)
{
    if (knownEP && type != CoTEndpoint::DATAGRAM)
        return COMMO_ILLEGAL_ARGUMENT;

    ContactState *state = NULL;
    bool isOk = true;
    bool protoNeedsRefresh = false;
    {
        thread::ReadLock lock(contactMapMutex);
        ContactMap::iterator iter = contacts.find(uid);
        if (iter != contacts.end()) {
            state = iter->second;
            isOk = processContactStateUpdate(state, &protoNeedsRefresh,
                                             protoInfOnly,
                                             msgVer,
                                             protoInf, knownEP,
                                             type, endpoint, callsign,
                                             port);
        }
    }
    if (!protoInfOnly && !state) {
        // Retry with the write lock so we can add new if needed
        thread::WriteLock lock(contactMapMutex);
        ContactMap::iterator iter = contacts.find(uid);
        bool isNew = (iter == contacts.end());
        if (!isNew) {
            state = iter->second;
        } else {
            state = new ContactState(knownEP);
        }
        isOk = processContactStateUpdate(state, &protoNeedsRefresh,
                                         protoInfOnly,
                                         msgVer,
                                         protoInf, knownEP,
                                         type, endpoint, callsign,
                                         port);

        if (isNew) {
            if (!isOk) {
                delete state;
            } else {
                ContactUID *intUID = new InternalContactUID(uid);
                contacts[intUID] = state;
                queueContactPresenceChange(intUID, true);
                protoNeedsRefresh = true;
            }
        }
    }
    if (protoNeedsRefresh) {
        thread::Lock vLock(protoVMutex);
        protoVDirty = true;
        protoVMonitor.broadcast(vLock);
    }
    return isOk ? COMMO_SUCCESS : COMMO_ILLEGAL_ARGUMENT;
}

bool ContactManager::processContactStateUpdate(ContactState *state,
                            bool *protoNeedsRefresh,
                            bool protoInfOnly,
                            unsigned int msgVer,
                            const TakProtoInfo *protoInf,
                            bool knownEP,
                            CoTEndpoint::InterfaceType type,
                            const std::string &endpoint,
                            const std::string &callsign,
                            int port)
{
    bool isOk = true;
    bool needRecompute = false;
    CommoTime nowTime = CommoTime::now();
    {
        thread::Lock sLock(state->mutex);
        if (knownEP != state->knownEndpoint) {
            *protoNeedsRefresh = false;
            return false;
        }
        
        bool epWasNotStale = nowTime <= state->meshEndpointExpireTime;
        // If it fails, we ignore this message and keep old endpoint(s)
        if (!protoInfOnly) {
            switch (type) {
            case CoTEndpoint::DATAGRAM:
                isOk = updateStateDatagramEndpoint(state, endpoint, port);
                break;
            case CoTEndpoint::TCP:
                isOk = updateStateTcpEndpoint(state, endpoint, port);
                break;
            case CoTEndpoint::STREAMING:
                isOk = updateStateStreamEndpoint(state, endpoint);
                break;
            default:
                isOk = false;
                break;
            }
            state->callsign = callsign;
        }

        // Protocol tracking for discovery-based mesh EP's only
        if (type != CoTEndpoint::STREAMING && !knownEP) {
            bool validEpPresent = isOk && !protoInfOnly;
            bool epNotStale = epWasNotStale || validEpPresent;
            std::pair<unsigned int, unsigned int> oldInfo = state->protoInf;

            if (validEpPresent)
                state->lastMeshEndpointProto = msgVer;

            if (protoInf) {
                state->protoInfValid = true;
                state->protoInfExpireTime = nowTime + 
                                        (float)TakMessage::PROTOINF_TIMEOUT_SEC;
                state->protoInf.first = protoInf->getMin();
                state->protoInf.second = protoInf->getMax();
            } else if (!state->protoInfValid && validEpPresent) {
                state->protoInf.first = state->protoInf.second = msgVer;
            }
            needRecompute = epNotStale && (!epWasNotStale ||
                            (oldInfo != state->protoInf));
        }
    }
    
    *protoNeedsRefresh = needRecompute;
    
    return isOk;
}

void ContactManager::interfaceUp(NetInterface *iface)
{
}

void ContactManager::interfaceDown(NetInterface *iface)
{
    // We only presently listen to streams
    StreamingNetInterface *siface = (StreamingNetInterface *)iface;
    const char *ep = siface->remoteEndpointId;
    removeStream(ep);
}

void ContactManager::removeStream(const std::string &epString)
{
    // We only presently listen to streams
    std::set<ContactUID *> removedUids;

    {
        thread::WriteLock lock(contactMapMutex);
        ContactMap::iterator iter = contacts.begin();
        while (iter != contacts.end()) {
            ContactMap::iterator curIter = iter;
            iter++;
            bool killState = false;
            ContactState *state = curIter->second;
            {
                thread::Lock sLock(state->mutex);
                if (state->streamEndpoint &&
                            state->streamEndpoint->getEndpointString() == epString) {
                    if (!state->datagramEndpoint && !state->tcpEndpoint) {
                        queueContactPresenceChange(curIter->first, false);
                        delete (const InternalContactUID *)curIter->first;
                        contacts.erase(curIter);
                        killState = true;
                    } else {
                        // Don't delete this one - still has other EPs
                        delete state->streamEndpoint;
                        state->streamEndpoint = NULL;
                    }
                    
                }
            }
            if (killState)
                delete state;
        }
    }
}



/**********************************************************************/
// Tx data

CommoResult ContactManager::sendCoT(ContactList *destinations,
        const CoTMessage *cotMessage, CoTSendMethod sendMethod)
{
    std::vector<const ContactUID *> vdest;
    for (size_t i = 0; i < destinations->nContacts; ++i)
        vdest.push_back(destinations->contacts[i]);
    CommoResult ret = sendCoT(&vdest, cotMessage, sendMethod);
    destinations->nContacts = vdest.size();
    for (size_t i = 0; i < destinations->nContacts; ++i)
        destinations->contacts[i] = vdest[i];
    return ret;
}

CommoResult ContactManager::sendCoT(
        std::vector<const ContactUID *> *destinations,
        const CoTMessage *cotMessage, CoTSendMethod sendMethod)
{
    std::vector<const ContactUID *> ret;

    typedef std::pair<std::vector<std::string>, std::vector<const ContactUID *> > CallContactPair;
    std::map<std::string, CallContactPair > streamMap;
    {
        thread::ReadLock lock(contactMapMutex);

        std::map<std::string, CallContactPair >::iterator smIter;
        std::vector<const ContactUID *>::iterator iter;
        for (iter = destinations->begin(); iter != destinations->end(); ++iter) {
            ContactMap::iterator contactIter = contacts.find(*iter);
            if (contactIter == contacts.end()) {
                // This contact is gone
                ret.push_back(*iter);
                continue;
            }
            ContactState *state = contactIter->second;
            {
                thread::Lock stateLock(state->mutex);
                CoTEndpoint *ep = getCurrentEndpoint(state, sendMethod);
                try {
                    if (!ep)
                        // Available endpoints don't match available methods
                        // They aren't "gone", just can't send the
                        // way the caller intends. But per API we treat
                        // them as "gone"
                        throw std::invalid_argument("");

                    std::string contactUidStr(
                            (const char *)contactIter->first->contactUID,
                            contactIter->first->contactUIDLen);

                    switch (ep->getType()) {
                    case CoTEndpoint::DATAGRAM:
                    {
                        DatagramCoTEndpoint *dgce = (DatagramCoTEndpoint *)ep;
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Sending CoT to contact %s using datagram endpoint %s and protocol version %d", contactUidStr.c_str(), dgce->getEndpointString().c_str(), 0);

                        int version = getSendProtoVersion(state);
                        dgMgr->sendDatagram(dgce->getNetAddr(cotMessage->getType()),
                                            cotMessage, version);
                        break;
                    }
                    case CoTEndpoint::TCP:
                    {
                        TcpCoTEndpoint *tcpce = (TcpCoTEndpoint *)ep;
                        int port = tcpce->getPortNum(cotMessage->getType());
                        std::string host = tcpce->getHostString();
                        int version = getSendProtoVersion(state);
                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Sending CoT to contact %s using tcp endpoint %s:%d and protocol version %d", contactUidStr.c_str(), host.c_str(), port, version);

                        tcpMgmt->sendMessage(host, port, cotMessage, version);
                        break;
                    }
                    case CoTEndpoint::STREAMING:
                    {
                        StreamingCoTEndpoint *sce = (StreamingCoTEndpoint *)ep;
                        std::string epString = sce->getEndpointString();

                        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, "Sending CoT to contact %s using TAK server endpoint %s", contactUidStr.c_str(), epString.c_str());

                        smIter = streamMap.find(epString);
                        if (smIter != streamMap.end()) {
                            smIter->second.first.push_back(state->callsign);
                            smIter->second.second.push_back(*iter);
                        } else {
                            CallContactPair ccp;
                            ccp.first.push_back(state->callsign);
                            ccp.second.push_back(*iter);
                            streamMap.insert(std::pair<std::string, CallContactPair>(epString, ccp));
                        }
                        break;
                    }
                    default:
                        throw std::invalid_argument("");
                    }
                } catch (std::invalid_argument &) {
                    // for datagram, interface is gone, which means so is our contact
                    // for streaming, connection is down, which means we can't
                    // reach the contact.
                    // or... Unsupported endpoint type?
                    // all given same treatment.
                    ret.push_back(*iter);
                }
            }
        }
    }

    {
        // Note carefully: We gave up context lock up above
        // (intentionally, to avoid calling back into streamMgmt while holding
        // that lock). Thus, it is UNSAFE
        // to use the UID part of the values of streamMap as they could
        // have been cleaned up externally.
        std::map<std::string, CallContactPair >::iterator smIter;
        for (smIter = streamMap.begin(); smIter != streamMap.end(); ++smIter) {
            try {
                CoTMessage sMsg(*cotMessage);
                sMsg.setEndpoint(ENDPOINT_STREAMING, "");
                CallContactPair ccp = smIter->second;
                std::vector<std::string> &v = ccp.first;
                sMsg.setTAKServerRecipients(&v);
                streamMgmt->sendMessage(smIter->first, &sMsg);
            } catch (std::invalid_argument &) {
                // either stream is down or there some error copying the
                // message (unlikely).
                ret.insert(ret.end(), smIter->second.second.begin(),
                                      smIter->second.second.end());
            }
        }
    }

    destinations->clear();
    destinations->insert(destinations->begin(), ret.begin(), ret.end());
    return ret.empty() ? COMMO_SUCCESS : COMMO_CONTACT_GONE;
}


/**********************************************************************/
// Contact presence listener management

void ContactManager::addContactPresenceListener(
        ContactPresenceListener* listener) COMMO_THROW (std::invalid_argument)
{
    thread::Lock lock(listenerMutex);
    if (!listeners.insert(listener).second)
        throw std::invalid_argument("Listener already registered");
}

void ContactManager::removeContactPresenceListener(
        ContactPresenceListener* listener) COMMO_THROW (std::invalid_argument)
{
    thread::Lock lock(listenerMutex);
    if (listeners.erase(listener) != 1)
        throw std::invalid_argument("Provided listener for removal is not registered");
}

void ContactManager::fireContactPresenceChange(const ContactUID *c, bool present)
{
    thread::Lock lock(listenerMutex);
    std::set<ContactPresenceListener *>::iterator iter;
    for (iter = listeners.begin(); iter != listeners.end(); ++iter) {
        ContactPresenceListener *listener = *iter;
        if (present)
            listener->contactAdded(c);
        else
            listener->contactRemoved(c);
    }
}

void ContactManager::queueContactPresenceChange(const ContactUID *c, bool present)
{
    thread::Lock lock(eventQueueMutex);

    eventQueue.push_front(std::pair<InternalContactUID *, bool>(new InternalContactUID(c), present));
    eventQueueMonitor.broadcast(lock);
}

void ContactManager::queueThread()
{
    while (!threadShouldStop(EVENT_THREADID)) {
        std::pair<InternalContactUID *, bool> event;
        {
            thread::Lock lock(eventQueueMutex);
            if (eventQueue.empty()) {
                eventQueueMonitor.wait(lock);
                continue;
            }

            event = eventQueue.back();
            eventQueue.pop_back();
        }
        fireContactPresenceChange(event.first, event.second);
        delete event.first;
    }
}


/**********************************************************************/
// Contact access


void ContactManager::setPreferStreamEndpoint(bool preferStream)
{
    this->preferStreaming = preferStream;
}

const ContactList *ContactManager::getAllContacts()
{
    thread::ReadLock lock(contactMapMutex);
    size_t n = contacts.size();
    const ContactUID **extContacts = new const ContactUID*[n];
    ContactMap::iterator iter;
    int i;
    for (i = 0, iter = contacts.begin(); iter != contacts.end(); i++, iter++)
        extContacts[i] = new InternalContactUID(iter->first);
    return new ContactList(n, extContacts);
}

void ContactManager::freeContactList(const ContactList *list)
{
    for (size_t i = 0; i < list->nContacts; ++i)
        delete (InternalContactUID *)(list->contacts[i]);
    delete[] list->contacts;
    delete list;
}


CommoResult ContactManager::configKnownEndpointContact(
                                       const ContactUID *contact,
                                       const char *callsign,
                                       const char *ipAddr,
                                       int destPort)
{
    if (((ipAddr == NULL) ^ (callsign == NULL)) || destPort < 0)
        return COMMO_ILLEGAL_ARGUMENT;

    if (!ipAddr && !callsign) {
        // Delete this "known" contact
        thread::WriteLock lock(contactMapMutex);
        ContactMap::iterator iter = contacts.find(contact);
        if (iter == contacts.end())
            return COMMO_ILLEGAL_ARGUMENT;

        ContactState *state = iter->second;
        if (!state->knownEndpoint)
            return COMMO_ILLEGAL_ARGUMENT;

        contacts.erase(iter);
        delete state;
        return COMMO_SUCCESS;
        
    } else if (strlen(ipAddr) == 0 || strlen(callsign) == 0) {
        return COMMO_ILLEGAL_ARGUMENT;
    } else {
        std::string endpoint(ipAddr);
        return processContactUpdate(false, 0, contact, NULL,
                                    true, CoTEndpoint::DATAGRAM, endpoint,
                                    callsign, destPort);

    }
}



/**********************************************************************/
// Contact endpoint info

std::string ContactManager::getActiveEndpointHost(const ContactUID *contact)
{
    thread::ReadLock lock(contactMapMutex);
    ContactMap::iterator iter = contacts.find(contact);
    if (iter == contacts.end())
        return "";

    {
        thread::Lock lock2(iter->second->mutex);
        CoTEndpoint *ep = getCurrentEndpoint(iter->second);
        if (ep->getType() == CoTEndpoint::STREAMING)
            return "";

        if (ep->getType() == CoTEndpoint::TCP)
            return ((TcpCoTEndpoint *)ep)->getHostString();

        DatagramCoTEndpoint *dge = (DatagramCoTEndpoint *)ep;
        std::string s;
        dge->getBaseAddr()->getIPString(&s);
        return s;
    }
}

bool ContactManager::hasContact(const ContactUID *contact)
{
    thread::ReadLock lock(contactMapMutex);
    ContactMap::iterator iter = contacts.find(contact);
    return iter != contacts.end();
}

bool ContactManager::hasStreamingEndpoint(const ContactUID *contact)
{
    thread::ReadLock lock(contactMapMutex);
    ContactMap::iterator iter = contacts.find(contact);
    if (iter == contacts.end())
        return false;

    {
        thread::Lock lock2(iter->second->mutex);
        return iter->second->streamEndpoint != NULL;
    }
}

std::string ContactManager::getStreamEndpointIdentifier(const ContactUID *contact, bool ifActive) COMMO_THROW (std::invalid_argument)
{
    thread::ReadLock lock(contactMapMutex);
    ContactMap::iterator iter = contacts.find(contact);
    if (iter == contacts.end())
        throw std::invalid_argument("Specified contact is not known");

    {
        thread::Lock lock2(iter->second->mutex);
        if (iter->second->streamEndpoint == NULL)
            throw std::invalid_argument("Specified contact has no streaming endpoint");
        else if (!ifActive || getCurrentEndpoint(iter->second) == iter->second->streamEndpoint)
            return iter->second->streamEndpoint->getEndpointString();
        else
            throw std::invalid_argument("Specified contact has no active streaming endpoint");
    }
}


/**********************************************************************/
// Private: Endpoint management

int ContactManager::getSendProtoVersion(ContactState *state)
{
    if (state->protoInf.second < TakProtoInfo::SELF_MIN ||
         state->protoInf.first > TakProtoInfo::SELF_MAX)
        // No overlap - revert to legacy
        return 0;
    return state->protoInf.second < TakProtoInfo::SELF_MAX ? 
        state->protoInf.second : TakProtoInfo::SELF_MAX;
}

// NOTE: assumes holding of the mutex for the contact
CoTEndpoint* ContactManager::getCurrentEndpoint(ContactState *state, CoTSendMethod sendMethod)
{
    // preferStreaming == false (and legacy before this option):
    // Priority given to tcp or datagram connection over stream if known
    // AND last seen via tcp or datagram time is less than some time ago
    //
    // preferStreaming == true
    // Priority given to stream endpoint if known, else fall back to non-stream
    CoTEndpoint *ret = NULL;
    CoTEndpoint *meshEp = NULL;
    CoTEndpoint *streamEp = NULL;
    
    if ((sendMethod & SEND_TAK_SERVER) != 0)
        streamEp = state->streamEndpoint;
    
    if ((sendMethod & SEND_POINT_TO_POINT) &&
                (state->tcpEndpoint || state->datagramEndpoint)) {
        CommoTime nearestTime = CommoTime::ZERO_TIME;
        if (state->tcpEndpoint) {
            nearestTime = state->tcpEndpoint->getLastRxTime();
            meshEp = state->tcpEndpoint;
        }
        if (state->datagramEndpoint && (!meshEp || 
                    nearestTime < state->datagramEndpoint->getLastRxTime())) {
            nearestTime = state->datagramEndpoint->getLastRxTime();
            meshEp = state->datagramEndpoint;
        }
        if (meshEp && streamEp &&
                    CommoTime::now().minus(nearestTime) > CONTACT_DIRECT_TIMEOUT)
            // Force fallback to streaming ep below, if exists
            meshEp = NULL;
    }
    
    if (meshEp && streamEp) {
        ret = preferStreaming ? streamEp : meshEp;
    } else {
        ret = meshEp ? meshEp : streamEp;
    }

    return ret;
}


// NOTE: assumes holding of the mutex for the contact
bool ContactManager::updateStateDatagramEndpoint(ContactState *state,
        const std::string &ep, int port)
{
    try {
        if (state->knownEndpoint) {
            if (!state->datagramEndpoint)
                state->datagramEndpoint = new DatagramCoTEndpoint(ep, port);
            else
                state->datagramEndpoint->updateKnownEndpoint(ep, port);
        } else {
            if (!state->datagramEndpoint)
                state->datagramEndpoint = new DatagramCoTEndpoint(ep);
            else
                state->datagramEndpoint->updateFromCoTEndpoint(ep);
            state->meshEndpointExpireTime = 
                state->datagramEndpoint->getLastRxTime() +
                (float)TakMessage::PROTOINF_TIMEOUT_SEC;
        }
        return true;
    } catch (std::invalid_argument &) {
        return false;
    }
}

// NOTE: assumes holding of the mutex for the contact
bool ContactManager::updateStateTcpEndpoint(ContactState *state,
        const std::string &ep, int port)
{
    try {
        if (!state->tcpEndpoint)
            state->tcpEndpoint = new TcpCoTEndpoint(ep, port);
        else
            state->tcpEndpoint->updateFromCoTEndpoint(ep, port);
        state->meshEndpointExpireTime = 
            state->tcpEndpoint->getLastRxTime() +
            (float)TakMessage::PROTOINF_TIMEOUT_SEC;
        return true;
    } catch (std::invalid_argument &) {
        return false;
    }
}

// NOTE: assumes holding of the mutex for the contact
bool ContactManager::updateStateStreamEndpoint(ContactState *state,
        const std::string &ep)
{
    try {
        if (!state->streamEndpoint)
            state->streamEndpoint = new StreamingCoTEndpoint(ep);
        else
            state->streamEndpoint->updateFromStreamingEndpoint(ep);
        return true;
    } catch (std::invalid_argument &) {
        return false;
    }
}


/**********************************************************************/
// CotEndpoint and subclasses

CoTEndpoint::CoTEndpoint(InterfaceType type) : type(type),
        lastRxTime(CommoTime::now())
{
}

CoTEndpoint::~CoTEndpoint()
{
}

CoTEndpoint::InterfaceType CoTEndpoint::getType() const
{
    return type;
}

CommoTime CoTEndpoint::getLastRxTime() const
{
    return lastRxTime;
}

void CoTEndpoint::touchRxTime()
{
    lastRxTime = CommoTime::now();
}

DatagramCoTEndpoint::DatagramCoTEndpoint(const std::string& endpointString)
            COMMO_THROW (std::invalid_argument) :
                CoTEndpoint(DATAGRAM),
                baseAddr(NULL),
                chatAddr(NULL),
                saAddr(NULL),
                cachedEndpointString(""),
                isKnown(false),
                port(0)
{
    updateFromCoTEndpoint(endpointString);
}

DatagramCoTEndpoint::DatagramCoTEndpoint(const std::string& ipString, int port)
            COMMO_THROW (std::invalid_argument) :
                CoTEndpoint(DATAGRAM),
                baseAddr(NULL),
                chatAddr(NULL),
                saAddr(NULL),
                cachedEndpointString(""),
                isKnown(true),
                port(port)
{
    updateKnownEndpoint(ipString, port);
}

DatagramCoTEndpoint::~DatagramCoTEndpoint()
{
    delete baseAddr;
    delete saAddr;
    delete chatAddr;
}

const NetAddress* DatagramCoTEndpoint::getBaseAddr() const
{
    return baseAddr;
}

const NetAddress* DatagramCoTEndpoint::getNetAddr(CoTMessageType type) const
{
    NetAddress *ret;
    switch (type) {
    case CHAT:
        ret = chatAddr;
        break;
    case SITUATIONAL_AWARENESS:
    default:
        ret = saAddr;
        break;
    }
    return ret;
}

void DatagramCoTEndpoint::updateFromCoTEndpoint(
        const std::string& endpointString)
                COMMO_THROW (std::invalid_argument)
{
    if (isKnown)
        throw std::invalid_argument("Cannot update endpoint of known contact");
    if (endpointString.empty())
        throw std::invalid_argument("Endpoint cannot be empty");
    if (endpointString.compare(cachedEndpointString) != 0) {
        NetAddress *addr = NetAddress::create(endpointString.c_str());
        if (!addr)
            throw std::invalid_argument("Cannot parse endpoint string");

        cachedEndpointString = endpointString;
        delete this->baseAddr;
        this->baseAddr = addr;
        delete this->saAddr;
        this->saAddr = baseAddr->deriveNewAddress(6969);
        delete this->chatAddr;
        this->chatAddr = baseAddr->deriveNewAddress(17012);
    }
    touchRxTime();
}

void DatagramCoTEndpoint::updateKnownEndpoint(const std::string &ipString,
                                              int port)
                                              COMMO_THROW (std::invalid_argument)
{
    if (!isKnown)
        throw std::invalid_argument("Cannot update endpoint of discovered contact");

    if (ipString.empty())
        throw std::invalid_argument("IP string cannot be empty");

    NetAddress *addr = NetAddress::create(ipString.c_str());
    if (!addr)
        throw std::invalid_argument("Cannot parse endpoint string");

    cachedEndpointString = ipString;
    delete this->baseAddr;
    this->baseAddr = addr;
    delete this->saAddr;
    this->saAddr = baseAddr->deriveNewAddress(port);
    delete this->chatAddr;
    this->chatAddr = baseAddr->deriveNewAddress(port);
}

std::string DatagramCoTEndpoint::getEndpointString()
{
    return cachedEndpointString;
}

TcpCoTEndpoint::TcpCoTEndpoint(const std::string& ipString, int port)
            COMMO_THROW (std::invalid_argument) :
                CoTEndpoint(TCP),
                cachedEndpointString(""),
                port(0)
{
    updateFromCoTEndpoint(ipString, port);
}

TcpCoTEndpoint::~TcpCoTEndpoint()
{
}

const std::string TcpCoTEndpoint::getHostString() const
{
    return cachedEndpointString;
}

const int TcpCoTEndpoint::getPortNum(CoTMessageType type) const
{
    int ret;
    //switch (type) {
    //case CHAT:
        //ret = 4242;
        //break;
    //case SITUATIONAL_AWARENESS:
    //default:
        ret = port;
    //    break;
    //}
    return ret;
}

void TcpCoTEndpoint::updateFromCoTEndpoint(
        const std::string& endpointString, int port)
                COMMO_THROW (std::invalid_argument)
{
    if (endpointString.empty())
        throw std::invalid_argument("Endpoint cannot be empty");
    if (port <= 0)
        throw std::invalid_argument("Endpoint port cannot be <= 0");
    if (endpointString.compare(cachedEndpointString) != 0 || this->port != port) {
        cachedEndpointString = endpointString;
        this->port = port;
    }
    touchRxTime();
}

StreamingCoTEndpoint::StreamingCoTEndpoint(const std::string &ep) :
        CoTEndpoint(CoTEndpoint::STREAMING), endpointString(ep)
{
}

StreamingCoTEndpoint::~StreamingCoTEndpoint()
{
}

void StreamingCoTEndpoint::updateFromStreamingEndpoint(
        const std::string& sourceEndpoint)
{
    if (sourceEndpoint.empty())
        throw std::invalid_argument("Endpoint cannot be empty");
    endpointString = sourceEndpoint;
    touchRxTime();
}

std::string StreamingCoTEndpoint::getEndpointString()
{
    return endpointString;
}



/**********************************************************************/
// ContactState

ContactState::ContactState(bool knownEP) :
        protoInfValid(false),
        protoInfExpireTime(CommoTime::ZERO_TIME),
        protoInf(0, 0),
        meshEndpointExpireTime(CommoTime::ZERO_TIME),
        lastMeshEndpointProto(0),
        streamEndpoint(NULL),
        datagramEndpoint(NULL), tcpEndpoint(NULL),
        callsign(), knownEndpoint(knownEP), mutex() {
}

ContactState::~ContactState() {
    delete streamEndpoint;
    delete datagramEndpoint;
    delete tcpEndpoint;
}
