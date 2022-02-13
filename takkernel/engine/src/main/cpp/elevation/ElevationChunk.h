#ifndef TAK_ENGINE_ELEVATION_ELEVATIONCHUNK_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONCHUNK_H_INCLUDED

#include "feature/Polygon2.h"
#include "math/Matrix2.h"
#include "model/Mesh.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {

            class ENGINE_API ElevationChunk
            {
            public :
                struct Data
                {
                public :
                    std::shared_ptr<Model::Mesh> value;
                    int srid {0};
                    Math::Matrix2 localFrame;
                    bool interpolated {false};
                };
            public:
                virtual ~ElevationChunk() NOTHROWS = 0;
            public :
                /**
                 * Returns the URI of the chunk
                 * @return
                 */
                virtual const char *getUri() const NOTHROWS = 0;
                /**
                 * Returns the data type of the chunk (informative).
                 * @return
                 */
                virtual const char *getType() const NOTHROWS = 0;
                /**
                 * Returns the nominal resolution of the chunk, in meters-per-pixel.
                 * @return
                 */
                virtual double getResolution() const NOTHROWS = 0;
                /**
                 * Returns the bounds of the chunk. The bounds shall be a quadrilateral
                 * polygon, with no holes. The exterior ring shall have <code>5</code>
                 * points, where the last point is equal to the first point. WGS84 with
                 * x=longitude, y=latitude.
                 *
                 * @return
                 */
                virtual const Feature::Polygon2 *getBounds() const NOTHROWS = 0;
                /**
                 * Obtains a new copy of the elevation data for the chunk.
                 * @return
                 */
                virtual Util::TAKErr createData(std::unique_ptr<Data, void(*)(const Data *)> &value) NOTHROWS = 0;
                /**
                 * Samples the elevation at the specified location, returning the elevation
                 * value in meters HAE. If no elevation value could be sampled at the
                 * location, {@link Double#NaN} is returned.
                 * @param latitude
                 * @param longitude
                 * @return
                 */
                virtual Util::TAKErr sample(double *value, const double latitude, const double longitude) NOTHROWS = 0;
                /**
                 * Bulk fetch of samples. Returns <code>true</code> if all missing samples
                 * were fetched, <code>false</code> otherwise. Elevation will be queried
                 * for all HAE values in the array that are {@link Double#NaN}.
                 *
                 * @param lla   The point array, in ordered Longitude, Latitude, Altitude HAE triplets.
                 * @param off
                 * @param len
                 * @return  TE_Ok if all elevation values were filled, TE_Done if one or more were
                 *                not filled, various codes on failure
                 */
                virtual Util::TAKErr sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng, const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS = 0;
                /**
                 * Get the circular error described by the elevation chunk.
                 * XXX should this be for a specific lat, lon?
                 * @return double in meters or Double.NaN if not valid
                 */
                virtual double getCE() const NOTHROWS = 0;
                /**
                 * Get the linear error described by the elevation chunk
                 * @return double in meters or Double.NaN if not valid
                 */
                virtual double getLE() const NOTHROWS = 0;
                /**
                 * Is the elevation data considered authoritative.
                 * @return boolean true if the data is authoritative.
                 */
                virtual bool isAuthoritative() const NOTHROWS = 0;
                /**
                 * Get the flags set on the elevation chunk.
                 * XXX where are the flags defined?
                 */
                virtual unsigned int getFlags() const NOTHROWS = 0;
            };

            typedef std::unique_ptr<ElevationChunk, void(*)(const ElevationChunk *)> ElevationChunkPtr;
            typedef std::unique_ptr<ElevationChunk::Data, void(*)(const ElevationChunk::Data *)> ElevationChunkDataPtr;
        }
    }
}

#endif
