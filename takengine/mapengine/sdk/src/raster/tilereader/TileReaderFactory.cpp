
#include "thread/Lock.h"
#include "raster/tilereader/TileReaderFactory.h"

using namespace atakmap::raster::tilereader;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

TileReaderSpi::~TileReaderSpi() { }

TileReaderFactory::ProviderMap TileReaderFactory::spis;
Mutex TileReaderFactory::mutex;

TileReader * TileReaderFactory::create(const char *uri, const Options *options) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error(
        "TileReaderFactory::create: Failed to acquire mutex");
    
    PGSC::String hint;
    
    ProviderMap::iterator it = spis.begin();
    ProviderMap::iterator end = spis.end();
    
    if (options) {
        hint = options->preferredSpi;
    }
    
    if (hint) {
        it = spis.find(hint);
        if (it != end) {
            end = it;
            ++end;
        }
    }
    
    for (; it != end; ++it) {
        TileReader *reader;
        if ((reader = (*it).second->create(uri, options))) {
            return reader;
        }
    }
    
    return NULL;
}

void TileReaderFactory::registerSpi(TileReaderSpi *spi) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error(
        "TileReaderFactory::create: Failed to acquire mutex");
    spis[spi->getName()] = spi;
}

void TileReaderFactory::unregisterSpi(TileReaderSpi *spi) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error(
        "TileReaderFactory::create: Failed to acquire mutex");
    ProviderMap::iterator end = spis.end();
    for (ProviderMap::iterator it = spis.begin(); it != end;) {
        if ((*it).second == spi) {
            it = spis.erase(it);
        } else {
            ++it;
        }
    }
}
