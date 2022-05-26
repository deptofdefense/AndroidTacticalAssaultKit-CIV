#include "raster/tilereader/TileReaderFactory2.h"
#include "util/CopyOnWrite.h"
#include <map>
#include <functional>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster::TileReader;



namespace {
    class TileReaderSpi2Registry
    {
       public:
        TAKErr registerSpi(const std::shared_ptr<TileReaderSpi2> &spi) NOTHROWS;
        TAKErr unregisterSpi(const std::shared_ptr<TileReaderSpi2> &spi) NOTHROWS;
        TAKErr create(TileReader2Ptr &result, const char *uri, const TileReaderFactory2Options *options) const NOTHROWS;
        TAKErr isSupported(const char *uri, const char *hint) const NOTHROWS;

       private:
        std::multimap<std::string, std::shared_ptr<TileReaderSpi2>> hint_sorted;
        std::multimap<int, std::shared_ptr<TileReaderSpi2>, std::greater<int>> priority_sorted;
    };

    CopyOnWrite<TileReaderSpi2Registry> &sharedTileReader2Registry();
}


TAKErr TAK::Engine::Raster::TileReader::TileReaderFactory2_create(TileReader2Ptr &reader, const char *uri) NOTHROWS
{
    return sharedTileReader2Registry().read()->create(reader, uri, nullptr);
}

TAKErr TAK::Engine::Raster::TileReader::TileReaderFactory2_create(TileReader2Ptr &reader, const char *uri,
    const TileReaderFactory2Options *options) NOTHROWS
{
    return sharedTileReader2Registry().read()->create(reader, uri, options);
}

TAKErr TAK::Engine::Raster::TileReader::TileReaderFactory2_register(const std::shared_ptr<TileReaderSpi2> &spi) NOTHROWS
{
    return sharedTileReader2Registry().invokeWrite(&TileReaderSpi2Registry::registerSpi, spi);
}

TAKErr TAK::Engine::Raster::TileReader::TileReaderFactory2_unregister(const std::shared_ptr<TileReaderSpi2> &spi) NOTHROWS
{
    return sharedTileReader2Registry().invokeWrite(&TileReaderSpi2Registry::unregisterSpi, spi);
}

TAKErr TAK::Engine::Raster::TileReader::TileReaderFactory2_isSupported(const char *uri, const char *hint) NOTHROWS
{
    return sharedTileReader2Registry().read()->isSupported(uri, hint);
}

TAKErr TAK::Engine::Raster::TileReader::TileReaderFactory2_isSupported(const char *uri) NOTHROWS
{
    return sharedTileReader2Registry().read()->isSupported(uri, nullptr);
}





TileReaderFactory2Options::TileReaderFactory2Options()
    : preferredSpi(nullptr), preferredTileWidth(0), preferredTileHeight(0), cacheUri(nullptr), asyncIO()
{}

TileReaderFactory2Options::TileReaderFactory2Options(const TileReaderFactory2Options &other)
    : preferredSpi(other.preferredSpi),
      preferredTileWidth(other.preferredTileWidth),
      preferredTileHeight(other.preferredTileHeight),
      cacheUri(other.cacheUri),
      asyncIO(other.asyncIO)
{}

TileReaderSpi2::~TileReaderSpi2() {}



namespace {

    CopyOnWrite<TileReaderSpi2Registry> &sharedTileReader2Registry()
    {
        static CopyOnWrite<TileReaderSpi2Registry> registry;
        return registry;
    }

    TAKErr TileReaderSpi2Registry::registerSpi(const std::shared_ptr<TileReaderSpi2> &spiPtr) NOTHROWS
    {
        if (!spiPtr)
            return TE_InvalidArg;
        const char *name = spiPtr->getName();
        if (!name)
            return TE_InvalidArg;

        TAKErr code = TE_Ok;
        TE_BEGIN_TRAP()
        {
            hint_sorted.insert(std::make_pair(name, spiPtr));
            priority_sorted.insert(std::make_pair(spiPtr->getPriority(), spiPtr));
        }
        TE_END_TRAP(code);
        return code;
    }

    template <typename Container, typename K>
    void eraseIf(Container &m, const K &key, const std::shared_ptr<TileReaderSpi2> &spi)
    {
        auto range = m.equal_range(key);
        for (auto it = range.first; it != range.second; ++it) {
            if (it->second == spi) {
                m.erase(it);
                break;
            }
        }
    }

    TAKErr TileReaderSpi2Registry::unregisterSpi(const std::shared_ptr<TileReaderSpi2> &spiPtr) NOTHROWS
    {
        if (!spiPtr)
            return TE_InvalidArg;
        const char *name = spiPtr->getName();
        if (!name)
            return TE_InvalidArg;

        eraseIf(this->priority_sorted, spiPtr->getPriority(), spiPtr);
        eraseIf(this->hint_sorted, name, spiPtr);

        return TE_Ok;
    }

    template <typename Iter>
    TAKErr createImpl(Iter begin, Iter end, TileReader2Ptr &result, const char *uri, const TileReaderFactory2Options *options) NOTHROWS
    {
        while (begin != end) {
            TAKErr code;
            if ((code = begin->second->create(result, uri, options)) != TE_Unsupported) {
                return code;
            }
            ++begin;
        }
        return TE_Unsupported;
    }

    template <typename Iter>
    TAKErr isSupportedImpl(Iter begin, Iter end, const char *uri) NOTHROWS
    {
        while (begin != end) {
            TAKErr code;
            if ((code = begin->second->isSupported(uri)) != TE_Unsupported) {
                return code;
            }
            ++begin;
        }
        return TE_Unsupported;
    }

    TAKErr TileReaderSpi2Registry::create(TileReader2Ptr &result, const char *uri, const TileReaderFactory2Options *options) const NOTHROWS
    {
        const char *hint = nullptr;
        if (options)
            hint = options->preferredSpi;

        if (hint) {
            auto range = hint_sorted.equal_range(hint);
            return createImpl(range.first, range.second, result, uri, options);
        }
        return createImpl(priority_sorted.begin(), priority_sorted.end(), result, uri, options);
    }

    TAKErr TileReaderSpi2Registry::isSupported(const char *uri, const char *hint) const NOTHROWS
    {
        // Note: Java ref impl doesn't use "hint"
        if (hint) {
            auto range = hint_sorted.equal_range(hint);
            return isSupportedImpl(range.first, range.second, uri);
        }
        return isSupportedImpl(priority_sorted.begin(), priority_sorted.end(), uri);
    }

}
