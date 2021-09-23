#ifndef TAK_ENGINE_ELEVATION_ELEVATIONDATA_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONDATA_H_INCLUDED

#include <cmath>
#include <memory>

#include "port/Platform.h"
#include "port/String.h"
#include "port/Collection.h"
#include "util/Error.h"
#include "feature/Envelope2.h"
#include "core/GeoPoint2.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {



            ///=============================================================================
            ///
            ///  class TAK::Engine::Elevation::ElevationData
            ///
            ///     Interface class that provides methods for child classes to implement related
            ///     to collecting elevation data for given points on the earth. Each ElevationData
            ///     instance represents a specific collection of elevation points over a specific region
            ///     (potentially the whole world) that come from a single source e.g. DTED0, DTED1, etc.
            ///
            ///==============================================================================

            class ENGINE_API ElevationData
            {
            public:
                enum ENGINE_API
                {
                    MODEL_TERRAIN = 0x01,
                    MODEL_SURFACE = 0x02,
                };
            public :
                class ENGINE_API Hints;
            protected :
                virtual ~ElevationData() NOTHROWS = 0;
            public :
                /**
                * Returns a flag indicating the elevation model (terrain or surface).
                * Some default values defined in this class include MODEL_TERRAIN and
                * MODEL_SURFACE.
                *
                * @return  A flag indicating the model
                */
                virtual int getElevationModel() NOTHROWS = 0;

                /**
                * Obtains the underlying data type (e.g. DTED1)
                *
                * @param type OUTPUT VALUE that returns a string with the type of the data.
                *
                * @return TE_Ok on success
                */
                virtual Util::TAKErr getType(Port::String &type) NOTHROWS = 0;

                /**
                * The nominal resolution, in meters, of the data.
                *
                * @return Resolution in meters.
                */
                virtual double getResolution() NOTHROWS = 0;

                /**
                * Returns the elevation, as meters HAE, at the specified location. A value
                * of <code>NaN</code> is returned if no elevation is available.
                *
                * @param value The elevation value at the specified location, in meters HAE, or
                *          <code>NaN</code> if not available.
                * @param latitude  The latitude
                * @param longitude The longitude
                *
                * @return  TE_OK on success.
                */
                virtual Util::TAKErr getElevation(double *value, const double latitude, const double longitude) NOTHROWS = 0;

                /**
                * Returns elevation values for a set of points.
                *
                * @param points        The points to collect elevation data for.
                * @param elevations    OUTPUT VALUE that contains elevation values for
                *                      the items passed in the points argument, in the same
                *                      order as the items passed in the points argument.
                * @param hint          If the values in this object are non-NAN, specifies a minimum
                *                      bounding box containing all points. The
                *                      implementation may use this information to prefetch
                *                      all data that will be required up front, possibly
                *                      reducing IO.
                */
                virtual Util::TAKErr getElevation(
                    double *values,
                    Port::Collection<Core::GeoPoint2>::IteratorPtr &points,
                    const Hints &hint) NOTHROWS = 0;
            };

            inline ElevationData::~ElevationData() NOTHROWS
            {}

            typedef std::unique_ptr<ElevationData, void(*)(const ElevationData *)> ElevationDataPtr;

            ///=============================================================================
            ///
            ///  struct TAK::Engine::Elevation::Hints
            ///
            ///     A container struct that holds values that may be used when collecting elevation
            ///     in order to limit search areas and potentially speed up requests.
            ///
            ///==============================================================================

            class ENGINE_API ElevationData::Hints
            {
            public :
                /**
                * If <code>true</code> prefer fast read time over data precision
                */
                bool preferSpeed;
                /**
                * Indicates the sampling resolution
                */
                double resolution;
                /**
                * If <code>true</code> values will be interpolated
                */
                bool interpolate;
                /**
                * The query region
                */
                Feature::Envelope2 bounds;
            public :
                Hints() NOTHROWS :
                    preferSpeed(false),
                    resolution(NAN),
                    interpolate(true),
                    bounds(Feature::Envelope2(NAN, NAN, NAN, NAN, NAN, NAN))
                {};

                Hints(const Hints &other) NOTHROWS :
                    preferSpeed(other.preferSpeed),
                    resolution(other.resolution),
                    interpolate(other.interpolate),
                    bounds(other.bounds)
                {};

                Hints(bool preferSpeed, double resolution, bool interpolate, const Feature::Envelope2 &bounds) NOTHROWS :
                    preferSpeed(preferSpeed),
                    resolution(resolution),
                    interpolate(interpolate),
                    bounds(Feature::Envelope2(
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ))
                {};
            };
        }
    }
}

#endif
