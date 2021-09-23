#ifndef TAK_ENGINE_RENDERER_ICON_CACHE_DB_URI_LOADER2_SPI_H_INCLUDED
#define TAK_ENGINE_RENDERER_ICON_CACHE_DB_URI_LOADER2_SPI_H_INCLUDED

#include "util/URILoader2.h"
#include "db/SpatiaLiteDb.h"
#include "port/String.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            
            class IconCacheDbURILoader2Spi : public TAK::Engine::Util::URILoader2Spi {
            public:
                IconCacheDbURILoader2Spi(const char *fileName) NOTHROWS;
                virtual ~IconCacheDbURILoader2Spi() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr openURI(TAK::Engine::Util::DataInput2Ptr &input, const char *uri, const TAK::Engine::Util::URILoader2Args *optionalArgs) NOTHROWS;
                
            private:
                mutable TAK::Engine::Thread::Mutex mutex;
                
                TAK::Engine::Port::String iconCacheFile;
                
                mutable std::unique_ptr<atakmap::db::SpatiaLiteDB> iconCacheDb;
            };
            
        }
    }
}

#endif