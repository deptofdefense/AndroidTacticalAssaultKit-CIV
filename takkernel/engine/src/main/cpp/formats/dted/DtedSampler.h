#ifndef TAK_ENGINE_FORMATS_DTED_DTEDSAMPLER_H_INCLUDED
#define TAK_ENGINE_FORMATS_DTED_DTEDSAMPLER_H_INCLUDED

#include "elevation/ElevationChunkFactory.h"
#include "feature/Envelope2.h"
#include "port/Platform.h"
#include "port/String.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace DTED {
class ENGINE_API DtedSampler : public Elevation::Sampler {
   public:
    /**
     * @param lat   Latitude of upper-left corner of DTED cell
     * @param lng   Longitude of upper-left corner of DTED cell
     */
    DtedSampler(const char *file, const double lat, const double lng) NOTHROWS;

   public:
    virtual Util::TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS;
    virtual Util::TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng,
                                const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS;

   private:
    TAK::Engine::Port::String file;
    TAK::Engine::Feature::Envelope2 bounds;
};

/**
 * Samples the specified DTED file for the raw MSL value at the specified location.
 * @param value         Returns the MSL value sampled
 * @param file          The DTED file
 * @param latitude      The latitude
 * @param longtitude    The longitude
 * @return The sampled value, in MSL
 */
ENGINE_API Util::TAKErr DTED_sample(double *value, const char *file, const double latitude, const double longitude) NOTHROWS;
}  // namespace DTED
}  // namespace Formats
}  // namespace Engine
}  // namespace TAK
#endif
