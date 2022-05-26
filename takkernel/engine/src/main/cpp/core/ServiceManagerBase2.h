#ifndef TAK_ENGINE_CORE_SERVICEMANAGERBASE_H_INCLUDED
#define TAK_ENGINE_CORE_SERVICEMANAGERBASE_H_INCLUDED

#include <map>
#include <memory>

#include "core/Service.h"
#include "port/Platform.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Core {
            class ServiceManagerBase2
            {
            protected:
                ServiceManagerBase2(TAK::Engine::Thread::Mutex& mutex) NOTHROWS;
            public :
                Util::TAKErr getService(std::shared_ptr<atakmap::core::Service> &value, const char* serviceType) const NOTHROWS;
                Util::TAKErr registerService(std::shared_ptr<atakmap::core::Service> svc) NOTHROWS;
                Util::TAKErr unregisterService(std::shared_ptr<atakmap::core::Service> svc) NOTHROWS;
            private:
                TAK::Engine::Thread::Mutex& mutex;
                std::map<std::string, std::shared_ptr<atakmap::core::Service>> services;
            };
        }
    }
}

#endif 
