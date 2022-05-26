
#include <cmath>

#include "raster/tilepyramid/EquirectangularTilesetSupport.h"

#include "raster/ImageDatasetDescriptor.h"
#include "raster/tilepyramid/TilesetInfo.h"

using namespace atakmap::raster::tilepyramid;

using namespace atakmap::raster;
using namespace atakmap::renderer;

EquirectangularTilesetSupport::EquirectangularTilesetSupport(TilesetInfo *tsInfo, AsyncBitmapLoader *bitmapLoader) :
TilesetSupport(bitmapLoader),
levelOffset(atoi(DatasetDescriptor::getExtraData(*tsInfo->getInfo(), "levelOffset", "0"))),
zeroWidth(tsInfo->getZeroWidth()),
zeroHeight(tsInfo->getZeroHeight()),
gridOriginLat(tsInfo->getGridOriginLat()),
gridOriginLng(tsInfo->getGridOriginLng()),
tilePixelHeight(tsInfo->getTilePixelHeight()),
tilePixelWidth(tsInfo->getTilePixelWidth())
{}

EquirectangularTilesetSupport::~EquirectangularTilesetSupport() { }

TileBounds EquirectangularTilesetSupport::getTileBounds(int latIndex, int lngIndex, int level)
{
    
    const double tileWidth = this->zeroWidth / (1 << level);
    const double tileHeight = this->zeroHeight / (1 << level);
    
    const double south = (latIndex * tileHeight) + this->gridOriginLat;
    const double west = (lngIndex * tileWidth) + this->gridOriginLng;
    
    TileBounds swne;
    swne[0] = south;
    swne[1] = west;
    swne[2] = south + tileHeight;
    swne[3] = west + tileWidth;
    return swne;
}

int EquirectangularTilesetSupport::getTileZeroX(double lng, int gridX, int gridWidth) {
    int westIndex = ((int) ((lng - this->gridOriginLng) / this->zeroWidth)) - gridX;
    return std::max(0, std::min(westIndex, gridWidth));
}

int EquirectangularTilesetSupport::getTileZeroY(double lat, int gridY, int gridHeight) {
    int southIndex = ((int) ((lat - this->gridOriginLat) / this->zeroHeight)) - gridY;
    return std::max(0, std::min(southIndex, gridHeight));
}

double EquirectangularTilesetSupport::getTilePixelX(int latIndex, int lngIndex, int level, double lng)
{
    const double tileWidthDegLng = this->zeroWidth / (1 << level);
    const int tileWidthPixels = this->tilePixelWidth;
    const double tileGridOriginLngWest = this->gridOriginLng;
    
    const double degPerPixelLng = tileWidthDegLng / tileWidthPixels;
    const double tileOffsetWest = tileGridOriginLngWest + (tileWidthDegLng * lngIndex);
    
    return (lng - tileOffsetWest) / degPerPixelLng;
}

double EquirectangularTilesetSupport::getTilePixelY(int latIndex, int lngIndex, int level, double lat)
{
    const double tileHeightDegLat = this->zeroHeight / (1 << level);
    const int tileHeightPixels = this->tilePixelHeight;
    const double tileGridOriginLatSouth = this->gridOriginLat;
    
    const double degPerPixelLat = tileHeightDegLat / tileHeightPixels;
    const double tileOffsetSouth = tileGridOriginLatSouth + (tileHeightDegLat * latIndex);
    
    return tileHeightPixels - ((lat - tileOffsetSouth) / degPerPixelLat);
}

double EquirectangularTilesetSupport::getTilePixelLat(int latIndex, int lngIndex, int level, int y)
{
    const double tileHeightDegLat = this->zeroHeight / (1 << level);
    const int tileHeightPixels = this->tilePixelHeight;
    const double tileGridOriginLatSouth = this->gridOriginLat;
    
    const double degPerPixelLat = tileHeightDegLat / tileHeightPixels;
    const double tileOffsetSouth = tileGridOriginLatSouth + (tileHeightDegLat * latIndex);
    
    return tileOffsetSouth + (tileHeightPixels - y) * degPerPixelLat;
}

double EquirectangularTilesetSupport::getTilePixelLng(int latIndex, int lngIndex, int level, int x)
{
    const double tileWidthDegLng = this->zeroWidth / (1 << level);
    const int tileWidthPixels = this->tilePixelWidth;
    const double tileGridOriginLngWest = this->gridOriginLng;
    
    const double degPerPixelLng = tileWidthDegLng / tileWidthPixels;
    const double tileOffsetWest = tileGridOriginLngWest + (tileWidthDegLng * lngIndex);
    
    return tileOffsetWest + x * degPerPixelLng;
}
