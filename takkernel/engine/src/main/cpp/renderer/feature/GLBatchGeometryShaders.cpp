#include "renderer/feature/GLBatchGeometryShaders.h"

#include <cassert>
#include <map>

#include "renderer/GLSLUtil.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    const char *POINT_VSH =
    #include "renderer/feature/BatchGeometryPoints.vert"
    ;
    const char *POINT_FSH =
    #include "renderer/feature/BatchGeometryPoints.frag"
    ;
    const char *LINE_VSH =
    #include "renderer/feature/BatchGeometryAntiAliasedLines.vert"
    ;
    const char *LINE_FSH =
    #include "renderer/feature/BatchGeometryAntiAliasedLines.frag"
    ;
    const char *POLYGON_VSH =
    #include "renderer/feature/BatchGeometryPolygons.vert"
    ;
    const char *POLYGON_FSH =
    #include "renderer/feature/BatchGeometryPolygons.frag"
    ;

    Mutex mutex;
    struct {
        std::map<const RenderContext*, LinesShader> lines;
        std::map<const RenderContext*, AntiAliasedLinesShader> antiAliasedLines;
        std::map<const RenderContext*, PolygonsShader> polygons;
        std::map<const RenderContext*, StrokedPolygonsShader> strokedPolygons;
        std::map<const RenderContext*, PointsShader> points;
    } shaders;
}

