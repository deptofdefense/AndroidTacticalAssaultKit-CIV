#ifndef TAK_ENGINE_DB_CURRENCYREGISTRY2_H_INCLUDED
#define TAK_ENGINE_DB_CURRENCYREGISTRY2_H_INCLUDED

#include <map>

#include "port/Platform.h"
#include "port/String.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Currency {
            class CatalogCurrency2;

            class CatalogCurrencyRegistry2
            {
            public :
                CatalogCurrencyRegistry2();
            public :
                void registerCurrency(CatalogCurrency2 *instance) NOTHROWS;
                void deregisterCurrency(CatalogCurrency2 *instance) NOTHROWS;
                void deregisterCurrency(const char *name) NOTHROWS;
                CatalogCurrency2 *getCurrency(const char *name) NOTHROWS;
            private :
                std::map<Port::String, CatalogCurrency2 *, Port::StringLess> registeredInstances;
                TAK::Engine::Thread::Mutex mutex;
            };
        }
    }
}

#endif // TAK_ENGINE_DB_CURRENCYREGISTRY2_H_INCLUDED
