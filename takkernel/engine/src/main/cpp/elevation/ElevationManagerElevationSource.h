#ifndef TAK_ENGINE_ELEVATION_ELEVATIONMANGERELEVATIONSOURCE_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONMANGERELEVATIONSOURCE_H_INCLUDED

#include "elevation/ElevationSource.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            enum ENGINE_API HeightmapStrategy
            {
                /** constructs heightmap from highest resolution data available */
                HighestResolution,
                /** fills with resolution equal to or lower than target (DESC). Holes may be present if no equal or lower resolution coverage is available */
                Low,
                /** fills with resolution equal to or lower than target (DESC). If any holes are present, fills with resolution higher than (ASC). */
                LowFillHoles,
            };

            Util::TAKErr ENGINE_API ElevationManagerElevationSource_create(ElevationSourcePtr& value, const std::size_t numPostsLat, const std::size_t numPostLng, const HeightmapStrategy strategy = HighestResolution) NOTHROWS;
        }
    }
}

#endif
