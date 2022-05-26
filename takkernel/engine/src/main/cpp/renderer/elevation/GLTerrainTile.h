#ifndef TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTLE_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTLE_H_INCLUDED


#include "port/Platform.h"
#include "renderer/GL.h"
#include "renderer/GLTexture2.h"
#include "renderer/Shader.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/elevation/GLTerrainTile_decls.h"
#include "renderer/elevation/TerrainTile.h"
#include "renderer/elevation/TerrainTileShaders.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                enum ColorShaderOptions {
                    TECSO_Lighting = 0x01,
                    TECSO_DistanceFog = 0x02,
                };

                /**
                 * Draws the specified terrain tiles.
                 *
                 * <P>Invoking this function is equivalent to making the following calls:
                 * <UL>
                 *   <LI>GLTerrainTile_begin(states[0], shaders);
                 *   <LI>GLTerrainTile_setElevationScale(ctx, elevationScale);
                 *   <LI>GLTerrainTile_bindTexture(ctx, tex.getTexId(), tex.getTexWidth(), tex.getTexHeight());
                 *   <LI>GLTerrainTile_drawTerrainTiles(ctx, states, numStates, tiles, numTiles, r, g, b, a);
                 *   <LI>GLTerrainTile_end(ctx);
                 * </UL>
                 *
                 * @param states            The render states. A state is only
                 *                          rendered if it has an associated
                 *                          texture. The transforms associated
                 *                          with the state should define the
                 *                          transformation from LLA (longitude,
                 *                          latitude, altitude) positions to
                 *                          texture coordinates (absolute, not
                 *                          relative UV).
                 * @param numStates         The number of render states to be
                 *                          executed
                 * @param tiles             The terrain tiles
                 * @param numTiles          The number of terrain tiles
                 * @param shaders           The shader set appropriate for
                 *                          rendering to the scene
                 * @param tex               The texture to be applied to the
                 *                          tiles
                 * @param elevationScale    The scale factor to be applied to
                 *                          elevation values
                 * @param r                 The red intensity to be modulated
                 *                          with the texture
                 * @param g                 The green intensity to be modulated
                 *                          with the texture
                 * @param b                 The blue intensity to be modulated
                 *                          with the texture
                 * @param a                 The alpha intensity to be modulated
                 *                          with the texture
                 */
                void GLTerrainTile_drawTerrainTiles(const TAK::Engine::Renderer::Core::GLGlobeBase::State *states, const std::size_t numStates, const GLTerrainTile *tiles, const std::size_t numTiles, const TerrainTileShaders &shaders, const GLTexture2 &tex, const float elevationScale = 1.f, const float r = 1.f, const float g = 1.f, const float b = 1.f, const float a = 1.f) NOTHROWS;

                /**
                 * Configures a render context and GL states for drawing one or
                 * more terrain tiles.
                 *
                 * <P>Render operations should NOT be nested; only one render
                 * context should be active per GL context at any given time.
                 *
                 * @param view      The scene state to be rendered to
                 * @param shaders   The shader set appropriate for rendering to
                 *                  the scene
                 * @return  A context object to be used with subsequent
                 *          calls for this draw operation
                 */
                TerrainTileRenderContext GLTerrainTile_begin(const TAK::Engine::Core::MapSceneModel2 &view, const TerrainTileShaders &shaders) NOTHROWS;
                /**
                 * Binds the specified texture for subsequent
                 * `GLTerrainTile_drawTerrainTiles` calls.
                 *
                 * @param ctx       The tile rendering context
                 * @param texid     The texture ID
                 * @param texWidth  The texture width
                 * @param texHeight The texture height
                 */
                void GLTerrainTile_bindTexture(TerrainTileRenderContext &ctx, const GLuint texid, const std::size_t texWidth, const std::size_t texHeight) NOTHROWS;
                /**
                 * Set the elevation scale factor for subsequent
                 * `GLTerrainTile_drawTerrainTiles` calls.
                 *
                 * @param ctx               The tile rendering context
                 * @param elevationScale    The elevation scale factor
                 */
                void GLTerrainTile_setElevationScale(TerrainTileRenderContext &ctx, const float elevationScale) NOTHROWS;
                /**
                 * Draws the specified terrain tiles using the specified
                 * context.
                 *
                 * @param states            The render states. A state is only
                 *                          rendered if it has an associated
                 *                          texture. The transforms associated
                 *                          with the state should define the
                 *                          transformation from LLA (longitude,
                 *                          latitude, altitude) positions to
                 *                          texture coordinates (absolute, not
                 *                          relative UV).
                 * @param numStates         The number of render states to be
                 *                          executed
                 * @param tiles             The terrain tiles
                 * @param numTiles          The number of terrain tiles
                 * @param r                 The red intensity to be modulated
                 *                          with the texture
                 * @param g                 The green intensity to be modulated
                 *                          with the texture
                 * @param b                 The blue intensity to be modulated
                 *                          with the texture
                 * @param a                 The alpha intensity to be modulated
                 *                          with the texture
                 */
                void GLTerrainTile_drawTerrainTiles(TerrainTileRenderContext &ctx, const TAK::Engine::Renderer::Core::GLGlobeBase::State *states, const std::size_t numStates, const GLTerrainTile *terrainTile, const std::size_t numTiles, const float r = 1.f, const float g = 1.f, const float b = 1.f, const float a = 1.f) NOTHROWS;
                /**
                 * Draws the specified terrain tiles using the specified
                 * context.
                 *
                 * @param states            The render states. A state is only
                 *                          rendered if it has an associated
                 *                          texture. The transforms associated
                 *                          with the state should define the
                 *                          transformation from LLA (longitude,
                 *                          latitude, altitude) positions to
                 *                          texture coordinates (absolute, not
                 *                          relative UV).
                 * @param numStates         The number of render states to be
                 *                          executed
                 * @param tiles             The terrain tiles
                 * @param numTiles          The number of terrain tiles
                 * @param r                 The red intensity to be modulated
                 *                          with the texture
                 * @param g                 The green intensity to be modulated
                 *                          with the texture
                 * @param b                 The blue intensity to be modulated
                 *                          with the texture
                 * @param a                 The alpha intensity to be modulated
                 *                          with the texture
                 */
                void GLTerrainTile_drawTerrainTiles(TerrainTileRenderContext &ctx, const Math::Matrix2 &lla2tex, const GLTerrainTile *terrainTile, const std::size_t numTiles, const float r = 1.f, const float g = 1.f, const float b = 1.f, const float a = 1.f) NOTHROWS;
                /**
                 * Ends the draw operation associated with the specified
                 * context.
                 *
                 * @param ctx   The tile rendering context
                 */
                void GLTerrainTile_end(TerrainTileRenderContext &ctx) NOTHROWS;

                TerrainTileShaders GLTerrainTile_getColorShader(const TAK::Engine::Core::RenderContext &ctx, const int srid) NOTHROWS;
                TerrainTileShaders GLTerrainTile_getColorShader(const TAK::Engine::Core::RenderContext &ctx, const int srid, const unsigned int options) NOTHROWS;
                TerrainTileShaders GLTerrainTile_getDepthShader(const TAK::Engine::Core::RenderContext &ctx, const int srid) NOTHROWS;
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_ELEVATION_GLTERRAINTLE_H_INCLUDED
