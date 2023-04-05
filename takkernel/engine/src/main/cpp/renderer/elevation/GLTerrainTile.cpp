#include "renderer/elevation/GLTerrainTile.h"

#include <algorithm>

#include "elevation/ElevationManager.h"
#include "math/Ellipsoid2.h"
#include "renderer/GLMatrix.h"
#include "renderer/GLSLUtil.h"
#include "thread/Mutex.h"
#include "core/ProjectionFactory3.h"

using namespace TAK::Engine::Renderer::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

constexpr const char* COLOR_ECEF_VERT_SHADER_SRC_BASE =
#include "shaders/TerrainTileECEFLo.vert"
;

const std::string COLOR_ECEF_VERT_SHADER_SRC = std::string(TE_GLSL_VERSION_300_ES) + std::string(COLOR_ECEF_VERT_SHADER_SRC_BASE);

constexpr const char* COLOR_PLANAR_VERT_SHADER_SRC_BASE =
#include "shaders/TerrainTilePlanar.vert"
;
const std::string COLOR_PLANAR_VERT_SHADER_SRC = std::string(TE_GLSL_VERSION_300_ES) + std::string(COLOR_PLANAR_VERT_SHADER_SRC_BASE);


#if 0
// XXX - experimenting with distance fog
#define COLOR_FRAG_SHADER_SRC \
    "#version 100\n" \
    "precision mediump float;\n" \
    "uniform sampler2D uTexture;\n" \
    "uniform vec4 uColor;\n" \
    "varying vec2 vTexPos;\n" \
    "void main(void) {\n" \
    "  const float density = 0.000005;\n"\
    "  const float LOG2 = 1.442695;\n"\
    "  float z = gl_FragCoord.z / gl_FragCoord.w;\n"\
    "  float fogFactor = exp2(-density*density*z*z*LOG2);\n"\
    "  fogFactor = clamp(fogFactor, 0.0, 1.0);\n"\
    "  vec4 fragColor = texture2D(uTexture, vTexPos)*uColor;\n"\
    "  vec4 fogColor = vec4(0.6, 0.6, 0.6, 1.0);\n"\
    "  gl_FragColor = mix(fogColor, fragColor, fogFactor);\n"\
    "}"
#else

constexpr const char* LIGHTING_EFFECT_SRC =
#include "shaders/ApplyLighting.frag"
;

constexpr const char* LIGHTING_EFFECT_EMPTY = "vec4 ApplyLighting(vec4 color){return color;}";
constexpr const char* COLOR_FRAG_SHADER_SRC_BASE =
#include "shaders/Color.frag"
;

const std::string COLOR_FRAG_SHADER_SRC = std::string(TE_GLSL_VERSION_300_ES) + std::string(COLOR_FRAG_SHADER_SRC_BASE);

#endif
// depth shaders
// XXX - `floatBitsToInt` producing undesirable results on Android/desktop GL, but spot on in ANGLE. Further debugging required
#define USE_GLSL_FLOATBITSTOINT 0
#if USE_GLSL_FLOATBITSTOINT
#define PACK_DEPTH_FN_SRC \
"vec4 PackDepth(float v) {\n" \
"  int bs = floatBitsToInt(v);\n" \
"  int a = (bs>>24)&0xFF;\n" \
"  int b = (bs>>16)&0xFF;\n" \
"  int c = (bs>>8)&0xFF;\n" \
"  int d = bs&0xFF;\n" \
"  float na = float(a)/255.0;\n" \
"  float nb = float(b)/255.0;\n" \
"  float nc = float(c)/255.0;\n" \
"  float nd = float(d)/255.0;\n" \
"  return vec4(na, nb, nc, nd);\n" \
"}\n"

#else
#define PACK_DEPTH_FN_SRC \
"vec4 PackDepth(float v) {\n" \
"  vec4 r = vec4(1.,255.,65025.,16581375.) * v;\n" \
"  r = fract(r);\n" \
"  r -= r.yzww * vec4(1.0/255.0,1.0/255.0,1.0/255.0,0.0);\n" \
"  return r;\n" \
"}\n"
#endif

constexpr const char* DEPTH_ECEF_VERT_SHADER_SRC_BASE =
#include "shaders/TerrainTileDepthECEFLo.vert"
;

const std::string DEPTH_ECEF_VERT_SHADER_SRC = std::string(TE_GLSL_VERSION_300_ES) + std::string(DEPTH_ECEF_VERT_SHADER_SRC_BASE);

