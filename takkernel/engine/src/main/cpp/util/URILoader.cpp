
#include "util/URILoader.h"

using namespace atakmap::util;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

URILoaderSpi::~URILoaderSpi() { }

Mutex URILoader::mutex;
std::list<URILoaderSpi *> URILoader::spis;

DataInput *URILoader::openURI(const char *uri, const atakmap::util::URILoaderArgs *optionalArgs) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("URILoader::openURI: Failed to acquire mutex");

    DataInput *retval = nullptr;
    
    for (URILoaderSpi *spi : spis) {
        if ((retval = spi->openURI(uri, optionalArgs))) {
            break;
        }
    }
    
    return retval;
}

void URILoader::registerSpi(atakmap::util::URILoaderSpi *spi, int priority) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("URILoader::registerSpi: Failed to acquire mutex");
    spis.push_back(spi);
}

void URILoader::unregisterSpi(atakmap::util::URILoaderSpi *spi) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("URILoader::unregisterSpi: Failed to acquire mutex");
    
    auto it = std::find(spis.begin(), spis.end(), spi);
    if (it != spis.end()) {
        spis.erase(it);
    }
}