#include "formats/quantizedmesh/impl/QMElevationSampler.h"
#include "formats/quantizedmesh/TileCoord.h"
#include "formats/quantizedmesh/impl/TerrainDataCache.h"


using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;

QMElevationSampler::QMElevationSampler(const char *file, double maxGsd) : data()
{
    int level = TileCoord_getLevel(maxGsd);
    data = TerrainDataCache_getData(file, level);
}

QMElevationSampler::~QMElevationSampler() NOTHROWS
{
}


Util::TAKErr QMElevationSampler::sample(double *value, const double latitude, const double longitude) NOTHROWS
{
    double ret = data.get() != nullptr ? data->getElevation(latitude, longitude, true) : NAN;
    *value = ret;
    return Util::TE_Ok;
}

Util::TAKErr QMElevationSampler::sample(double *value, const std::size_t count,
    const double *srcLat, const double *srcLng, 
    const std::size_t srcLatStride, const std::size_t srcLngStride,
    const std::size_t dstStride) NOTHROWS
{
    if (data.get() == nullptr) {
        for (std::size_t i = 0; i < count; ++i)
            value[i*dstStride] = NAN;
        return Util::TE_Done;
    } else {
        return data->getElevation(value, count, srcLat, srcLng, srcLatStride, srcLngStride, dstStride, true);
    }
}