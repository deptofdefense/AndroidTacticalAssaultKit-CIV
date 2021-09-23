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
#define TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS 3u

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
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTILES_H
