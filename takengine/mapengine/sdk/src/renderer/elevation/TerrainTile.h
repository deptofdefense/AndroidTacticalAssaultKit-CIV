#ifndef TAK_ENGINE_RENDERER_ELEVATION_TERRAINTILE_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_TERRAINTILE_H_INCLUDED

#include <cstdlib>

#include "model/Mesh.h"
#include "model/SceneInfo.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                struct ENGINE_API TerrainTile
                {
                    std::size_t skirtIndexOffset;
                    std::shared_ptr<TAK::Engine::Elevation::ElevationChunk::Data> data;
                    /** WGS84 AABB, x=longtitude, y=latitude, z=hae */
                    TAK::Engine::Feature::Envelope2 aabb_wgs84;
                    bool hasData;

                    /** @deprecated band-aid until depth hit test is implemented */
                    std::shared_ptr<TAK::Engine::Elevation::ElevationChunk::Data> data_proj;
                
                    bool heightmap;
                    std::size_t posts_x;
                    std::size_t posts_y;
                    /** if `true`, `aabb_wgs84.maxY` corresponds to row `posts_y-1u`, else corresponds to row `0` */
                    bool invert_y_axis;
                };
            }
        }
    }
}

#endif
