#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYSHADERS_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYSHADERS_H_INCLUDED

#include "model/VertexArray.h"
#include "renderer/Shader.h"
#include "renderer/core/GLDirtyRegion.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/feature/GLBatchPoint3.h"
#include "renderer/feature/GLBatchPolygon3.h"
#include "renderer/feature/GLBatchLineString3.h"
#include "renderer/feature/GLBatchPointBuffer.h"

#include "core/GeoPoint2.h"
#include "port/Collection.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                struct ENGINE_API LinesShader
                {
                    GLuint handle{ GL_NONE };
                    GLint u_mvp{ -1 };
                    GLint u_color{ -1 };
                    GLint a_vertexCoords{ -1 };
                    GLint a_color{ -1 };
                };
                struct ENGINE_API AntiAliasedLinesShader
                {
                    Renderer::Shader2 base;
                    GLint u_mvp{ -1 };
                    GLint u_viewportSize{ -1 };
                    GLint u_hitTest{ -1 };
                    GLint a_vertexCoord0{ -1 };
                    GLint a_vertexCoord1{ -1 };
                    GLint a_texCoord{ -1 };
                    GLint a_color{ -1 };
                    GLint a_normal{ -1 };
                    GLint a_halfStrokeWidth{ -1 };
                    GLint a_dir{ -1 };
                    GLint a_pattern{ -1 };
                    GLint a_factor{ -1 };
                };
                struct ENGINE_API PolygonsShader
                {
                    //Renderer::Shader2 base;
                    GLuint handle{ GL_NONE };
                    GLint uColor{ -1 };
                    GLint aPosition{ -1 };
                    GLint aOutlineWidth{ -1 };
                    GLint aExteriorVertex{ -1 };
                    GLint u_mvp{ -1 };
                    GLint a_color{ -1 };
                    GLint uViewport{ -1 };
                };
                struct ENGINE_API StrokedPolygonsShader
                {
                    Renderer::Shader2 base;
                    GLint u_mvp{ -1 };
                    GLint a_fillColor{ -1 };
                    GLint a_strokeColor{ -1 };
                    GLint a_strokeWidth{ -1 };
                    GLint a_edges{ -1 };
                };
                struct ENGINE_API PointsShader
                {
                    GLuint handle{ GL_NONE };
                    GLint uMVP{ -1 };
                    GLint uTexture{ -1 };
                    GLint uColor{ -1 };
                    GLint uMapRotation{ -1 };
                    GLint uCameraRtc{ -1 };
                    GLint uWcsScale{ -1 };
                    GLint uTanHalfFov{ -1 };
                    GLint uViewportHeight{ -1 };
                    GLint uDrawTilt{ -1 };
                    GLint aRotation{ -1 };
                    GLint spriteBottomLeft{ -1 };
                    GLint spriteDimensions{ -1 };
                    GLint aVertexCoords{ -1 };
                    GLint aPointSize{ -1 };
                    GLint aColor{ -1 };
                    GLint aId{ -1 };
                    GLint aAbsoluteRotationFlag{ -1 };
                };

                ENGINE_API LinesShader GLBatchGeometryShaders_getLinesShader(const TAK::Engine::Core::RenderContext& ctx) NOTHROWS;
                ENGINE_API AntiAliasedLinesShader GLBatchGeometryShaders_getAntiAliasedLinesShader(const TAK::Engine::Core::RenderContext& ctx) NOTHROWS;
                ENGINE_API PolygonsShader GLBatchGeometryShaders_getPolygonsShader(const TAK::Engine::Core::RenderContext& ctx) NOTHROWS;
                ENGINE_API StrokedPolygonsShader GLBatchGeometryShaders_getStrokedPolygonsShader(const TAK::Engine::Core::RenderContext& ctx) NOTHROWS;
                ENGINE_API PointsShader GLBatchGeometryShaders_getPointsShader(const TAK::Engine::Core::RenderContext& ctx) NOTHROWS;
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER4_H_INCLUDED

