#ifndef TAK_ENGINE_CURRENCY_CURRENCY2_H_INCLUDED
#define TAK_ENGINE_CURRENCY_CURRENCY2_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Currency {
            class CatalogCurrency2
            {
            public :
                struct AppData;
                typedef std::unique_ptr<AppData, void(*)(const AppData *)> AppDataPtr;
            protected :
                virtual ~CatalogCurrency2() NOTHROWS = 0;
            public :
                virtual const char *getName() const NOTHROWS = 0;
                virtual int getAppVersion() const NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr getAppData(AppDataPtr &data, const char *file) const NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr isValidApp(bool *value, const char *f, const int appVersion, const AppData &data) const NOTHROWS = 0;
            };

            struct CatalogCurrency2::AppData
            {
                const uint8_t *value;
                const std::size_t length;
            };
        }
    }
}

#endif
