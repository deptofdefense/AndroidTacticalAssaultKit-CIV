#ifndef IMPL_HWIFSCANNER_H_
#define IMPL_HWIFSCANNER_H_

#include "internalutils.h"
#include "netinterface.h"
#include "netsocket.h"
#include <Cond.h>
#include "commologger.h"
#include "threadedhandler.h"
#include "platform.h"
#include <set>
#include <map>
#include <stdexcept>

namespace atakmap {
namespace commoncommo {
namespace impl
{


class HWIFScannerListener
{
public:
    // Parameters must be copied during event servicing if used after return
    // These will never be invoked simultaneously; dispatched serially.
    virtual void interfaceUp(const HwAddress *addr, const NetAddress *netAddr) = 0;
    virtual void interfaceDown(const HwAddress *addr) = 0;

protected:
    HWIFScannerListener() {};
    virtual ~HWIFScannerListener() {};

private:
    COMMO_DISALLOW_COPY(HWIFScannerListener);
};



class HWIFScanner : public ThreadedHandler
{

public:
    // Throws if unable to open hardware scanning resources
    HWIFScanner(CommoLogger *logger,
                netinterfaceenums::NetInterfaceAddressMode addrMode) COMMO_THROW (SocketException);
    virtual ~HWIFScanner();

    void addHWAddress(const HwAddress *addr);
    void removeHWAddress(const HwAddress *addr);

    // Throws if 'addr' is not a registered HwAddr
    bool isInterfaceUp(const HwAddress *addr) COMMO_THROW (std::invalid_argument);
    // NULL if interface not up
    // Returns a copy that must be delete'd by caller
    // Throws if 'addr' is not a registered HwAddr
    NetAddress *getNetAddress(const HwAddress *addr) COMMO_THROW (std::invalid_argument);
    // empty string if no interface is up
    // Otherwise gives a local address string that is "best suited" for others
    // to interace with this system.
    std::string getPrimaryAddressString();

    void addListener(HWIFScannerListener *listener);
    void removeListener(HWIFScannerListener *listener);
    
    netinterfaceenums::NetInterfaceAddressMode getAddressMode();


protected:
    virtual void threadEntry(size_t threadNum);
    virtual void threadStopSignal(size_t threadNum);

private:
    COMMO_DISALLOW_COPY(HWIFScanner);

    CommoLogger *logger;
    const netinterfaceenums::NetInterfaceAddressMode addrMode;
    InterfaceEnumerator ifEnum;
    std::set<HWIFScannerListener *> listeners;
    std::map<const HwAddress *, NetAddress *, HwComp> interfaceStateMap;
    PGSC::Thread::Mutex interfaceStateMutex;
    PGSC::Thread::CondVar interfaceStateMonitor;
    PGSC::Thread::Mutex listenerMutex;



};

}
}
}



#endif /* IMPL_HWIFSCANNER_H_ */
