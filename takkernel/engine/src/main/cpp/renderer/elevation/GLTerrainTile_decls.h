#ifndef TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTILE_DECLS_H
#define TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTILE_DECLS_H

#include "port/Platform.h"
#include "renderer/GL.h"
#include "renderer/Shader.h"
#include "renderer/elevation/TerrainTile.h"
#include "renderer/elevation/TerrainTileShaders.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                struct ENGINE_API GLTerrainTile
                {
                    /** The terrain tile */
                    std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile> tile;
                    /** Optionally allocated VBO containing the tile data */
                    GLuint vbo {GL_NONE};
                    /** Optionally allocated IBO containing the tile data (if indexed) */
                    GLuint ibo {GL_NONE};
                };

                struct TerrainTileRenderContext
                {
                    // render state
                    TerrainTileShader shader;
                    GLuint texid {GL_NONE};

                    // transforms
                    struct {
                        TAK::Engine::Math::Matrix2 primary[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
                        TAK::Engine::Math::Matrix2 secondary[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
                    } localFrame;
                    std::size_t numLocalFrames {0u};
                    struct {
                        TAK::Engine::Math::Matrix2 primary;
                        TAK::Engine::Math::Matrix2 secondary;
                    } mvp;
                    bool hasSecondary {false};
                    float elevationScale {1.f};

                    struct {
                        /** altitude, expressed in degrees */
                        double altitude{90.0};
                        /** azimuth, expressed in degrees relative to north, clockwise */
                        double azimuth{0.0};
                        float intensity {1.f};
                    } lightSources[TE_GLTERRAINTILE_MAX_LIGHT_SOURCES];
                    std::size_t numLightSources {0u};
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTILES_H
