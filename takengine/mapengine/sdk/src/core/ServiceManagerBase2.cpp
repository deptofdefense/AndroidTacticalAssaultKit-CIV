#include <string>

#include "core/ServiceManagerBase2.h"

#include "thread/Lock.h"

using namespace TAK::Engine::Core;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;

ServiceManagerBase2::ServiceManagerBase2(Mutex& mutex_) NOTHROWS :
    mutex(mutex_)
{}
            
TAKErr ServiceManagerBase2::getService(std::shared_ptr<Service> &value, const char* serviceType) const NOTHROWS
{
    if (!serviceType)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<std::string, std::shared_ptr<Service>>::const_iterator entry;
    entry = services.find(serviceType);
    if (entry == services.end())
        return TE_InvalidArg;
    value = entry->second;
    return TE_Ok;
}

TAKErr ServiceManagerBase2::registerService(std::shared_ptr<Service> svc) NOTHROWS
{
    if (!svc.get())
        return TE_InvalidArg;
    if (!svc->getType())
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    services[svc->getType()] = svc;
    return TE_Ok;
}

TAKErr ServiceManagerBase2::unregisterService(std::shared_ptr<Service> svc) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<std::string, std::shared_ptr<Service>>::iterator entry;
    for (entry = services.begin(); entry != services.end(); entry++)
    {
        if (entry->second.get() == svc.get()) {
            services.erase(entry);
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}
