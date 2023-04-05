#pragma once

#include "feature/Polygon2.h"
#include "feature/SpatialCalculator2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            enum SpatialFilterType
            {
                Include,
                Exclude
            };

            class ENGINE_API SpatialFilter
            {
               protected:
                virtual ~SpatialFilter() NOTHROWS = 0;

               public:
                virtual Util::TAKErr getType(SpatialFilterType &value) NOTHROWS = 0;
                virtual Util::TAKErr getFilterGeometry(Feature::Polygon2 **filter_geometry) NOTHROWS = 0;
            };
            typedef std::unique_ptr<SpatialFilter, void (*)(const SpatialFilter *)> SpatialFilterPtr;
        }  // namespace Feature
    }      // namespace Engine
}  // namespace TAK
