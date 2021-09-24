#include "raster/mobac/EquirectangularMobacMapSourceTilesetSupport.h"

#include "raster/mobac/MobacMapSourceTilesetSupport.h"
#include "renderer/AsyncBitmapLoader.h"

using namespace atakmap::raster::mobac;

using namespace atakmap::raster::tilepyramid;
using namespace atakmap::renderer;
using namespace atakmap::util;

EquirectangularMobacMapSourceTilesetSupport::EquirectangularMobacMapSourceTilesetSupport(TilesetInfo *tsInfo, AsyncBitmapLoader *bitmapLoader, MobacMapSourceTilesetSupport *i) :
EquirectangularTilesetSupport(tsInfo, bitmapLoader),
impl(i)
{}

EquirectangularMobacMapSourceTilesetSupport::~EquirectangularMobacMapSourceTilesetSupport() { }

void EquirectangularMobacMapSourceTilesetSupport::setOfflineMode(bool offlineOnly)
{
    this->impl->setOfflineMode(offlineOnly);
}

bool EquirectangularMobacMapSourceTilesetSupport::isOfflineMode()
{
    return this->impl->isOfflineMode();
}

void EquirectangularMobacMapSourceTilesetSupport::init()
{
    this->impl->init();
}

void EquirectangularMobacMapSourceTilesetSupport::release()
{
    this->impl->release();
}

void EquirectangularMobacMapSourceTilesetSupport::start()
{
    this->impl->start();
}

void EquirectangularMobacMapSourceTilesetSupport::stop()
{
    this->impl->stop();
}

namespace {
    
    class LoadBitmapFutureImpl : public FutureImpl<Bitmap>, public AsyncBitmapLoader::Listener {
    public:
        virtual ~LoadBitmapFutureImpl() { }
        
        virtual void cancelImpl() {
            // can we?
        }
        
        virtual void bitmapLoadComplete(int jobid, atakmap::renderer::AsyncBitmapLoader::ERRCODE errcode, atakmap::renderer::Bitmap b) {
            if (errcode != AsyncBitmapLoader::BITMAP_OK) {
                this->setState(SharedState::Error);
            } else {
                this->completeProcessing(b);
            }
        }
    };
    
}

FutureTask<Bitmap> EquirectangularMobacMapSourceTilesetSupport::getTile(int latIndex, int lngIndex, int level/*, Options opts*/)
{
    level += this->levelOffset;
    latIndex = (1 << level) - latIndex - 1;
    
    FutureTask<Bitmap> retval = this->impl->getTile(latIndex, lngIndex, level/*, opts*/);
    this->bitmapLoader->loadBitmap(retval);
    return retval;
}
