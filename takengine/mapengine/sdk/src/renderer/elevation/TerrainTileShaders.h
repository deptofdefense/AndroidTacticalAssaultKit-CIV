#ifndef TAK_ENGINE_RENDERER_ELEVATION_TERRAINTILESHADERS_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_TERRAINTILESHADERS_H_INCLUDED

#include "renderer/Shader.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                struct TerrainTileShader
                {
                    TAK::Engine::Renderer::Shader2 base;
                    // vertex shader
                    int uModelViewOffscreen;
                    int uLocalTransform;
                    int uTexWidth;
                    int uTexHeight;
                    int uElevationScale;
                };

                struct TerrainTileShaders
                {
                    TerrainTileShader hi;
                    TerrainTileShader md;
                    TerrainTileShader lo;
                    /** if `drawMapResolution` <= threshold, use `hi` */
                    double hi_threshold;
                    /** if `drawMapResolution` <= threshold, use `md` */
                    double md_threshold;
                };
            }
        }
    }
}

#endif

