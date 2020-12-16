#include "raster/mobac/MobacMapTile.h"

using namespace atakmap::raster::mobac;


MobacMapTile::MobacMapTile() :
    data(NULL),
    dataLength(0),
    expiration(-1),
    releaseData(nullptr)
{
    bitmap.data = NULL;
    bitmap.dataLen = 0;
    bitmap.releaseData = NULL;
}
