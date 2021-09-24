#ifndef TAK_ENGINE_RASTER_TILECLIENTFACTORY_H_INCLUDED
#define TAK_ENGINE_RASTER_TILECLIENTFACTORY_H_INCLUDED

#include "raster/tilematrix/TileClient.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {

                class ENGINE_API TileClientSpi {
                public:
                    struct ENGINE_API Options {
                        int64_t dnsLookupTimeout;
                        int64_t connectTimeout;

                        Options() NOTHROWS;

                        Options(int64_t dnsLookupTimeout, int64_t connectTimeout) NOTHROWS;

                        ~Options() NOTHROWS;
                    };
                
                    virtual ~TileClientSpi() NOTHROWS;

                    virtual const char *getName() const NOTHROWS = 0;
                    virtual Util::TAKErr create(TileClientPtr &result, const char *path, const char *offlineCachePath, const Options *opts) const NOTHROWS = 0;
                    virtual int getPriority() const NOTHROWS = 0;
                };
                typedef std::unique_ptr<TileClientSpi, void (*)(const TileClientSpi *)> TileClientSpiPtr;

                ENGINE_API Util::TAKErr TileClientFactory_registerSpi(const std::shared_ptr<TileClientSpi> &spi) NOTHROWS;
                ENGINE_API Util::TAKErr TileClientFactory_unregisterSpi(const TileClientSpi *spi) NOTHROWS;
                ENGINE_API Util::TAKErr TileClientFactory_create(TileClientPtr &result, const char *path, const char *offlineCache, const TileClientSpi::Options *opts) NOTHROWS;
            
            }
        }
    }
}

#endif
