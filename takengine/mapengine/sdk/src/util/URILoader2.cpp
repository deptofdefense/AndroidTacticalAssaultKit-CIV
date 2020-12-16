
#include <list>

#include "thread/RWMutex.h"
#include "thread/Lock.h"

#include "util/URILoader2.h"

using namespace TAK::Engine::Util;

using namespace TAK::Engine::Thread;

namespace {
    struct RegistryEntry {
        std::shared_ptr<URILoader2Spi> spi;
        int priority;
    };
    
    static RWMutex registeryRWMutex;
    static std::list<RegistryEntry> registrySpis;
}


URILoader2Spi::~URILoader2Spi() NOTHROWS { }


TAK::Engine::Util::TAKErr TAK::Engine::Util::URILoader2_openURI(TAK::Engine::Util::DataInput2Ptr &input, const char *uri, const URILoader2Args *optionalArgs) {
    
    TAK::Engine::Util::TAKErr code = TAK::Engine::Util::TE_Unsupported;
    URILoader2Args defaultArgs;
    if (!optionalArgs) {
        optionalArgs = &defaultArgs;
    }
    
    ReadLockPtr lock(NULL, NULL);
    code = ReadLock_create(lock, registeryRWMutex);
    TE_CHECKRETURN_CODE(code);
    
    for (RegistryEntry &entry : registrySpis) {
        if ((code = entry.spi->openURI(input, uri, optionalArgs)) != TAK::Engine::Util::TE_Unsupported) {
            if (code == TAK::Engine::Util::TE_Ok || !optionalArgs->continueOnError) {
                break;
            }
        }
    }
    
    return code;
}

TAK::Engine::Util::TAKErr TAK::Engine::Util::URILoader2_registerSpi(const std::shared_ptr<URILoader2Spi> &spi, int priority) {
    TAKErr code(TE_Ok);
    WriteLockPtr lock(NULL, NULL);
    code = WriteLock_create(lock, registeryRWMutex);
    TE_CHECKRETURN_CODE(code);
    
    std::list<RegistryEntry>::iterator it = registrySpis.begin();
    while (it != registrySpis.end()) {
        if (it->priority > priority) {
            break;
        }
        ++it;
    }
    
    RegistryEntry entry { spi, priority };
    registrySpis.insert(it, entry);
    
    return TAK::Engine::Util::TE_Ok;
}

TAK::Engine::Util::TAKErr TAK::Engine::Util::URILoader2_unregisterSpi(const URILoader2Spi *spi) {

    TAKErr code(TE_Ok);
    WriteLockPtr lock(NULL, NULL);
    code = WriteLock_create(lock, registeryRWMutex);
    TE_CHECKRETURN_CODE(code);
    
    std::list<RegistryEntry>::iterator it = registrySpis.begin();
    while (it != registrySpis.end()) {
        if (it->spi.get() == spi) {
            registrySpis.erase(it);
            break;
        }
        ++it;
    }
    
    return TAK::Engine::Util::TE_Ok;
}