constexpr const char* DEPTH_PLANAR_VERT_SHADER_SRC_BASE =
#include "shaders/TerrainTileDepthPlanar.vert"
;

const std::string DEPTH_PLANAR_VERT_SHADER_SRC = std::string(TE_GLSL_VERSION_300_ES) + std::string(DEPTH_PLANAR_VERT_SHADER_SRC_BASE);

constexpr const char* DEPTH_FRAG_SHADER_SRC_BASE =
#include "shaders/Depth.frag"
;

const std::string DEPTH_FRAG_SHADER_SRC = std::string(TE_GLSL_VERSION_300_ES) + std::string(DEPTH_FRAG_SHADER_SRC_BASE);

#define FLAT_HI_THRESHOLD 0.0

#define GLOBE_HI_THRESHOLD 0.0


namespace
{
    void setOrtho(Matrix2 *value, const double left, const double right, const double bottom, const double top, const double zNear, const double zFar) NOTHROWS;
    void drawTerrainTilesImpl(const GLGlobeBase::State *renderPasses, const std::size_t numRenderPasses, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTexture2 &texture, const GLTerrainTile *terrainTiles, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS;
    void glUniformMatrix4(GLint location, const Matrix2 &matrix, const Matrix2::MatrixOrder order = Matrix2::COLUMN_MAJOR) NOTHROWS;
    void glUniformMatrix4v(GLint location, const Matrix2 *matrix, const std::size_t count) NOTHROWS;
    void drawTerrainTileImpl(const Matrix2 &lla2tex, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &gltile, const bool drawSkirt, const float r, const float g, const float b, const float a) NOTHROWS;
    void glSceneModel(MapSceneModel2 &scene) NOTHROWS;
    TAKErr createTerrainTileShader(TerrainTileShader *value, const char *vertShaderSrc, const char *fragShaderSrc) NOTHROWS;
    TAKErr createTerrainTileShaders(TerrainTileShaders *value,
                                  const char *vertShaderSrc,
                                  const char *fragShaderSrc) NOTHROWS;
    GLenum glType(const DataType tedt) NOTHROWS;
    void configureLightingUniforms(const TerrainTileRenderContext &ctx, const TerrainTileShader &shader) NOTHROWS;

    std::map<const RenderContext *, std::map<int64_t, TerrainTileShaders>> colorShaders;
    std::map<const RenderContext *, std::map<int, TerrainTileShaders>> depthShaders;
}

void TAK::Engine::Renderer::Elevation::GLTerrainTile_drawTerrainTiles(const GLGlobeBase::State *states, const std::size_t numStates, const GLTerrainTile *terrainTiles, const std::size_t numTiles, const TerrainTileShaders &shaders, const GLTexture2 &tex, const float elevationScale, const float r, const float g, const float b, const float a) NOTHROWS
{
    if(!numStates)
        return; // nothing to draw

    const GLGlobeBase::State &view = states[0];

    TerrainTileRenderContext ctx = GLTerrainTile_begin(view.scene, shaders);
    GLTerrainTile_setElevationScale(ctx, elevationScale);
    GLTerrainTile_bindTexture(ctx, tex.getTexId(), tex.getTexWidth(), tex.getTexHeight());
    GLTerrainTile_drawTerrainTiles(ctx, states, numStates, terrainTiles, numTiles, r, g, b, a);
    GLTerrainTile_end(ctx);
}

TerrainTileRenderContext TAK::Engine::Renderer::Elevation::GLTerrainTile_begin(const MapSceneModel2 &scene, const TerrainTileShaders &shaders) NOTHROWS
{
    TerrainTileRenderContext ctx;

    GeoPoint2 focus;
    scene.projection->inverse(&focus, scene.camera.target);

    // select GSD based on AGL if specified
    double gsd = scene.gsd;
    if(!TE_ISNAN(scene.camera.agl)) {
        const double aglgsd = MapSceneModel2_gsd(scene.camera.agl, scene.camera.fov, scene.height);
        gsd = std::min(scene.gsd, aglgsd);
    }

    // select shader
    ctx.shader = (gsd <= shaders.hi_threshold) ?
                                shaders.hi :
                (gsd <= shaders.md_threshold) ?
                                shaders.md :
                                shaders.lo;

    glUseProgram(ctx.shader.base.handle);
    GLint activeTexture;
    glGetIntegerv(GL_ACTIVE_TEXTURE, &activeTexture);

    glUniform1i(ctx.shader.base.uTexture, activeTexture - GL_TEXTURE0);
    glUniform1f(ctx.shader.uTexWidth, 1.f);
    glUniform1f(ctx.shader.uTexHeight, 1.f);

    glUniform1f(ctx.shader.uElevationScale, 1.f);

    // first pass
    {
        // construct the MVP matrix
        if (scene.camera.mode == MapCamera2::Perspective) {
            ctx.mvp.primary = scene.camera.projection;
            ctx.mvp.primary.concatenate(scene.camera.modelView);
        } else {
            // projection
            setOrtho(&ctx.mvp.primary, 0.0, (double)scene.width, 0.0, (double)scene.height, scene.camera.near, scene.camera.far);
            // model-view
            ctx.mvp.primary.concatenate(scene.forwardTransform);
        }
    }

    // if the projection is planar, may need to handle IDL crossings
    bool handleIdlCrossing = (scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE);
    if(handleIdlCrossing) {
#define sgn(a) ((a) < 0.0 ? -1.0 : 1.0)
        // construct segment in screen space along the IDL
        Point2<double> idlNxy;
        Point2<double> idlSxy;
        scene.forward(&idlNxy, GeoPoint2(90.0, sgn(focus.longitude) * 180.0));
        scene.forward(&idlSxy, GeoPoint2(-90.0, sgn(focus.longitude) * 180.0));

        // find distance from focus xy to IDL screenspace segment
        //https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
        const double x2 = idlSxy.x;
        const double x1 = idlNxy.x;
        const double y2 = idlSxy.y;
        const double y1 = idlNxy.y;
        const double x0 = scene.width/2.0;
        const double y0 = scene.height/2.0;
        const double test = fabs(((x2-x1)*(y1-y0))-((x1-x0)*(y2-y1))) / sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));

