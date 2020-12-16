#ifndef TAK_ENGINE_RENDERER_ELEVATION_TERRAINTILE_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_TERRAINTILE_H_INCLUDED

#include <cstdlib>

#include "model/Mesh.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"
#include "util/Allocatable.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                struct ENGINE_API TerrainTile : TAK::Engine::Util::Allocatable<TerrainTile>
                {
                    std::size_t skirtIndexOffset{0};
                    TAK::Engine::Elevation::ElevationChunk::Data data;
                    /** WGS84 AABB, x=longtitude, y=latitude, z=hae */
                    TAK::Engine::Feature::Envelope2 aabb_wgs84;
                    bool hasData{false};

                    /** @deprecated band-aid until depth hit test is implemented */
                    TAK::Engine::Elevation::ElevationChunk::Data data_proj;
                
                    bool heightmap{false};
                    std::size_t posts_x{0};
                    std::size_t posts_y{0};
                    /** if `true`, `aabb_wgs84.maxY` corresponds to row `posts_y-1u`, else corresponds to row `0` */
                    bool invert_y_axis{false};
                };
            }
        }
    }
}

#endif
