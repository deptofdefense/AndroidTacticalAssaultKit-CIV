#include "hwifscanner.h"
#include "internalutils.h"
#include "commothread.h"
#include "commotime.h"

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::netinterfaceenums;
using namespace atakmap::commoncommo::impl;
using namespace atakmap::commoncommo::impl::thread;

namespace {
    const char *THREAD_NAMES[] = {
        "cmohwscan"
    };
}

HWIFScanner::HWIFScanner(CommoLogger *logger, NetInterfaceAddressMode addrMode) COMMO_THROW (SocketException) :
        ThreadedHandler(1, THREAD_NAMES),
        logger(logger), addrMode(addrMode),
        ifEnum(logger, addrMode), listeners(),
        interfaceStateMap(), interfaceStateMutex(),
        interfaceStateMonitor(),
        listenerMutex()
{
    startThreads();
}

HWIFScanner::~HWIFScanner()
{
    stopThreads();
    {
        Lock lock(interfaceStateMutex);

        std::map<const HwAddress *, NetAddress *, HwComp>::iterator iter;
        for (iter = interfaceStateMap.begin(); iter != interfaceStateMap.end(); ++iter) {
            delete iter->second;
            delete (InternalHwAddress *)iter->first;
        }
    }
}

void HWIFScanner::addHWAddress(
        const HwAddress* addr)
{
    Lock lock(interfaceStateMutex);
    if (interfaceStateMap.find(addr) == interfaceStateMap.end()) {
        interfaceStateMap.insert(std::pair<HwAddress *, NetAddress *>(new InternalHwAddress(addr), NULL));
        // Wake so it can scan for it immediately
        interfaceStateMonitor.broadcast(lock);
        std::string hwStr = InternalUtils::hwAddrAsString(addr, addrMode);
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Hw interface %s added for scanning", hwStr.c_str());
    }
}

void HWIFScanner::removeHWAddress(
        const HwAddress* addr)
{
    Lock lock(interfaceStateMutex);
    std::map<const HwAddress *, NetAddress *, HwComp>::iterator iter;
    iter = interfaceStateMap.find(addr);
    if (iter != interfaceStateMap.end()) {
        delete (InternalHwAddress *)iter->first;
        delete iter->second;
        interfaceStateMap.erase(iter);
    }
}

bool HWIFScanner::isInterfaceUp(
        const HwAddress* addr) COMMO_THROW (std::invalid_argument)
{
    Lock lock(interfaceStateMutex);
    std::map<const HwAddress *, NetAddress *, HwComp>::iterator iter = interfaceStateMap.find(addr);
    if (iter == interfaceStateMap.end())
        throw std::invalid_argument("Checking status for unknown interface");
    return iter->second != NULL;
}

NetAddress* HWIFScanner::getNetAddress(
        const HwAddress* addr) COMMO_THROW (std::invalid_argument)
{
    Lock lock(interfaceStateMutex);
    std::map<const HwAddress *, NetAddress *, HwComp>::iterator iter = interfaceStateMap.find(addr);
    if (iter == interfaceStateMap.end())
        throw std::invalid_argument("Checking status for unknown interface");
    if (iter->second == NULL)
        return NULL;
    return NetAddress::duplicateAddress(iter->second);
}

std::string HWIFScanner::getPrimaryAddressString()
{
    Lock lock(interfaceStateMutex);
    std::map<const HwAddress *, NetAddress *, HwComp>::iterator iter;
    NetAddress *addr = NULL;
    for (iter = interfaceStateMap.begin(); iter != interfaceStateMap.end(); ++iter) {
        if (iter->second) {
            addr = iter->second;
            break;
        }
    }

    std::string ret;
    if (addr)
        addr->getIPString(&ret);
    return ret;
}

void HWIFScanner::addListener(
        HWIFScannerListener* listener)
{
    Lock lock(listenerMutex);
    listeners.insert(listener);
}

void HWIFScanner::removeListener(
        HWIFScannerListener* listener)
{
    if (listener == NULL)
        return;

    Lock lock(listenerMutex);
    listeners.erase(listener);
}