        handleIdlCrossing = test <= Vector2_length(Point2<double>(scene.width/2.0, scene.height/2.0, 0.0));
    }

    if(handleIdlCrossing) {
        for(std::size_t i = 0u; i < TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS; i++)
            ctx.localFrame.secondary[i].set(ctx.localFrame.primary[i]);

        MapSceneModel2 hemi2(scene);
        if(scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) {
            // reconstruct the scene model in the secondary hemisphere
            GeoPoint2 focus2(focus);
            if (focus2.longitude  < 0.0)
                focus2.longitude += 360.0;
            else
                focus2.longitude -= 360.0;

            hemi2.set(
                    hemi2.displayDpi,
                    hemi2.width,
                    hemi2.height,
                    hemi2.projection->getSpatialReferenceID(),
                    focus2,
                    hemi2.focusX,
                    hemi2.focusY,
                    hemi2.camera.azimuth,
                    90.0+hemi2.camera.elevation,
                    hemi2.gsd,
                    hemi2.camera.mode);
            glSceneModel(hemi2);
        }

        // construct the MVP matrix
        Matrix2 mvp;
        // projection
        if (hemi2.camera.mode == MapCamera2::Perspective) {
            ctx.mvp.secondary = hemi2.camera.projection;
            ctx.mvp.secondary.concatenate(hemi2.camera.modelView);
        } else {
            // projection
            setOrtho(&ctx.mvp.secondary, 0.0, (double)hemi2.width, 0.0, (double)hemi2.height, hemi2.camera.near, hemi2.camera.far);
            // model-view
            ctx.mvp.secondary.concatenate(hemi2.forwardTransform);
        }

        ctx.hasSecondary = true;
    }

    glEnableVertexAttribArray(ctx.shader.base.aVertexCoords);
    glEnableVertexAttribArray(ctx.shader.base.aNormals);
    glEnableVertexAttribArray(ctx.shader.aNoDataFlag);
    glEnableVertexAttribArray(ctx.shader.aEcefVertCoords);

    return ctx;
}
void TAK::Engine::Renderer::Elevation::GLTerrainTile_bindTexture(TerrainTileRenderContext &ctx, const GLuint texid, const std::size_t texWidth, const std::size_t texHeight) NOTHROWS
{
    if (ctx.texid != texid) {
        glBindTexture(GL_TEXTURE_2D, texid);
        glUniform1f(ctx.shader.uTexWidth, static_cast<float>(texWidth));
        glUniform1f(ctx.shader.uTexHeight, static_cast<float>(texHeight));
        ctx.texid = texid;
    }
}
void TAK::Engine::Renderer::Elevation::GLTerrainTile_setElevationScale(TerrainTileRenderContext &ctx, const float elevationScale) NOTHROWS
{
    if (ctx.elevationScale != elevationScale) {
        glUniform1f(ctx.shader.uElevationScale, static_cast<float>(elevationScale));
        ctx.elevationScale = elevationScale;
    }
}
void TAK::Engine::Renderer::Elevation::GLTerrainTile_drawTerrainTiles(TerrainTileRenderContext& ctx, const GLGlobeBase::State* states, const std::size_t numStates, const GLTerrainTile* terrainTile, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS
{
    GLTexture2 *tex = nullptr;

    configureLightingUniforms(ctx, ctx.shader);

    // primary first pass
    drawTerrainTilesImpl(states, numStates, ctx.shader, ctx.mvp.primary, ctx.localFrame.primary, ctx.numLocalFrames, *tex, terrainTile, numTiles, r, g, b, a);
    if(ctx.hasSecondary)
        drawTerrainTilesImpl(states, numStates, ctx.shader, ctx.mvp.secondary, ctx.localFrame.secondary, ctx.numLocalFrames, *tex, terrainTile, numTiles, r, g, b, a);
}
void TAK::Engine::Renderer::Elevation::GLTerrainTile_drawTerrainTiles(TerrainTileRenderContext& ctx, const Matrix2& lla2tex, const GLTerrainTile* terrainTiles, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS
{
    configureLightingUniforms(ctx, ctx.shader);

    // draw terrain tiles
    for (std::size_t idx = 0u; idx < numTiles; idx++) {
        auto tile = terrainTiles[idx];
        drawTerrainTileImpl(lla2tex, ctx.shader, ctx.mvp.primary, ctx.localFrame.primary, ctx.numLocalFrames, tile, a == 1.f, r, g, b, a);
        if(ctx.hasSecondary)
            drawTerrainTileImpl(lla2tex, ctx.shader, ctx.mvp.secondary, ctx.localFrame.secondary, ctx.numLocalFrames, tile, a == 1.f, r, g, b, a);
    }
}

void TAK::Engine::Renderer::Elevation::GLTerrainTile_end(TerrainTileRenderContext &ctx) NOTHROWS
{
    glDisableVertexAttribArray(ctx.shader.base.aVertexCoords);
    glDisableVertexAttribArray(ctx.shader.base.aNormals);
    glDisableVertexAttribArray(ctx.shader.aNoDataFlag);
    glDisableVertexAttribArray(ctx.shader.aEcefVertCoords);
    glUseProgram(0);
}

TerrainTileShaders TAK::Engine::Renderer::Elevation::GLTerrainTile_getColorShader(const TAK::Engine::Core::RenderContext &ctx, const int srid) NOTHROWS
{
    return GLTerrainTile_getColorShader(ctx, srid, 0u);
}
TerrainTileShaders TAK::Engine::Renderer::Elevation::GLTerrainTile_getColorShader(const TAK::Engine::Core::RenderContext &ctx, const int srid, const unsigned int options) NOTHROWS
{
    static Mutex m;
    Lock lock(m);
    const int64_t key = (((int64_t)srid)<<32LL)|((int64_t)options);
    auto entry = colorShaders[&ctx].find(key);
    if(entry != colorShaders[&ctx].end())
        return entry->second;

    TerrainTileShaders shaders;
    shaders.hi.base.handle = GL_NONE;
    shaders.md.base.handle = GL_NONE;
    shaders.lo.base.handle = GL_NONE;

    std::string fragShaderSrc = COLOR_FRAG_SHADER_SRC;
    if(options == TECSO_Lighting)
        fragShaderSrc += LIGHTING_EFFECT_SRC;
    else
        fragShaderSrc += LIGHTING_EFFECT_EMPTY;

    switch (srid) {
        case 4978 :
        {
            createTerrainTileShaders(&shaders,
                                     COLOR_ECEF_VERT_SHADER_SRC.c_str(),
                                     fragShaderSrc.c_str());
            shaders.hi_threshold = 0.0;
            shaders.md_threshold = 0.0;
            colorShaders[&ctx][key] = shaders;
            break;
        }
        case 4326 :
        {
            createTerrainTileShaders(&shaders,
                                   COLOR_PLANAR_VERT_SHADER_SRC.c_str(),
                                   fragShaderSrc.c_str());
            shaders.hi_threshold = 0.0;
            shaders.md_threshold = 0.0;
            colorShaders[&ctx][key] = shaders;
            break;
        }
        default :
        {
            break;
        }
    }
    return shaders;
}
TerrainTileShaders TAK::Engine::Renderer::Elevation::GLTerrainTile_getDepthShader(const TAK::Engine::Core::RenderContext &ctx, const int srid) NOTHROWS
{
    static Mutex m;
    Lock lock(m);
    auto entry = depthShaders[&ctx].find(srid);
    if(entry != depthShaders[&ctx].end())
        return entry->second;

    TerrainTileShaders shaders;
    shaders.hi.base.handle = GL_NONE;
    shaders.md.base.handle = GL_NONE;
    shaders.lo.base.handle = GL_NONE;

    switch (srid) {
        case 4978 :
        {
            createTerrainTileShaders(&shaders,
                    DEPTH_ECEF_VERT_SHADER_SRC.c_str(),
                    DEPTH_FRAG_SHADER_SRC.c_str());

            shaders.hi_threshold = 0.0;
            shaders.md_threshold = 0.0;
            depthShaders[&ctx][srid] = shaders;
            break;
        }
        case 4326 :
        {
            createTerrainTileShaders(&shaders,
                    DEPTH_PLANAR_VERT_SHADER_SRC.c_str(),
                    DEPTH_FRAG_SHADER_SRC.c_str());
            shaders.hi_threshold = 0.0;
            shaders.md_threshold = 0.0;
            depthShaders[&ctx][srid] = shaders;
            break;
        }
        default :
        {
            break;
        }
    }
    return shaders;
}

namespace
{
    void setOrtho(Matrix2 *value, const double left, const double right, const double bottom, const double top, const double zNear, const double zFar) NOTHROWS
    {
        float mxf[16u];
        atakmap::renderer::GLMatrix::orthoM(mxf,
            static_cast<float>(left), static_cast<float>(right),
            static_cast<float>(bottom), static_cast<float>(top),
            static_cast<float>(zNear), static_cast<float>(zFar));
        for(std::size_t i = 0u; i < 16u; i++)
            value->set(i%4, i/4, mxf[i]);
    }

    void drawTerrainTilesImpl(const GLGlobeBase::State *renderPasses, const std::size_t numRenderPasses, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTexture2 &ignored0, const GLTerrainTile *terrainTiles, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS
    {
        // draw terrain tiles
        for (std::size_t idx = 0u; idx < numTiles; idx++) {
            auto tile = terrainTiles[idx];
            for (std::size_t i = numRenderPasses; i > 0; i--) {
                if (renderPasses[i - 1u].texture) {
                    const GLGlobeBase::State &s = renderPasses[i - 1u];
                    const bool swapHemi = s.crossesIDL &&
                        ((s.drawLng < 0.0 && (tile.tile->aabb_wgs84.minX + tile.tile->aabb_wgs84.maxX) / 2.0 > 0.0) ||
                         (s.drawLng > 0.0 && (tile.tile->aabb_wgs84.minX + tile.tile->aabb_wgs84.maxX) / 2.0 < 0.0));

                    // construct the LLA -> texture transform
                    Matrix2 lla2tex;
                    lla2tex.translate(s.viewport.x, s.viewport.y, 0.0);
                    lla2tex.concatenate(s.scene.forwardTransform);
                    if(swapHemi)
                        lla2tex.translate(s.drawLng > 0.0 ? 360.0 : -360.0, 0.0, 0.0);
                    drawTerrainTileImpl(lla2tex, shader, mvp, local, numLocal, tile, a == 1.f, r, g, b, a);
                }
            }
        }
    }

    void glUniformMatrix4(GLint location, const Matrix2 &matrix, const Matrix2::MatrixOrder order) NOTHROWS
    {
        double matrixD[16];
        float matrixF[16];
        matrix.get(matrixD, order);
        for (std::size_t i = 0u; i < 16u; i++)
            matrixF[i] = (float)matrixD[i];
        glUniformMatrix4fv(location, 1, false, matrixF);
    }

    void glUniformMatrix4v(GLint location, const Matrix2 *matrix, const std::size_t count) NOTHROWS
    {
#define MAX_UNIFORM_MATRICES 16u
        double matrixD[MAX_UNIFORM_MATRICES*16];
        float matrixF[MAX_UNIFORM_MATRICES*16];

        const std::size_t limit = std::min(count, (std::size_t)MAX_UNIFORM_MATRICES);
        if(limit < count)
            Logger_log(TELL_Warning, "Max uniform matrices exceeded, %u", (unsigned)count);

        for (std::size_t i = 0u; i < limit; i++)
            matrix[i].get(matrixD+(i*16u), Matrix2::COLUMN_MAJOR);
        for (std::size_t i = 0u; i < (limit*16u); i++)
            matrixF[i] = (float)matrixD[i];

        glUniformMatrix4fv(location, static_cast<GLsizei>(limit), false, matrixF);
    }

    void drawTerrainTileImpl(const Matrix2 &lla2tex, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &gltile, const bool drawSkirt, const float r, const float g, const float b, const float a) NOTHROWS
    {
        if(!gltile.tile)
            return;

        TAKErr code(TE_Ok);
        const TerrainTile &tile = *gltile.tile;

        int drawMode;
        switch (tile.data.value->getDrawMode()) {
            case TEDM_Triangles:
                drawMode = GL_TRIANGLES;
                break;
            case TEDM_TriangleStrip:
                drawMode = GL_TRIANGLE_STRIP;
                break;
            default:
                Logger_log(TELL_Warning, "GLMapView2: Undefined terrain model draw mode");
                return;
        }

        // set the local frame
        Matrix2 matrix;

        matrix.set(mvp);
        for(std::size_t i = numLocal; i >= 1; i--)
            matrix.concatenate(local[i-1u]);
        // transform LCS to WCS
        if(shader.aEcefVertCoords > 0)
            matrix.concatenate(tile.data_proj.localFrame);
        else
            matrix.concatenate(tile.data.localFrame);

        glUniformMatrix4(shader.base.uMVP, matrix);

        Matrix2 normalMatrix(matrix);
        matrix.createInverse(&normalMatrix);
        glUniformMatrix4(shader.base.uNormalMatrix, normalMatrix, Matrix2::ROW_MAJOR);

        // set the local frame for the offscreen texture
        matrix.set(lla2tex);
        // offscreen is in LLA, so we only need to convert the tile vertices from the LCS to WCS
        matrix.concatenate(tile.data.localFrame);

        glUniformMatrix4(shader.uModelViewOffscreen, matrix);

        glUniform4f(shader.base.uColor, r, g, b, a);

        const bool hasWinding = (tile.data.value->getFaceWindingOrder() != TEWO_Undefined);
        if (hasWinding) {
            glEnable(GL_CULL_FACE);
            switch (tile.data.value->getFaceWindingOrder()) {
                case TEWO_Clockwise:
                    glFrontFace(GL_CW);
                    break;
                case TEWO_CounterClockwise:
                    glFrontFace(GL_CCW);
                    break;
                default:
                    Logger_log(TELL_Error, "GLMapView2::drawTerrainTile : undefined winding order %d", tile.data.value->getFaceWindingOrder());
                    return;
            }
            glCullFace(GL_BACK);
        }

        // render offscreen texture
        const VertexDataLayout &layout = tile.data.value->getVertexDataLayout();

        const uint8_t *base;
        GLuint vbo = GL_NONE;

        if(gltile.vbo) {
            base = (const uint8_t*)0;
            vbo = gltile.vbo;
        } else {
            const void *vertexCoords;
            code = tile.data.value->getVertices(&vertexCoords, TEVA_Position);
            if (code != TE_Ok) {
                Logger_log(TELL_Error, "GLMapView2::drawTerrainTile : failed to obtain vertex coords, code=%d", code);
                return;
            }
            base = static_cast<const uint8_t *>(vertexCoords);
        }

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), (const void *)(base + layout.position.offset));
        const GLenum normalType = glType(layout.normal.type);
        if((layout.attributes&TEVA_Normal) && normalType)
            glVertexAttribPointer(shader.base.aNormals, 3u, normalType, !DataType_isFloating(layout.normal.type), static_cast<GLsizei>(layout.normal.stride), (const void *)(base + layout.normal.offset));
        else
            glVertexAttrib3f(shader.base.aNormals, 0.f, 0.f, 1.f);
        glVertexAttrib1f(shader.aNoDataFlag, 1.f); // default the no data flag
        if (tile.noDataAttr && (layout.attributes&tile.noDataAttr)) {
            VertexArray noDataArr;
            if (VertexDataLayout_getVertexArray(&noDataArr, layout, tile.noDataAttr) == TE_Ok) {
                const GLenum noDataFlagType = glType(noDataArr.type);
                if (noDataFlagType)
                    glVertexAttribPointer(shader.aNoDataFlag, 1u, noDataFlagType, !DataType_isFloating(noDataArr.type), static_cast<GLsizei>(noDataArr.stride), (const void*)(base + noDataArr.offset));
            }
        }
        if (tile.ecefAttr && (layout.attributes & tile.ecefAttr)) {
            VertexArray ecefAttr;
            if (VertexDataLayout_getVertexArray(&ecefAttr, layout, tile.ecefAttr) == TE_Ok) {
                const GLenum ecefFlagType = glType(ecefAttr.type);
                if (ecefFlagType)
                    glVertexAttribPointer(shader.aEcefVertCoords, 3u, ecefFlagType, !DataType_isFloating(ecefAttr.type), static_cast<GLsizei>(ecefAttr.stride), (const void*)(base + ecefAttr.offset));
            }
        }
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);

        if (tile.data.value->isIndexed()) {
            DataType indexType;
            tile.data.value->getIndexType(&indexType);
            int glIndexType;
            switch(indexType) {
                case TEDT_UInt8 :
                    glIndexType = GL_UNSIGNED_BYTE;
                    break;
                case TEDT_UInt16 :
                    glIndexType = GL_UNSIGNED_SHORT;
                    break;
                case TEDT_UInt32 :
                    glIndexType = GL_UNSIGNED_INT;
                    break;
                default :
                    Logger_log(TELL_Error, "GLMapView2::drawTerrainTile : index type not supported by GL %d", indexType);
                    return;
            }

            std::size_t numIndices = drawSkirt ? tile.data.value->getNumIndices() : tile.skirtIndexOffset;
            if(gltile.ibo) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gltile.ibo);
                glDrawElements(drawMode, static_cast<GLsizei>(numIndices), glIndexType, (void *)tile.data.value->getIndexOffset());
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);
            } else {
                glDrawElements(drawMode, static_cast<GLsizei>(numIndices), glIndexType, static_cast<const uint8_t *>(tile.data.value->getIndices()) + tile.data.value->getIndexOffset());
            }
        } else {
            glDrawArrays(drawMode, 0u, static_cast<GLsizei>(tile.data.value->getNumVertices()));
        }

        if (hasWinding)
            glDisable(GL_CULL_FACE);
    }
    void glSceneModel(MapSceneModel2 &scene) NOTHROWS
    {
            const std::size_t vflipHeight = scene.height;
            Matrix2 verticalFlipTranslate;
            verticalFlipTranslate.setToTranslate(0, static_cast<double>(vflipHeight));
            const Matrix2 xformVerticalFlipScale(
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);

            // account for flipping of y-axis for OpenGL coordinate space
            scene.inverseTransform.concatenate(verticalFlipTranslate);
            scene.inverseTransform.concatenate(xformVerticalFlipScale);

            scene.forwardTransform.preConcatenate(xformVerticalFlipScale);
            scene.forwardTransform.preConcatenate(verticalFlipTranslate);
    }
    TAKErr createTerrainTileShader(TerrainTileShader *value, const char *vertShaderSrc, const char *fragShaderSrc) NOTHROWS
    {
        TAKErr code(TE_Ok);
        ShaderProgram program;

        code = GLSLUtil_createProgram(&program, vertShaderSrc, fragShaderSrc);
        TE_CHECKRETURN_CODE(code);

        value->base.handle = program.program;
        glUseProgram(value->base.handle);
        // vertex shader handles
        value->base.uMVP = glGetUniformLocation(value->base.handle, "uMVP");
        value->base.uNormalMatrix = glGetUniformLocation(value->base.handle, "uNormalMatrix");
        value->uModelViewOffscreen = glGetUniformLocation(value->base.handle, "uModelViewOffscreen");
        value->uTexWidth = glGetUniformLocation(value->base.handle, "uTexWidth");
        value->uTexHeight = glGetUniformLocation(value->base.handle, "uTexHeight");
        value->uElevationScale = glGetUniformLocation(value->base.handle, "uElevationScale");
        value->uLocalTransform = glGetUniformLocation(value->base.handle, "uLocalTransform");
        value->base.aVertexCoords = glGetAttribLocation(value->base.handle, "aVertexCoords");
        value->base.aNormals = glGetAttribLocation(value->base.handle, "aNormals");
        value->aNoDataFlag = glGetAttribLocation(value->base.handle, "aNoDataFlag");
        value->aEcefVertCoords = glGetAttribLocation(value->base.handle, "aEcefVertCoords");
        // fragment shader handles
        value->base.uTexture = glGetUniformLocation(value->base.handle, "uTexture");
        value->base.uColor = glGetUniformLocation(value->base.handle, "uColor");
        value->uMinAmbientLight = glGetUniformLocation(value->base.handle, "uMinAmbientLight");
        value->uLightSourceNormal = glGetUniformLocation(value->base.handle, "uLightSourceNormal");
        value->uLightSourceContribution = glGetUniformLocation(value->base.handle, "uLightSourceContribution");

        return code;
    }
    TAKErr createTerrainTileShaders(TerrainTileShaders *value,
                                  const char *vertShaderSrc,
                                  const char *fragShaderSrc) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(vertShaderSrc) {
            code = createTerrainTileShader(&value->hi, vertShaderSrc, fragShaderSrc);
            TE_CHECKRETURN_CODE(code);
        }
        
        value->md = value->hi;
        value->lo = value->hi;

        return code;
    }
    GLenum glType(const DataType tedt) NOTHROWS
    {
        switch(tedt) {
            case TEDT_UInt8 :   return GL_UNSIGNED_BYTE;
            case TEDT_Int8 :    return GL_BYTE;
            case TEDT_UInt16 :  return GL_UNSIGNED_SHORT;
            case TEDT_Int16 :   return GL_SHORT;
            case TEDT_UInt32 :  return GL_UNSIGNED_INT;
            case TEDT_Int32 :   return GL_INT;
            case TEDT_Float32 : return GL_FLOAT;
            default :           return GL_NONE;
        }
    }
    void configureLightingUniforms(const TerrainTileRenderContext &ctx, const TerrainTileShader &shader) NOTHROWS
    {
        float lightSourceContributions[TE_GLTERRAINTILE_MAX_LIGHT_SOURCES];
        float lightSourceNormals[TE_GLTERRAINTILE_MAX_LIGHT_SOURCES*3u];

        std::size_t numLightSources = std::min(ctx.numLightSources, (std::size_t)TE_GLTERRAINTILE_MAX_LIGHT_SOURCES);
        if(ctx.numLightSources) {
            for(int i = 0u; i < numLightSources; i++) {
                // initialize light position at horizon, due north
                Point2<double> position(0.0, 1.0, 0.0);
                Matrix2 mx;
                // apply azimuth
                mx.rotate(-ctx.lightSources[i].azimuth/180.0*M_PI, 0.0, 0.0, 1.0);
                // apply altitude
                mx.rotate(ctx.lightSources[i].altitude/180.0*M_PI, 1.0, 0.0, 0.0);

                // compute the light source direction
                mx.transform(&position, position);

                // set the light source
                lightSourceContributions[i] = ctx.lightSources[i].intensity;
                lightSourceNormals[i*3u] = (float)position.x;
                lightSourceNormals[i*3u+1u] = (float)position.y;
                lightSourceNormals[i*3u+2u] = (float)position.z;
            }
        } else {
            // set the default light source as nadir, full intensity
                lightSourceContributions[0u] = 1.f;
                lightSourceNormals[0u] = 0.f;
                lightSourceNormals[1u] = 0.f;
                lightSourceNormals[2u] = 1.f;

            numLightSources++;
        }

        const std::size_t numDisabledLights = TE_GLTERRAINTILE_MAX_LIGHT_SOURCES - numLightSources;
        for(std::size_t i = 0u; i < numDisabledLights; i++) {
            const std::size_t lightIdx = (numLightSources+i);
            lightSourceContributions[lightIdx] = 0.f;
            lightSourceNormals[lightIdx*3u] = 0.f;
            lightSourceNormals[lightIdx*3u+1u] = 0.f;
            lightSourceNormals[lightIdx*3u+2u] = 1.f;
        }

        glUniform1fv(shader.uLightSourceContribution, TE_GLTERRAINTILE_MAX_LIGHT_SOURCES, lightSourceContributions);
        glUniform3fv(shader.uLightSourceNormal, TE_GLTERRAINTILE_MAX_LIGHT_SOURCES, lightSourceNormals);

        // XXX - set this to zero or some other smaller value when lighting is specified???
        glUniform1f(shader.uMinAmbientLight, ctx.numLightSources ? 0.15f : 0.25f);
    }
}
