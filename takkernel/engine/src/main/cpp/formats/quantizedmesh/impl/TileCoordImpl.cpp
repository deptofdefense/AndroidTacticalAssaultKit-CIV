#include "formats/quantizedmesh/TileCoord.h"
#include "formats/quantizedmesh/impl/TileCoordImpl.h"
#include "feature/LineString2.h"
#include "feature/Polygon2.h"
#include "math/Utils.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;



TileCoordImpl::TileCoordImpl(int x, int y, int z) : x(x), y(y), z(z)
{
}

TileCoordImpl::TileCoordImpl(double lat, double lon, int z) : x((int)TileCoord_getTileX(lon, z)), y((int)TileCoord_getTileY(lat, z)), z(z)
{
}

Util::TAKErr TileCoordImpl::getCoverage(Feature::Geometry2Ptr &result)
{
    double lat0 = TileCoord_getLatitude(y, z);
    double lat1 = TileCoord_getLatitude(y + 1, z);
    double lon0 = TileCoord_getLongitude(x, z);
    double lon1 = TileCoord_getLongitude(x + 1, z);
    double s = atakmap::math::min(lat0, lat1);
    double w = atakmap::math::min(lon0, lon1);
    double n = atakmap::math::max(lat0, lat1);
    double e = atakmap::math::max(lon0, lon1);

    Feature::LineString2 cov;
    cov.addPoint(n, w);
    cov.addPoint(n, e);
    cov.addPoint(s, e);
    cov.addPoint(s, w);
    cov.addPoint(n, w);
    
    result = Feature::Geometry2Ptr(new Feature::Polygon2(cov), Util::Memory_deleter_const<Feature::Geometry2, Feature::Polygon2>);
    return Util::TE_Ok;
}

