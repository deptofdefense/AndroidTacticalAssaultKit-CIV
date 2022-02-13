
#include "raster/tilematrix/TileClientFactory.h"
#include "util/CopyOnWrite.h"
#include <map>
#include <vector>
#include <functional>

using namespace TAK::Engine::Raster::TileMatrix;
using namespace TAK::Engine::Util;

TileClientSpi::Options::Options() NOTHROWS
    : dnsLookupTimeout(1),
    connectTimeout(3000) {}

TileClientSpi::Options::Options(int64_t dnsLookupTimeout, int64_t connectTimeout) NOTHROWS
    : dnsLookupTimeout(dnsLookupTimeout),
    connectTimeout(connectTimeout) {}

TileClientSpi::Options::~Options() NOTHROWS 
{}

TileClientSpi::~TileClientSpi() NOTHROWS
{}

//
//
//

namespace {
    class TileClientFactoryRegistry {
    public:
        TAKErr registerSpi(const std::shared_ptr<TileClientSpi> &spi) NOTHROWS;
        TAKErr unregisterSpi(const TileClientSpi *spi) NOTHROWS;
        std::multimap<int, std::shared_ptr<TileClientSpi>, std::greater<int>> priority_map;
    };

    CopyOnWrite<TileClientFactoryRegistry>& getGlobalTileClientFactoryRegistry();
}

//
// TileClientFactory
//

TAKErr TAK::Engine::Raster::TileMatrix::TileClientFactory_registerSpi(const std::shared_ptr<TileClientSpi>& spi) NOTHROWS
{
    return ::getGlobalTileClientFactoryRegistry().invokeWrite(&TileClientFactoryRegistry::registerSpi, spi);
}

TAKErr TAK::Engine::Raster::TileMatrix::TileClientFactory_unregisterSpi(const TileClientSpi* spi) NOTHROWS
{
    return ::getGlobalTileClientFactoryRegistry().invokeWrite(&TileClientFactoryRegistry::unregisterSpi, spi);
}

TAKErr TAK::Engine::Raster::TileMatrix::TileClientFactory_create(TileClientPtr& result, const char* path, const char* offlineCache,
                                                                 const TileClientSpi::Options* opts) NOTHROWS
{
    TAKErr code = TE_Unsupported;
    auto registry = getGlobalTileClientFactoryRegistry().read();
    for (auto& entry : registry->priority_map) {
        std::shared_ptr<TileClientSpi> spi = entry.second;
        code = spi->create(result, path, offlineCache, opts);
        if (code != TE_Unsupported)
            break;
    }
    return code;
}

//
// TileClientFactoryRegistry
//

namespace {
    
    TAKErr TileClientFactoryRegistry::registerSpi(const std::shared_ptr<TileClientSpi>& spi) NOTHROWS {
        if (!spi)
            return TE_InvalidArg;
        this->priority_map.insert(std::make_pair(spi->getPriority(), spi));
        return TE_Ok;
    }

    TAKErr TileClientFactoryRegistry::unregisterSpi(const TileClientSpi* spi) NOTHROWS {
        for (auto it = this->priority_map.begin(); it != this->priority_map.end();) {
            if (it->second.get() == spi)
                it = this->priority_map.erase(it);
            else
                ++it;
        }
        return TE_Ok;
    }

    CopyOnWrite<TileClientFactoryRegistry>& getGlobalTileClientFactoryRegistry() {
        static CopyOnWrite<TileClientFactoryRegistry> inst;
        return inst;
    }
}
