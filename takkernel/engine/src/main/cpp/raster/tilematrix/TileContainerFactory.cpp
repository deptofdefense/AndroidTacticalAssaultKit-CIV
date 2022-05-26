
#include "raster/tilematrix/TileContainerFactory.h"
#include "util/CopyOnWrite.h"
#include "util/IO2.h"
#include "port/STLVectorAdapter.h"
#include <vector>
#include <map>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster::TileMatrix;

//
// TileContainer
//

TileContainer::~TileContainer() NOTHROWS
{}

//
// TileContainerSpi
//

TileContainerSpi::~TileContainerSpi() NOTHROWS
{}

//
// TileContainerFactory
//

namespace {
    class TileContainerSpiRegistry {
    public:
        typedef std::vector<std::shared_ptr<TileContainerSpi>> SpiVector;
        TAKErr registerSpi(const std::shared_ptr<TileContainerSpi> &spi) NOTHROWS;
        TAKErr unregisterSpi(const TileContainerSpi *spi) NOTHROWS;
        std::pair<SpiVector::const_iterator, SpiVector::const_iterator> iterate() const NOTHROWS;

    private:
        SpiVector spis;
    };

    CopyOnWrite<TileContainerSpiRegistry> &getGlobalTileContainerSpiRegistry();
}

TAKErr TAK::Engine::Raster::TileMatrix::TileContainerFactory_openOrCreateCompatibleContainer(TileContainerPtr& result, const char* path,
                                                                                             const TileMatrix* spec,
                                                                                             const char* hint) NOTHROWS
{

    if (spec == nullptr)
        return TE_InvalidArg;

    bool exists = false;
    TAKErr code = IO_exists(&exists, path);
    if (code != TE_Ok)
        return code;

    code = TE_Unsupported;
    bool create = !exists;
    auto iters = getGlobalTileContainerSpiRegistry().read()->iterate();
    while (iters.first != iters.second) {
        std::shared_ptr<TileContainerSpi> spi = *iters.first;
        if (hint != nullptr) {
            int comp = -1;
            code = Port::String_compareIgnoreCase(&comp, spi->getName(), hint);
            TE_CHECKRETURN_CODE(code);
            if (comp != 0)
                continue;
        }

        bool compat = false;
        TAKErr compatCode = spi->isCompatible(&compat, spec);
        TE_CHECKRETURN_CODE(compatCode);

        if (compat) {
            if (create)
                code = spi->create(result, spec->getName(), path, spec);
            else
                code = spi->open(result, path, spec, false);
            break;
        }
        ++iters.first;
    }

    return code;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileContainerFactory_open(TileContainerPtr& result, const char* path, bool readOnly,
                                                                  const char* hint) NOTHROWS
{

    if (path == nullptr)
        return TE_InvalidArg;

    TAKErr code = TE_Unsupported;
    auto iters = getGlobalTileContainerSpiRegistry().read()->iterate();
    while (iters.first != iters.second) {
        std::shared_ptr<TileContainerSpi> spi = *iters.first;
        if (hint != nullptr) {
            int comp = -1;
            TAKErr compCode = Port::String_compareIgnoreCase(&comp, spi->getName(), hint);
            TE_CHECKRETURN_CODE(compCode);
            if (comp != 0) {
                ++iters.first;
                continue;
            }
        }

        code = spi->open(result, path, nullptr, readOnly);
        if (code != TE_Unsupported)
            break;
        ++iters.first;
    }

    return code;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileContainerFactory_registerSpi(const std::shared_ptr<TileContainerSpi>& spi) NOTHROWS
{
    return getGlobalTileContainerSpiRegistry().invokeWrite(&TileContainerSpiRegistry::registerSpi, spi);
}

TAKErr TAK::Engine::Raster::TileMatrix::TileContainerFactory_unregisterSpi(const TileContainerSpi* spi) NOTHROWS
{
    return getGlobalTileContainerSpiRegistry().invokeWrite(&TileContainerSpiRegistry::unregisterSpi, spi);
}

TAKErr TAK::Engine::Raster::TileMatrix::TileContainerFactory_visitSpis(Util::TAKErr (*visitor)(void* opaque, TileContainerSpi&),
                                                                       void* opaque) NOTHROWS
{

    if (!visitor)
        return TE_InvalidArg;

    // hold on to shared ptr so instances stay strong while visiting
    std::shared_ptr<const TileContainerSpiRegistry> registry = getGlobalTileContainerSpiRegistry().read();
    
    auto iters = registry->iterate();
    while (iters.first != iters.second) {
        TAKErr visitCode = visitor(opaque, **iters.first);
        if (visitCode == TE_Done)
            return visitCode;
        ++iters.first;
    }
    return TE_Ok;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileContainerFactory_visitCompatibleSpis(Util::TAKErr (*visitor)(void* opaque, TileContainerSpi&),
                                                                                 void* opaque, const TileMatrix* spec) NOTHROWS
{

    if (!visitor)
        return TE_InvalidArg;

    // hold on to shared ptr so instances stay strong while visiting
    std::shared_ptr<const TileContainerSpiRegistry> registry = getGlobalTileContainerSpiRegistry().read();

    auto iters = registry->iterate();
    while (iters.first != iters.second) {
        std::shared_ptr<TileContainerSpi> spi = *iters.first;
        bool compat = false;
        spi->isCompatible(&compat, spec);
        if (compat) {
            TAKErr visitCode = visitor(opaque, **iters.first);
            if (visitCode == TE_Done)
                return visitCode;
        }
        ++iters.first;
    }

    return TE_Ok;
}


//
// TileContainerSpiRegistry
//

namespace {
    TAKErr TileContainerSpiRegistry::registerSpi(const std::shared_ptr<TileContainerSpi>& spi) NOTHROWS {
        spis.push_back(spi);
        return TE_Ok;
    }

    TAKErr TileContainerSpiRegistry::unregisterSpi(const TileContainerSpi* spi) NOTHROWS {
        auto it = spis.begin();
        while (it != spis.end()) {
            if ((*it).get() == spi)
                it = spis.erase(it);
            else
                ++it;
        }
        return TE_Ok;
    }

    std::pair<TileContainerSpiRegistry::SpiVector::const_iterator, TileContainerSpiRegistry::SpiVector::const_iterator>
        TileContainerSpiRegistry::iterate() const NOTHROWS {
        return std::make_pair(spis.begin(), spis.end());
    }

    CopyOnWrite<TileContainerSpiRegistry>& getGlobalTileContainerSpiRegistry() {
        static CopyOnWrite<TileContainerSpiRegistry> inst;
        return inst;
    }
}