LinesShader TAK::Engine::Renderer::Feature::GLBatchGeometryShaders_getLinesShader(const RenderContext& ctx) NOTHROWS
{
    Lock lock(mutex);
    const auto entry = shaders.lines.find(&ctx);
    if (entry != shaders.lines.end())
        return entry->second;
    LinesShader shader;
    // XXX - TODO
    shaders.lines[&ctx] = shader;
    return shader;
}
AntiAliasedLinesShader TAK::Engine::Renderer::Feature::GLBatchGeometryShaders_getAntiAliasedLinesShader(const RenderContext& ctx) NOTHROWS
{
    Lock lock(mutex);
    const auto entry = shaders.antiAliasedLines.find(&ctx);
    if (entry != shaders.antiAliasedLines.end())
        return entry->second;

    TAKErr code(TE_Ok);
    AntiAliasedLinesShader shader;
    int vertShader = GL_NONE;
    code = GLSLUtil_loadShader(&vertShader, LINE_VSH, GL_VERTEX_SHADER);
    assert(code == TE_Ok);

    int fragShader = GL_NONE;
    code = GLSLUtil_loadShader(&fragShader, LINE_FSH, GL_FRAGMENT_SHADER);
    assert(code == TE_Ok);

    ShaderProgram prog;
    code = GLSLUtil_createProgram(&prog, vertShader, fragShader);
    glDeleteShader(vertShader);
    glDeleteShader(fragShader);
    assert(code == TE_Ok);
    shader.base.handle = prog.program;

    glUseProgram(shader.base.handle);
    shader.u_mvp = glGetUniformLocation(shader.base.handle, "u_mvp");
    shader.u_viewportSize = glGetUniformLocation(shader.base.handle, "u_viewportSize");
    shader.u_hitTest= glGetUniformLocation(shader.base.handle, "u_hitTest");
    shader.a_vertexCoord0 = glGetAttribLocation(shader.base.handle, "a_vertexCoord0");
    shader.a_vertexCoord1 = glGetAttribLocation(shader.base.handle, "a_vertexCoord1");
    shader.a_color = glGetAttribLocation(shader.base.handle, "a_color");
    shader.a_normal = glGetAttribLocation(shader.base.handle, "a_normal");
    shader.a_halfStrokeWidth = glGetAttribLocation(shader.base.handle, "a_halfStrokeWidth");
    shader.a_dir = glGetAttribLocation(shader.base.handle, "a_dir");
    shader.a_pattern = glGetAttribLocation(shader.base.handle, "a_pattern");
    shader.a_factor = glGetAttribLocation(shader.base.handle, "a_factor");

    shaders.antiAliasedLines[&ctx] = shader;
    return shader;
}
PolygonsShader TAK::Engine::Renderer::Feature::GLBatchGeometryShaders_getPolygonsShader(const RenderContext& ctx) NOTHROWS
{
    Lock lock(mutex);
    const auto entry = shaders.polygons.find(&ctx);
    if (entry != shaders.polygons.end())
        return entry->second;
    TAKErr code(TE_Ok);
    PolygonsShader shader;
    int vertShader = GL_NONE;
    code = GLSLUtil_loadShader(&vertShader, POLYGON_VSH, GL_VERTEX_SHADER);
    assert(code == TE_Ok);

    int fragShader = GL_NONE;
    code = GLSLUtil_loadShader(&fragShader, POLYGON_FSH, GL_FRAGMENT_SHADER);
    assert(code == TE_Ok);

    ShaderProgram prog;
    code = GLSLUtil_createProgram(&prog, vertShader, fragShader);
    glDeleteShader(vertShader);
    glDeleteShader(fragShader);
    assert(code == TE_Ok);
    shader.handle = prog.program;

    glUseProgram(shader.handle);
    shader.u_mvp = glGetUniformLocation(shader.handle, "u_mvp");
    shader.uViewport = glGetUniformLocation(shader.handle, "uViewport");
    shader.uColor = glGetUniformLocation(shader.handle, "u_color");
    shader.aPosition = glGetAttribLocation(shader.handle, "aPosition");
    shader.aOutlineWidth = glGetAttribLocation(shader.handle, "aOutlineWidth");
    shader.aExteriorVertex = glGetAttribLocation(shader.handle, "aExteriorVertex");
    shader.a_color = glGetAttribLocation(shader.handle, "a_color");
    shaders.polygons[&ctx] = shader;
    return shader;
}
StrokedPolygonsShader TAK::Engine::Renderer::Feature::GLBatchGeometryShaders_getStrokedPolygonsShader(const RenderContext& ctx) NOTHROWS
{
    Lock lock(mutex);
    const auto entry = shaders.strokedPolygons.find(&ctx);
    if (entry != shaders.strokedPolygons.end())
        return entry->second;
    StrokedPolygonsShader shader;
    // XXX - TODO
    shaders.strokedPolygons[&ctx] = shader;
    return shader;
}
PointsShader TAK::Engine::Renderer::Feature::GLBatchGeometryShaders_getPointsShader(const RenderContext& ctx) NOTHROWS
{
    Lock lock(mutex);
    const auto entry = shaders.points.find(&ctx);
    if (entry != shaders.points.end())
        return entry->second;

    TAKErr code(TE_Ok);
    PointsShader shader;
    int vertShader = GL_NONE;
    code = GLSLUtil_loadShader(&vertShader, POINT_VSH, GL_VERTEX_SHADER);
    assert(code == TE_Ok);

    int fragShader = GL_NONE;
    code = GLSLUtil_loadShader(&fragShader, POINT_FSH, GL_FRAGMENT_SHADER);
    assert(code == TE_Ok);

    ShaderProgram prog{ 0u, 0u, 0u };
    code = GLSLUtil_createProgram(&prog, vertShader, fragShader);
    glDeleteShader(prog.fragShader);
    glDeleteShader(prog.vertShader);
    assert(code == TE_Ok);

    shader.handle = prog.program;
    glUseProgram(shader.handle);

    shader.uMVP = glGetUniformLocation(shader.handle, "uMVP");
    shader.uTexture = glGetUniformLocation(shader.handle, "uTexture");
    shader.uColor = glGetUniformLocation(shader.handle, "uColor");
    shader.uMapRotation = glGetUniformLocation(shader.handle, "uMapRotation");
    shader.uCameraRtc = glGetUniformLocation(shader.handle, "uCameraRtc");
    shader.uWcsScale = glGetUniformLocation(shader.handle, "uWcsScale");
    shader.uTanHalfFov = glGetUniformLocation(shader.handle, "uTanHalfFov");
    shader.uViewportHeight = glGetUniformLocation(shader.handle, "uViewportHeight");
    shader.uDrawTilt = glGetUniformLocation(shader.handle, "uDrawTilt");
    shader.aColor = glGetUniformLocation(shader.handle, "aRotation");
    shader.aPointSize = glGetAttribLocation(shader.handle, "aPointSize");
    shader.aRotation = glGetAttribLocation(shader.handle, "aRotation");
    shader.aVertexCoords = glGetAttribLocation(shader.handle, "aVertexCoords");
    shader.spriteBottomLeft = glGetAttribLocation(shader.handle, "aSpriteBottomLeft");
    shader.spriteDimensions = glGetAttribLocation(shader.handle, "aSpriteDimensions");
    shader.aColor = glGetAttribLocation(shader.handle, "aColor");
    shader.aId = glGetAttribLocation(shader.handle, "aId");
    shader.aAbsoluteRotationFlag = glGetAttribLocation(shader.handle, "aAbsoluteRotationFlag");

    shaders.points[&ctx] = shader;
    return shader;
}
