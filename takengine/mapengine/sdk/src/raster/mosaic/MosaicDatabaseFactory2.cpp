#include "raster/mosaic/MosaicDatabaseFactory2.h"
#include "util/CopyOnWrite.h"
#include <map>
#include <string>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster::Mosaic;

namespace {
class MosaicDatabaseSpi2Registry
{
   public:
    TAKErr registerSpi(const std::shared_ptr<MosaicDatabaseSpi2> &spi) NOTHROWS;
    TAKErr unregisterSpi(const std::shared_ptr<MosaicDatabaseSpi2> &spi) NOTHROWS;
    TAKErr create(MosaicDatabase2Ptr &result, const char *provider) const NOTHROWS;
    TAKErr canCreate(const char *provider) const NOTHROWS;

   private:
    std::map<std::string, std::shared_ptr<MosaicDatabaseSpi2>> spis;
};

static CopyOnWrite<MosaicDatabaseSpi2Registry> &sharedMosaicDatabase2Registry();
}  // namespace




TAKErr TAK::Engine::Raster::Mosaic::MosaicDatabaseFactory2_register(const std::shared_ptr<MosaicDatabaseSpi2> &spi) NOTHROWS {
    return sharedMosaicDatabase2Registry().invokeWrite(&MosaicDatabaseSpi2Registry::registerSpi, spi);
}

TAKErr TAK::Engine::Raster::Mosaic::MosaicDatabaseFactory2_unregister(const std::shared_ptr<MosaicDatabaseSpi2> &spi) NOTHROWS {
    return sharedMosaicDatabase2Registry().invokeWrite(&MosaicDatabaseSpi2Registry::unregisterSpi, spi);
}

TAKErr TAK::Engine::Raster::Mosaic::MosaicDatabaseFactory2_create(MosaicDatabase2Ptr &db, const char *provider) NOTHROWS {
    return sharedMosaicDatabase2Registry().read()->create(db, provider);
}

TAKErr TAK::Engine::Raster::Mosaic::MosaicDatabaseFactory2_canCreate(const char *provider) NOTHROWS {
    return sharedMosaicDatabase2Registry().read()->canCreate(provider);
}


namespace {

    CopyOnWrite<MosaicDatabaseSpi2Registry> &sharedMosaicDatabase2Registry()
    {
        static CopyOnWrite<MosaicDatabaseSpi2Registry> registry;
        return registry;
    }

    TAKErr MosaicDatabaseSpi2Registry::registerSpi(const std::shared_ptr<MosaicDatabaseSpi2> &spiPtr) NOTHROWS
    {
        if (!spiPtr)
            return TE_InvalidArg;
        const char *provider = spiPtr->getName();
        if (!provider)
            return TE_InvalidArg;

        TAKErr code = TE_Ok;
        TE_BEGIN_TRAP()
        {
            spis.insert(std::make_pair(provider, spiPtr));
        }
        TE_END_TRAP(code);
        return code;
    }

    TAKErr MosaicDatabaseSpi2Registry::unregisterSpi(const std::shared_ptr<MosaicDatabaseSpi2> &spiPtr) NOTHROWS
    {
        if (!spiPtr)
            return TE_InvalidArg;
        const char *provider = spiPtr->getName();
        if (!provider)
            return TE_InvalidArg;

        auto iter = spis.find(provider);
        if (iter != spis.end() && iter->second == spiPtr)
            spis.erase(iter);

        return TE_Ok;
    }

    TAKErr MosaicDatabaseSpi2Registry::create(MosaicDatabase2Ptr &result, const char *provider) const NOTHROWS
    {
        auto iter = spis.find(provider);
        if (iter == spis.end()) {
            return TE_Unsupported;
        }
        return iter->second->createInstance(result);
    }

    TAKErr MosaicDatabaseSpi2Registry::canCreate(const char *provider) const NOTHROWS
    {
        auto iter = spis.find(provider);
        return (iter == spis.end()) ? TE_Unsupported : TE_Ok;
    }

}  // namespace