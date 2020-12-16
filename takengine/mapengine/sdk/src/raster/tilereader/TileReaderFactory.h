
#ifndef ATAKMAP_RASTER_TILEREADER_TILEREADERFACTORY_H_INCLUDED
#define ATAKMAP_RASTER_TILEREADER_TILEREADERFACTORY_H_INCLUDED

#include <map>
#include <memory>

#include "port/String.h"
#include "thread/Mutex.h"

#include "raster/tilereader/TileReader.h"

namespace atakmap {
    namespace raster {
        namespace tilereader {
            
            class TileReaderSpi;
            
            class TileReaderFactory {
            public:
                struct Options {
                    TAK::Engine::Port::String preferredSpi;
                    int preferredTileWidth;
                    int preferredTileHeight;
                    TAK::Engine::Port::String cacheUri;
                    std::shared_ptr<TileReader::AsynchronousIO> asyncIO;
                };
                
            public:
                static TileReader *create(const char *uri, const Options *options);
                static void registerSpi(TileReaderSpi *spi);
                static void unregisterSpi(TileReaderSpi *spi);
                
            private:
                typedef std::map<TAK::Engine::Port::String, TileReaderSpi *, TAK::Engine::Port::StringLess> ProviderMap;
                static ProviderMap spis;
                static TAK::Engine::Thread::Mutex mutex;
            };
            
            class TileReaderSpi {
            public:
                virtual ~TileReaderSpi();
                virtual const char *getName() const = 0;
                virtual TileReader *create(const char *uri, const TileReaderFactory::Options *options) = 0;
            };
        }
    }
}


#endif