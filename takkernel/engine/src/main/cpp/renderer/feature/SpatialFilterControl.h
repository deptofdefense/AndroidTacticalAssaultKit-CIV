#pragma once

#include "feature/SpatialFilter.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {

                /**
                 * Control to acquire the RasterDataAccess for the associated RasterLayer2.
                 */
                class ENGINE_API SpatialFilterControl
                {
                   public:
                    virtual ~SpatialFilterControl() NOTHROWS = 0;

                    virtual Util::TAKErr setSpatialFilters(Port::Collection<std::shared_ptr<Engine::Feature::SpatialFilter>>* spatial_filters) NOTHROWS = 0;
                };

                ENGINE_API const char* SpatialFilterControl_getType() NOTHROWS;
            }  // namespace Feature
        }      // namespace Renderer
    }          // namespace Engine
}  // namespace TAK