NetInterfaceAddressMode HWIFScanner::getAddressMode()
{
    return addrMode;
}

void HWIFScanner::threadStopSignal(size_t threadNum)
{
    Lock lock(interfaceStateMutex);
    interfaceStateMonitor.broadcast(lock);
}

void HWIFScanner::threadEntry(size_t threadNum)
{
    // Only one thread for this class so ignore threadNum

    while (!threadShouldStop(threadNum)) {
        ifEnum.rescanInterfaces();
        const std::map<const HwAddress *, const NetAddress *, HwComp> *ifAddrMap = ifEnum.getInterfaces();

        std::vector<std::pair<InternalHwAddress *, NetAddress *> > fireUp;
        std::vector<InternalHwAddress *> fireDown;
        {
            Lock lock(interfaceStateMutex);

            std::map<const HwAddress *, NetAddress *, HwComp>::iterator iter;
            for (iter = interfaceStateMap.begin(); iter != interfaceStateMap.end(); ++iter) {
                std::map<const HwAddress *, const NetAddress *, HwComp>::const_iterator ifAddrIter;
                std::string hwStr = InternalUtils::hwAddrAsString(iter->first, addrMode);

                ifAddrIter = ifAddrMap->find(iter->first);
                const NetAddress *ifCurAddr = NULL;
                if (ifAddrIter != ifAddrMap->end())
                    ifCurAddr = ifAddrIter->second;

                if (ifCurAddr == NULL && iter->second != NULL) {
                    // Interface changed to down
                    delete iter->second;
                    iter->second = NULL;
                    fireDown.push_back(new InternalHwAddress(iter->first));

                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Hw interface %s: DOWN", hwStr.c_str());

                } else if (ifCurAddr != NULL && iter->second == NULL) {
                    // Interface changed to up.
                    NetAddress *ipaddr = NetAddress::duplicateAddress(ifCurAddr);

                    std::string ipStr;
                    ifCurAddr->getIPString(&ipStr);
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Hw interface %s: UP %s", hwStr.c_str(), ipStr.c_str());

                    iter->second = ipaddr;
                    fireUp.push_back(std::pair<InternalHwAddress *, NetAddress *>(new InternalHwAddress(iter->first), NetAddress::duplicateAddress(ipaddr)));
                } else if (ifCurAddr != NULL && iter->second != NULL && !ifCurAddr->isSameAddress(iter->second)) {
                    // Changed address, still up
                    // Update internal address copy
                    delete iter->second;
                    iter->second = NetAddress::duplicateAddress(ifCurAddr);

                    std::string ipStr;
                    ifCurAddr->getIPString(&ipStr);
                    InternalUtils::logprintf(logger, CommoLogger::LEVEL_INFO, "Hw interface %s: ADDR CHANGE %s", hwStr.c_str(), ipStr.c_str());

                    // and push out a down and an up message.
                    fireDown.push_back(new InternalHwAddress(iter->first));
                    fireUp.push_back(std::pair<InternalHwAddress *, NetAddress *>(new InternalHwAddress(iter->first), NetAddress::duplicateAddress(iter->second)));

                }
            }
        }

        // Fire events
        if (!fireDown.empty() || !fireUp.empty()) {
            Lock lock(listenerMutex);
            std::set<HWIFScannerListener *>::iterator listenIter;
            for (listenIter = listeners.begin(); listenIter != listeners.end(); ++listenIter) {
                HWIFScannerListener *l = *listenIter;
                // MUST send down messages out before up due to "address changed" code above.
                for (size_t i = 0; i < fireDown.size(); ++i) {
                    l->interfaceDown(fireDown[i]);
                    delete fireDown[i];
                }
                for (size_t i = 0; i < fireUp.size(); ++i) {
                    l->interfaceUp(fireUp[i].first, fireUp[i].second);
                    delete fireUp[i].first;
                    delete fireUp[i].second;
                }
            }
        }

        {
            Lock lock(interfaceStateMutex);
            int64_t millis = 10 * 1000;
            interfaceStateMonitor.wait(lock, millis);
        }
    }

}
