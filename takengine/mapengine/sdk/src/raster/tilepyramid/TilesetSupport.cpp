
#include "thread/Lock.h"

#include "raster/tilepyramid/TilesetSupport.h"

#include "raster/ImageDatasetDescriptor.h"
#include "raster/tilepyramid/SimpleUriTilesetSupport.h"
#include "raster/tilepyramid/TilesetInfo.h"
//#include "private/Util.h"

using namespace atakmap::raster;
using namespace atakmap::raster::tilepyramid;
using namespace atakmap::renderer;
//using namespace atakmap::priv;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

TilesetSupport::SpiMap TilesetSupport::spis;
Mutex TilesetSupport::mutex;

TilesetSupport::TilesetSupport(AsyncBitmapLoader *loader)
: bitmapLoader(loader)
{}

TilesetSupport::~TilesetSupport() { }

int TilesetSupport::getTilesVersion(int latIndex, int lngiIdex, int level) {
    return 0;
}

void TilesetSupport::registerSpi(TilesetSupport::Spi *spi) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("TilesetSupport::registerSpi: Failed to acquire mutex");
    spis[spi->getName()] = spi;
}

void TilesetSupport::unregisterSpi(TilesetSupport::Spi *spi)
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error("TilesetSupport::unregisterSpi: Failed to acquire mutex");
    
    //spis->Remove(spi->getName());
}

TilesetSupport *TilesetSupport::createImpl(TilesetInfo *info, AsyncBitmapLoader *loader) {
    TilesetSupport::Spi *spi = nullptr;
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error("TilesetSupport::createImpl: Failed to acquire mutex");
        
        const char *spiClass = DatasetDescriptor::getExtraData(*info->getInfo(), "supportSpi", SimpleUriTilesetSupport::SPI->getName());
        SpiMap::iterator entry = spis.find(spiClass);
        if (entry == spis.end()) {
            return NULL;
        }
        spi = (*entry).second;
    }
    return spi->create(info, loader);
}

TilesetSupport *TilesetSupport::create(TilesetInfo *info, AsyncBitmapLoader *loader) {
    TilesetSupport::Spi *spi = nullptr;
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error("TilesetSupport::create: Failed to acquire mutex");
        
        const char *spiClass = DatasetDescriptor::getExtraData(*info->getInfo(), "supportSpi", "mobac");//SimpleUriTilesetSupport::SPI->getName());
        SpiMap::iterator entry = spis.find(spiClass);
        if (entry == spis.end()) {
            return NULL;
        }
        spi = (*entry).second;
    }
    
    size_t size = atakmap::util::getFileSize(info->getInfo()->getURI());
    //auto contents = atakmap::util::getDirContents("/var/mobile/Containers/Bundle/Application/23E7B575-3D1B-4396-9BB4-C3EFEA32EFB2/iTAK.app/Map/Layers");
    
    TilesetSupport *support = spi->create(info, loader);
    if (!support) {
        support = createImpl(info, loader);
    }
    return support;
}
