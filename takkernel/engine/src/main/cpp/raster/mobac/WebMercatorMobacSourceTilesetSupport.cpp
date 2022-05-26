
#include "raster/mobac/WebMercatorMobacMapSourceTilesetSupport.h"

#include "raster/mobac/MobacMapSourceTilesetSupport.h"
#include "renderer/AsyncBitmapLoader.h"

using namespace atakmap::raster::mobac;

//TODO--using namespace atakmap::raster::osm;
using namespace atakmap::raster::tilepyramid;
using namespace atakmap::renderer;
using namespace atakmap::util;

WebMercatorMobacMapSourceTilesetSupport::WebMercatorMobacMapSourceTilesetSupport(TilesetInfo *tsInfo, AsyncBitmapLoader *bitmapLoader, MobacMapSourceTilesetSupport *i) :
OSMTilesetSupport(tsInfo, bitmapLoader),
impl(i)
{}

WebMercatorMobacMapSourceTilesetSupport::~WebMercatorMobacMapSourceTilesetSupport() { }

void WebMercatorMobacMapSourceTilesetSupport::setOfflineMode(bool offlineOnly)
{
    this->impl->setOfflineMode(offlineOnly);
}

bool WebMercatorMobacMapSourceTilesetSupport::isOfflineMode()
{
    return this->impl->isOfflineMode();
}

void WebMercatorMobacMapSourceTilesetSupport::init()
{
    this->impl->init();
}

void WebMercatorMobacMapSourceTilesetSupport::release()
{
    this->impl->release();
}

void WebMercatorMobacMapSourceTilesetSupport::start()
{
    this->impl->start();
}

void WebMercatorMobacMapSourceTilesetSupport::stop()
{
    this->impl->stop();
}

FutureTask<Bitmap> WebMercatorMobacMapSourceTilesetSupport::getTile(int latIndex, int lngIndex, int level/*, Options opts*/)
{
    level += this->levelOffset;
    latIndex = (1 << level) - latIndex - 1;
    
    FutureTask<Bitmap> retval = this->impl->getTile(latIndex, lngIndex, level/*, opts*/);
    this->bitmapLoader->loadBitmap(retval);
    return retval;
}
