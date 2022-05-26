#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_QMELEVATIONSAMPLER_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_QMELEVATIONSAMPLER_H_INCLUDED

#include "formats/quantizedmesh/impl/TerrainData.h"
#include "elevation/ElevationChunkFactory.h"
#include "port/Platform.h"
#include "util/Error.h"

#include "util/DataInput2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

/**
 * Sampler for a single QM Tile file
 */
class QMElevationSampler : public TAK::Engine::Elevation::Sampler {
public:
    QMElevationSampler(const char *file, double maxGsd);

    virtual ~QMElevationSampler() NOTHROWS;
    virtual Util::TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS;
    virtual Util::TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng,
                                const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS;

private:
    std::shared_ptr<TerrainData> data;
};

}
}
}
}
}

#endif
