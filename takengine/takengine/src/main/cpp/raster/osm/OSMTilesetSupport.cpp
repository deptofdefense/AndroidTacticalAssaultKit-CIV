#include "raster/osm/OSMTilesetSupport.h"

#include "raster/ImageDatasetDescriptor.h"
#include "raster/osm/OSMUtils.h"
#include "raster/tilepyramid/TilesetInfo.h"

using namespace atakmap::raster::osm;

using namespace atakmap::raster::tilepyramid;
using namespace atakmap::renderer;

OSMTilesetSupport::OSMTilesetSupport(TilesetInfo *tsInfo, AsyncBitmapLoader *loader) :
TilesetSupport(loader),
levelOffset(atoi(DatasetDescriptor::getExtraData(*tsInfo->getInfo(), "levelOffset", "0")))
{}

OSMTilesetSupport::~OSMTilesetSupport() { }

TileBounds OSMTilesetSupport::getTileBounds(int latIndex, int lngIndex, int level)
{
    level += this->levelOffset;
    latIndex = (1 << level) - latIndex - 1;
    TileBounds swne;
    swne[0] = OSMUtils::mapnikTileLat(level, latIndex + 1);
    swne[1] = OSMUtils::mapnikTileLng(level, lngIndex);
    swne[2] = OSMUtils::mapnikTileLat(level, latIndex);
    swne[3] = OSMUtils::mapnikTileLng(level, lngIndex + 1);
    
    return swne;
}

int OSMTilesetSupport::getTileZeroX(double lng, int gridX, int gridWidth)
{
    return OSMUtils::mapnikTileX(this->levelOffset, lng) - gridX;
}

int OSMTilesetSupport::getTileZeroY(double lat, int gridY, int gridHeight)
{
    if (lat > 85.0511)
        return gridHeight;
    else if (lat <= -85.0511)
        return gridY - 1;
    const int level = this->levelOffset;
    const int osmLatIndex = OSMUtils::mapnikTileY(level, lat);
    
    return ((1 << level) - osmLatIndex - 1) - gridY;
}

double OSMTilesetSupport::getTilePixelX(int latIndex, int lngIndex, int level, double lng)
{
    const int osmLevel = this->levelOffset + level;
    
    // call returns int
    return OSMUtils::mapnikPixelX(osmLevel, lngIndex, lng);
}

double OSMTilesetSupport::getTilePixelY(int latIndex, int lngIndex, int level, double lat)
{
    const int osmLevel = this->levelOffset + level;
    const int osmLatIndex = (1 << osmLevel) - latIndex - 1;
    
    // call returns int
    return OSMUtils::mapnikPixelY(osmLevel, osmLatIndex, lat);
}

double OSMTilesetSupport::getTilePixelLat(int latIndex, int lngIndex, int level, int y)
{
    const int osmLevel = this->levelOffset + level;
    const int osmLatIndex = (1 << osmLevel) - latIndex - 1;
    
    return OSMUtils::mapnikPixelLat(osmLevel, osmLatIndex, y);
}

double OSMTilesetSupport::getTilePixelLng(int latIndex, int lngIndex, int level, int x)
{
    const int osmLevel = this->levelOffset + level;
    return OSMUtils::mapnikPixelLng(osmLevel, lngIndex, x);
}
