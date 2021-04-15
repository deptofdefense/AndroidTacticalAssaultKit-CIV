#include "renderer/map/layer/GLLayerFactory.h"

#include "renderer/core/GLLayerFactory2.h"
#include "renderer/core/LegacyAdapters.h"
#include "renderer/map/layer/GLLayer.h"
#include "thread/RWMutex.h"
#include "util/Memory.h"

using namespace atakmap::renderer::map::layer;

using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer::map;

namespace
{
    struct SpiEntry
    {
        GLLayerSpi *spi;
        std::size_t insert;
        int priority;
    };

    struct SpiEntryComp
    {
        bool operator()(const SpiEntry &a, const SpiEntry &b) const
        {
            if (a.priority > b.priority)
                return true;
            else if (a.priority < b.priority)
                return false;
            else
                return (a.insert < b.insert);
        }
    };

    std::set<SpiEntry, SpiEntryComp> &spis()
    {
        static std::set<SpiEntry, SpiEntryComp> s;
        return s;
    }

    RWMutex &mutex()
    {
        static RWMutex m;
        return m;
    }

    std::size_t &inserts()
    {
        static std::size_t i;
        return i;
    }

    std::map<GLLayerSpi *, GLLayerSpi2 *> &legacyAdapters()
    {
        static std::map<GLLayerSpi *, GLLayerSpi2 *> m;
        return m;
    }
}


void GLLayerFactory::registerSpi(GLLayerSpi *spi, int priority)
{
    WriteLock lock(mutex());

    std::set<SpiEntry, SpiEntryComp> &registry = spis();

    SpiEntry entry;
    entry.spi = spi;
    entry.priority = priority;
    entry.insert = inserts()++;

    registry.insert(entry);

    std::map<GLLayerSpi *, GLLayerSpi2 *> &adapters = legacyAdapters();
    std::map<GLLayerSpi *, GLLayerSpi2 *>::iterator adapterEntry;
    adapterEntry = adapters.find(spi);
    if (adapterEntry == adapters.end()) {
        GLLayerSpi2Ptr spi2Ptr(nullptr, nullptr);
        if (LegacyAdapters_adapt(spi2Ptr, GLLayerSpiPtr(spi, Memory_leaker_const<GLLayerSpi>)) == TE_Ok) {
            adapters[spi] = spi2Ptr.get();
            GLLayerFactory2_registerSpi(std::move(spi2Ptr), priority);
        }
    }
}

void GLLayerFactory::unregisterSpi(GLLayerSpi *spi)
{
    WriteLock lock(mutex());

    std::set<SpiEntry, SpiEntryComp> &registry = spis();
    std::set<SpiEntry, SpiEntryComp>::iterator it;
    for (it = registry.begin(); it != registry.end(); it++)
    {
        if ((*it).spi == spi)
        {
            registry.erase(it);
            break;
        }
    }

    std::map<GLLayerSpi *, GLLayerSpi2 *> &adapters = legacyAdapters();
    std::map<GLLayerSpi *, GLLayerSpi2 *>::iterator adapterEntry;
    adapterEntry = adapters.find(spi);
    if (adapterEntry != adapters.end()) {
        GLLayerFactory2_unregisterSpi(*adapterEntry->second);
        adapters.erase(adapterEntry);
    }
}

GLLayer *GLLayerFactory::create(GLMapView *context, atakmap::core::Layer *layer)
{
    ReadLock lock(mutex());

    GLLayerSpiArg arg(context, layer);

    std::set<SpiEntry, SpiEntryComp> &registry = spis();
    std::set<SpiEntry, SpiEntryComp>::iterator it;
    for (it = registry.begin(); it != registry.end(); it++)
    {
        GLLayer *retval = (*it).spi->create(arg);
        if (retval)
            return retval;
    }

    return nullptr;
}
