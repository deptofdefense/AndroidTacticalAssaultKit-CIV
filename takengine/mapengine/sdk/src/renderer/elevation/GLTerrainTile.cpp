#include "renderer/elevation/GLTerrainTile.h"

#include "math/Ellipsoid2.h"
#include "renderer/GLMatrix.h"
#include "renderer/GLSLUtil.h"
#include "thread/Mutex.h"

#include <algorithm>

using namespace TAK::Engine::Renderer::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#if 0
#define LLA2ECEF_FN_SRC \
    "vec3 lla2ecef(in vec3 lla) {\n" \
    "   float a = 6228.6494140625;\n" \
    "   float b = 6207.76593188006;\n" \
    "   float latRad = radians(lla.y);\n" \
    "   float cosLat = cos(latRad);\n" \
    "   float sinLat = sin(latRad);\n" \
    "   float lonRad = radians(lla.x);\n" \
    "   float cosLon = cos(lonRad);\n" \
    "   float sinLon = sin(lonRad);\n" \
    "   float a2_b2 = (a*a) / (b*b);\n" \
    "   float b2_a2 = (b*b) / (a*a);\n" \
    "   float cden = sqrt((cosLat*cosLat) + (b2_a2 * (sinLat*sinLat)));\n" \
    "   float lden = sqrt((a2_b2 * (cosLat*cosLat)) + (sinLat*sinLat));\n" \
    "   float X = ((a / cden * 1024.0) + lla.z) * (cosLat*cosLon);\n" \
    "   float Y = ((a / cden * 1024.0) + lla.z) * (cosLat*sinLon);\n" \
    "   float Z = ((b / lden * 1024.0) + lla.z) * sinLat;\n" \
    "   return vec3(X, Y, Z);\n" \
    "}\n"
#else
#define LLA2ECEF_FN_SRC \
    "const float radiusEquator = 6378137.0;\n" \
    "const float radiusPolar = 6356752.3142;\n" \
    "vec3 lla2ecef(in vec3 llh) {\n" \
    "  float flattening = (radiusEquator - radiusPolar)/radiusEquator;\n" \
    "   float eccentricitySquared = 2.0 * flattening - flattening * flattening;\n" \
    "   float sin_latitude = sin(radians(llh.y));\n" \
    "   float cos_latitude = cos(radians(llh.y));\n" \
    "   float sin_longitude = sin(radians(llh.x));\n" \
    "   float cos_longitude = cos(radians(llh.x));\n" \
    "   float N = radiusEquator / sqrt(1.0 - eccentricitySquared * sin_latitude * sin_latitude);\n" \
    "   float x = (N + llh.z) * cos_latitude * cos_longitude;\n" \
    "   float y = (N + llh.z) * cos_latitude * sin_longitude; \n" \
    "   float z = (N * (1.0 - eccentricitySquared) + llh.z) * sin_latitude;\n" \
    "   return vec3(x, y, z);\n" \
    "}\n"
#endif
#define OFFSCREEN_ECEF_VERT_LO_SHADER_SRC \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "uniform mat4 uModelViewOffscreen;\n" \
    "uniform float uTexWidth;\n" \
    "uniform float uTexHeight;\n" \
    "uniform float uElevationScale;\n" \
    "attribute vec3 aVertexCoords;\n" \
    "varying vec2 vTexPos;\n" \
    LLA2ECEF_FN_SRC \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);\n" \
    "  lla = lla / lla.w;\n" \
    "  vec3 ecef = lla2ecef(vec3(lla.xy, lla.z*uElevationScale));\n" \
    "  vec4 offscreenPos = uModelViewOffscreen * vec4(lla.xy, 0.0, 1.0);\n" \
    "  offscreenPos.x = offscreenPos.x / offscreenPos.w;\n" \
    "  offscreenPos.y = offscreenPos.y / offscreenPos.w;\n" \
    "  offscreenPos.z = offscreenPos.z / offscreenPos.w;\n" \
    "  vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);\n" \
    "  vTexPos = texPos.xy;\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "}"

#define OFFSCREEN_ECEF_VERT_MD_SHADER_SRC \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "uniform mat4 uModelViewOffscreen;\n" \
    "uniform float uTexWidth;\n" \
    "uniform float uTexHeight;\n" \
    "uniform float uElevationScale;\n" \
    "attribute vec3 aVertexCoords;\n" \
    "varying vec2 vTexPos;\n" \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords.xyz, 1.0);\n" \
    "  lla /= lla.w;\n" \
    "  vec4 llaLocal = uLocalTransform[1] * lla;\n" \
    "  vec4 lla2ecef_in = vec4(llaLocal.xy, llaLocal.x*llaLocal.y, 1.0);\n" \
    "  lla2ecef_in /= lla2ecef_in.w;\n" \
    "  vec4 ecefSurface = uLocalTransform[2] * lla2ecef_in;\n" \
    "  ecefSurface /= ecefSurface.w;\n" \
    "  vec3 ecef = vec3(ecefSurface.xy * (1.0 + llaLocal.z / 6378137.0), ecefSurface.z * (1.0 + llaLocal.z / 6356752.3142));\n" \
    "  vec4 offscreenPos = uModelViewOffscreen * vec4(lla.xy, 0.0, 1.0);\n" \
    "  offscreenPos.x = offscreenPos.x / offscreenPos.w;\n" \
    "  offscreenPos.y = offscreenPos.y / offscreenPos.w;\n" \
    "  offscreenPos.z = offscreenPos.z / offscreenPos.w;\n" \
    "  vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);\n" \
    "  vTexPos = texPos.xy;\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "}"

#define OFFSCREEN_PLANAR_VERT_SHADER_SRC \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uModelViewOffscreen;\n" \
    "uniform float uTexWidth;\n" \
    "uniform float uTexHeight;\n" \
    "uniform float uElevationScale;\n" \
    "attribute vec3 aVertexCoords;\n" \
    "varying vec2 vTexPos;\n" \
    "void main() {\n" \
    "  vec4 offscreenPos = uModelViewOffscreen * vec4(aVertexCoords.xy, 0.0, 1.0);\n" \
    "  offscreenPos.x = offscreenPos.x / offscreenPos.w;\n" \
    "  offscreenPos.y = offscreenPos.y / offscreenPos.w;\n" \
    "  offscreenPos.z = offscreenPos.z / offscreenPos.w;\n" \
    "  vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);\n" \
    "  vTexPos = texPos.xy;\n" \
    "  gl_Position = uMVP * vec4(aVertexCoords.xy, aVertexCoords.z*uElevationScale, 1.0);\n" \
    "}"

#if 0
// XXX - experimenting with distance fog
#define OFFSCREEN_FRAG_SHADER_SRC \
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
#define OFFSCREEN_FRAG_SHADER_SRC \
    "precision mediump float;\n" \
    "uniform sampler2D uTexture;\n" \
    "uniform vec4 uColor;\n" \
    "varying vec2 vTexPos;\n" \
    "void main(void) {\n" \
    "  gl_FragColor = texture2D(uTexture, vTexPos)*uColor;\n"\
    "}"
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

#define DEPTH_ECEF_VERT_LO_SHADER_SRC \
    "#version 300 es\n" \
    "precision highp float;\n" \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "uniform float uElevationScale;\n" \
    "in vec3 aVertexCoords;\n" \
    "out float vDepth;\n" \
    LLA2ECEF_FN_SRC \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);\n" \
    "  lla = lla / lla.w;\n" \
    "  vec3 ecef = lla2ecef(vec3(lla.xy, lla.z*uElevationScale));\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "  vDepth = (gl_Position.z + 1.0) * 0.5;" \
    "}"

#define DEPTH_ECEF_VERT_MD_SHADER_SRC \
    "#version 300 es\n" \
    "precision highp float;\n" \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "uniform float uElevationScale;\n" \
    "in vec3 aVertexCoords;\n" \
    "out float vDepth;\n" \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords.xyz, 1.0);\n" \
    "  lla /= lla.w;\n" \
    "  vec4 llaLocal = uLocalTransform[1] * lla;\n" \
    "  vec4 lla2ecef_in = vec4(llaLocal.xy, llaLocal.x*llaLocal.y, 1.0);\n" \
    "  lla2ecef_in /= lla2ecef_in.w;\n" \
    "  vec4 ecefSurface = uLocalTransform[2] * lla2ecef_in;\n" \
    "  ecefSurface /= ecefSurface.w;\n" \
    "  vec3 ecef = vec3(ecefSurface.xy * (1.0 + llaLocal.z / 6378137.0), ecefSurface.z * (1.0 + llaLocal.z / 6356752.3142));\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "  vDepth = (gl_Position.z + 1.0) * 0.5;" \
    "}"

#define DEPTH_PLANAR_VERT_SHADER_SRC \
    "#version 300 es\n" \
    "precision highp float;\n" \
    "uniform mat4 uMVP;\n" \
    "uniform float uElevationScale;\n" \
    "in vec3 aVertexCoords;\n" \
    "out float vDepth;\n" \
    "void main() {\n" \
    "  gl_Position = uMVP * vec4(aVertexCoords.xy, aVertexCoords.z*uElevationScale, 1.0);\n" \
    "  vDepth = (gl_Position.z + 1.0) * 0.5;" \
    "}"
#define DEPTH_FRAG_SHADER_SRC \
    "#version 300 es\n" \
    "precision mediump float;\n" \
    "in float vDepth;\n" \
    "out vec4 vFragColor;\n" \
    PACK_DEPTH_FN_SRC \
    "void main(void) {\n" \
    "  vFragColor = PackDepth(vDepth);\n" \
    "}"

namespace
{
    TAKErr lla2ecef_transform(Matrix2 *value, const Projection2 &ecef, const Matrix2 *localFrame) NOTHROWS;
    void setOrtho(Matrix2 *value, const double left, const double right, const double bottom, const double top, const double zNear, const double zFar) NOTHROWS;
    void drawTerrainTilesImpl(const GLMapView2::State *renderPasses, const std::size_t numRenderPasses, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTexture2 &texture, const GLTerrainTile *terrainTiles, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS;
    void glUniformMatrix4(GLint location, const Matrix2 &matrix) NOTHROWS;
    void glUniformMatrix4v(GLint location, const Matrix2 *matrix, const std::size_t count) NOTHROWS;
    void drawTerrainTileImpl(const Matrix2 &lla2tex, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &gltile, const bool drawSkirt, const float r, const float g, const float b, const float a) NOTHROWS;
    TAKErr validateSceneModel(GLMapView2::State *view, const std::size_t width, const std::size_t height) NOTHROWS;
    TAKErr createTerrainTileShader(TerrainTileShader *value, const char *vertShaderSrc, const char *fragShaderSrc) NOTHROWS;
    TAKErr createTerrainTileShaders(TerrainTileShaders *value,
                                  const char *hiVertShaderSrc, const char *mdVertShaderSrc, const char *loVertShaderSrc,
                                  const char *fragShaderSrc) NOTHROWS;

    std::map<const RenderContext *, std::map<int, TerrainTileShaders>> colorShaders;

}

void TAK::Engine::Renderer::Elevation::GLTerrainTile_drawTerrainTiles(const TAK::Engine::Renderer::Core::GLMapView2::State *states, const std::size_t numStates, const GLTerrainTile *terrainTiles, const std::size_t numTiles, const TerrainTileShaders &shaders, const GLTexture2 &tex, const float elevationScale, const float r, const float g, const float b, const float a) NOTHROWS
{
    if(!numStates)
        return; // nothing to draw

    const TAK::Engine::Renderer::Core::GLMapView2::State &view = states[0];

    TerrainTileRenderContext ctx = GLTerrainTile_begin(view, shaders);
    GLTerrainTile_setElevationScale(ctx, elevationScale);
    GLTerrainTile_bindTexture(ctx, tex.getTexId(), tex.getTexWidth(), tex.getTexHeight());
    GLTerrainTile_drawTerrainTiles(ctx, states, numStates, terrainTiles, numTiles, r, g, b, a);
    GLTerrainTile_end(ctx);
}

TerrainTileRenderContext TAK::Engine::Renderer::Elevation::GLTerrainTile_begin(const TAK::Engine::Renderer::Core::GLMapView2::State &view, const TerrainTileShaders &shaders) NOTHROWS
{
    TerrainTileRenderContext ctx;

    // select shader
    if(view.scene.displayModel->earth->getGeomClass() == TAK::Engine::Math::GeometryModel2::ELLIPSOID) {
        if(view.drawMapResolution <= shaders.hi_threshold) {
            Matrix2 tx;
            tx.setToTranslate(view.drawLng, view.drawLat, 0.0);
            lla2ecef_transform(&ctx.localFrame.primary[0], *view.scene.projection, &tx);
            ctx.localFrame.primary[0].translate(-view.drawLng, -view.drawLat, 0.0);
            ctx.numLocalFrames++;
        } else if (view.drawMapResolution <= shaders.md_threshold) {
            const auto &ellipsoid = static_cast<const Ellipsoid2 &>(*view.scene.displayModel->earth);

            const double a = ellipsoid.radiusX;
            const double b = ellipsoid.radiusZ;

            const double cosLat0d = cos(view.drawLat*M_PI/180.0);
            const double cosLng0d = cos(view.drawLng*M_PI/180.0);
            const double sinLat0d = sin(view.drawLat*M_PI/180.0);
            const double sinLng0d = sin(view.drawLng*M_PI/180.0);

            const double a2_b2 = (a*a)/(b*b);
            const double b2_a2 = (b*b)/(a*a);
            const double cden = sqrt((cosLat0d*cosLat0d) + (b2_a2 * (sinLat0d*sinLat0d)));
            const double lden = sqrt((a2_b2 * (cosLat0d*cosLat0d)) + (sinLat0d*sinLat0d));

            // scale by ellipsoid radii
            ctx.localFrame.primary[2].setToScale(a/cden, a/cden, b/lden);
            // calculate coefficients for lat/lon => ECEF conversion, using small angle approximation
            ctx.localFrame.primary[2].concatenate(Matrix2(
                    -cosLat0d*sinLng0d, -cosLng0d*sinLat0d, sinLat0d*sinLng0d, cosLat0d*cosLng0d,
                    cosLat0d*cosLng0d, -sinLat0d*sinLng0d, -sinLat0d*cosLng0d, cosLat0d*sinLng0d,
                    0, cosLat0d, 0, sinLat0d,
                    0, 0, 0, 1
            ));
            // convert degrees to radians
            ctx.localFrame.primary[2].scale(M_PI/180.0, M_PI/180.0, M_PI/180.0*M_PI/180.0);
            ctx.numLocalFrames++;

            // degrees are relative to focus
            ctx.localFrame.primary[1].setToTranslate(-view.drawLng, -view.drawLat, 0);
            ctx.numLocalFrames++;

            // degrees are relative to focus
            ctx.localFrame.primary[0].setToIdentity();
            ctx.numLocalFrames++;
        }
    }

    ctx.shader = (view.drawMapResolution <= shaders.hi_threshold) ?
                                shaders.hi :
                                (view.drawMapResolution <= shaders.md_threshold) ?
                                shaders.md : shaders.lo;

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
        if (view.scene.camera.mode == MapCamera2::Perspective) {
            ctx.mvp.primary = view.scene.camera.projection;
            ctx.mvp.primary.concatenate(view.scene.camera.modelView);
        } else {
            // projection
            setOrtho(&ctx.mvp.primary, view.left, view.right, view.bottom, view.top, view.near, view.far);
            // model-view
            ctx.mvp.primary.concatenate(view.scene.forwardTransform);
        }
    }

#if 0
    if(view.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE && view.crossesIDL) {
        GLMapView2::State hemi2(view);
        // reconstruct the scene model in the secondary hemisphere
        if (view.drawLng  < 0.0)
            hemi2.drawLng += 360.0;
        else
            hemi2.drawLng -= 360.0;

        hemi2.sceneModelVersion = ~hemi2.sceneModelVersion;
        validateSceneModel(&hemi2, hemi2.scene.width, hemi2.scene.height);

        // construct the MVP matrix
        // projection
        setOrtho(&ctx.mvp.secondary, view.left, view.right, view.bottom, view.top, view.near, view.far);
        // model-view
        ctx.mvp.secondary.concatenate(hemi2.scene.forwardTransform);

        ctx.hasSecondary = true;
    }
#else
    if(view.crossesIDL &&
        ((view.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) ||
            (&ctx.shader != &shaders.lo))) {

        GLMapView2::State hemi2(view);
        for(std::size_t i = 0u; i < TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS; i++)
            ctx.localFrame.secondary[i].set(ctx.localFrame.primary[i]);

        if(hemi2.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) {
            // reconstruct the scene model in the secondary hemisphere
            if (hemi2.drawLng  < 0.0)
                hemi2.drawLng += 360.0;
            else
                hemi2.drawLng -= 360.0;

            hemi2.sceneModelVersion = ~hemi2.sceneModelVersion;
            validateSceneModel(&hemi2, hemi2.scene.width, hemi2.scene.height);
        } else if(ctx.shader.base.handle == shaders.hi.base.handle) {
            // reconstruct the scene model in the secondary hemisphere
            if (hemi2.drawLng  < 0.0)
                ctx.localFrame.secondary[0].translate(-360.0, 0.0, 0.0);
            else
                ctx.localFrame.secondary[0].translate(360.0, 0.0, 0.0);
        } else if(ctx.shader.base.handle == shaders.md.base.handle) {
            // reconstruct the scene model in the secondary hemisphere
            if (hemi2.drawLng  < 0.0)
                ctx.localFrame.secondary[1].translate(-360.0, 0.0, 0.0);
            else
                ctx.localFrame.secondary[1].translate(360.0, 0.0, 0.0);
        }

        // construct the MVP matrix
        Matrix2 mvp;
        // projection
        if (hemi2.scene.camera.mode == MapCamera2::Perspective) {
            ctx.mvp.secondary = hemi2.scene.camera.projection;
            ctx.mvp.secondary.concatenate(hemi2.scene.camera.modelView);
        } else {
            // projection
            setOrtho(&ctx.mvp.secondary, hemi2.left, hemi2.right, hemi2.bottom, hemi2.top, hemi2.near, hemi2.far);
            // model-view
            ctx.mvp.secondary.concatenate(hemi2.scene.forwardTransform);
        }

        ctx.hasSecondary = true;
    }
#endif

    glEnableVertexAttribArray(ctx.shader.base.aVertexCoords);

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
        glUniform1f(ctx.shader.uElevationScale, static_cast<float>(ctx.elevationScale));
        ctx.elevationScale = elevationScale;
    }
}
void TAK::Engine::Renderer::Elevation::GLTerrainTile_drawTerrainTiles(TerrainTileRenderContext& ctx, const TAK::Engine::Renderer::Core::GLMapView2::State* states, const std::size_t numStates, const GLTerrainTile* terrainTile, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS
{
    GLTexture2 *tex = nullptr;

    // primary first pass
    drawTerrainTilesImpl(states, numStates, ctx.shader, ctx.mvp.primary, ctx.localFrame.primary, ctx.numLocalFrames, *tex, terrainTile, numTiles, r, g, b, a);
    if(ctx.hasSecondary)
        drawTerrainTilesImpl(states, numStates, ctx.shader, ctx.mvp.secondary, ctx.localFrame.secondary, ctx.numLocalFrames, *tex, terrainTile, numTiles, r, g, b, a);
}
void TAK::Engine::Renderer::Elevation::GLTerrainTile_end(TerrainTileRenderContext &ctx) NOTHROWS
{
    glDisableVertexAttribArray(ctx.shader.base.aVertexCoords);
    glUseProgram(0);
}

TerrainTileShaders TAK::Engine::Renderer::Elevation::GLTerrainTile_getColorShader(const TAK::Engine::Core::RenderContext &ctx, const int srid) NOTHROWS
{
    static Mutex m;
    Lock lock(m);
    auto entry = colorShaders[&ctx].find(srid);
    if(entry != colorShaders[&ctx].end())
        return entry->second;

    TerrainTileShaders shaders;
    shaders.hi.base.handle = GL_NONE;
    shaders.md.base.handle = GL_NONE;
    shaders.lo.base.handle = GL_NONE;

    switch (srid) {
        case 4978 :
        {
            createTerrainTileShaders(&shaders,
                    OFFSCREEN_PLANAR_VERT_SHADER_SRC, OFFSCREEN_ECEF_VERT_MD_SHADER_SRC, OFFSCREEN_ECEF_VERT_LO_SHADER_SRC,
                    OFFSCREEN_FRAG_SHADER_SRC);
            shaders.hi_threshold = 1.5;
            shaders.md_threshold = 100.0;
            colorShaders[&ctx][srid] = shaders;
            break;
        }
        case 4326 :
        {
            createTerrainTileShaders(&shaders,
                                   OFFSCREEN_PLANAR_VERT_SHADER_SRC, OFFSCREEN_PLANAR_VERT_SHADER_SRC, OFFSCREEN_PLANAR_VERT_SHADER_SRC,
                                   OFFSCREEN_FRAG_SHADER_SRC);
            shaders.hi_threshold = 0.0;
            shaders.md_threshold = 0.0;
            colorShaders[&ctx][srid] = shaders;
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
    auto entry = colorShaders[&ctx].find(srid);
    if(entry != colorShaders[&ctx].end())
        return entry->second;

    TerrainTileShaders shaders;
    shaders.hi.base.handle = GL_NONE;
    shaders.md.base.handle = GL_NONE;
    shaders.lo.base.handle = GL_NONE;

    switch (srid) {
        case 4978 :
        {
            createTerrainTileShaders(&shaders,
                    OFFSCREEN_PLANAR_VERT_SHADER_SRC, OFFSCREEN_PLANAR_VERT_SHADER_SRC, OFFSCREEN_PLANAR_VERT_SHADER_SRC,
                    OFFSCREEN_FRAG_SHADER_SRC);
            shaders.hi_threshold = 1.5;
            shaders.md_threshold = 100.0;
            colorShaders[&ctx][srid] = shaders;
            break;
        }
        case 4326 :
        {
            createTerrainTileShaders(&shaders,
                    DEPTH_PLANAR_VERT_SHADER_SRC, DEPTH_PLANAR_VERT_SHADER_SRC, DEPTH_PLANAR_VERT_SHADER_SRC,
                    DEPTH_FRAG_SHADER_SRC);
            shaders.hi_threshold = 0.0;
            shaders.md_threshold = 0.0;
            colorShaders[&ctx][srid] = shaders;
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

    TAKErr lla2ecef_transform(Matrix2 *value, const Projection2 &ecef, const Matrix2 *localFrame) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Matrix2 mx;

        Point2<double> pointD(0.0, 0.0, 0.0);
        GeoPoint2 geo;

        // if draw projection is ECEF and source comes in as LLA, we can
        // transform from LLA to ECEF by creating a local ENU CS and
        // chaining the following conversions (all via matrix)
        // 1. LCS -> LLA
        // 2. LLA -> ENU
        // 3. ENU -> ECEF
        // 4. ECEF -> NDC (via MapSceneModel 'forward' matrix)

        // obtain origin as LLA
        pointD.x = 0;
        pointD.y = 0;
        pointD.z = 0;
        if(localFrame)
            localFrame->transform(&pointD, pointD);
        // transform origin to ECEF
        geo.latitude = pointD.y;
        geo.longitude = pointD.x;
        geo.altitude = pointD.z;
        geo.altitudeRef = TAK::Engine::Core::AltitudeReference::HAE;

        code = ecef.forward(&pointD, geo);
        TE_CHECKRETURN_CODE(code);

        // construct ENU -> ECEF
#define __RADIANS(x) ((x)*M_PI/180.0)
        const double phi = __RADIANS(geo.latitude);
        const double lambda = __RADIANS(geo.longitude);

        mx.translate(pointD.x, pointD.y, pointD.z);

        Matrix2 enu2ecef(
                -sin(lambda), -sin(phi)*cos(lambda), cos(phi)*cos(lambda), 0.0,
                cos(lambda), -sin(phi)*sin(lambda), cos(phi)*sin(lambda), 0.0,
                0, cos(phi), sin(phi), 0.0,
                0.0, 0.0, 0.0, 1.0
        );

        mx.concatenate(enu2ecef);

        // construct LLA -> ENU
        const double metersPerDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(geo.latitude);
        const double metersPerDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(geo.latitude);

        mx.scale(metersPerDegLng, metersPerDegLat, 1.0);

        value->set(mx);

        return code;
    }

    void drawTerrainTilesImpl(const GLMapView2::State *renderPasses, const std::size_t numRenderPasses, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTexture2 &ignored0, const GLTerrainTile *terrainTiles, const std::size_t numTiles, const float r, const float g, const float b, const float a) NOTHROWS
    {
        // draw terrain tiles
        for (std::size_t idx = 0u; idx < numTiles; idx++) {
            auto tile = terrainTiles[idx];
            for (std::size_t i = numRenderPasses; i > 0; i--) {
                if (renderPasses[i - 1u].texture) {
                    const GLMapView2::State &s = renderPasses[i - 1u];
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

    void glUniformMatrix4(GLint location, const Matrix2 &matrix) NOTHROWS
    {
        double matrixD[16];
        float matrixF[16];
        matrix.get(matrixD, Matrix2::COLUMN_MAJOR);
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
        if(shader.uLocalTransform < 0) {
            for(std::size_t i = numLocal; i >= 1; i--)
                matrix.concatenate(local[i-1u]);
            matrix.concatenate(tile.data.localFrame);
        } else {
            Matrix2 mx[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
            for(std::size_t i = numLocal; i >= 1; i--)
                mx[i-1u].set(local[i-1u]);
            mx[0].concatenate(tile.data.localFrame);
            glUniformMatrix4v(shader.uLocalTransform, mx, numLocal ? numLocal : 1u);
        }

        glUniformMatrix4(shader.base.uMVP, matrix);

        // set the local frame for the offscreen texture
        matrix.set(lla2tex);
        if (shader.uLocalTransform < 0) {
            // offscreen is in LLA, so we only need to convert the tile vertices from the LCS to WCS
            matrix.concatenate(tile.data.localFrame);
        }
        glUniformMatrix4(shader.uModelViewOffscreen, matrix);

        glUniform4f(shader.base.uColor, r, g, b, a);
#if 0
        if (depthEnabled) {
#else
        if(true) {
#endif
            glDepthFunc(GL_LEQUAL);
        }
        /*
                    GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
                    GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, this->offscreen->vertexCoords);
                    GLES20FixedPipeline.glColor4f(1f,  1f, 1f, 1f);
                    GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINE_STRIP, this->offscreen->indicesCount, GLES20FixedPipeline.GL_UNSIGNED_SHORT, this->offscreen->indices);
                    GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        */

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

        if(gltile.vbo) {
            // VBO
            glBindBuffer(GL_ARRAY_BUFFER, gltile.vbo);
            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), (void *)layout.position.offset);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        } else {
            const void *vertexCoords;
            code = tile.data.value->getVertices(&vertexCoords, TEVA_Position);
            if (code != TE_Ok) {
                Logger_log(TELL_Error, "GLMapView2::drawTerrainTile : failed to obtain vertex coords, code=%d", code);
                return;
            }

            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), static_cast<const uint8_t *>(vertexCoords) + layout.position.offset);
        }

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
    TAKErr validateSceneModel(GLMapView2::State *view, const std::size_t width, const std::size_t height) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (view->sceneModelVersion != view->drawVersion) {
            const std::size_t vflipHeight = height;
            if (vflipHeight != view->verticalFlipTranslateHeight) {
                view->verticalFlipTranslate.setToTranslate(0, static_cast<double>(vflipHeight));
                view->verticalFlipTranslateHeight = static_cast<int>(vflipHeight);
            }

            view->scene.set(view->scene.displayDpi,
                            width,
                            height,
                            view->drawSrid,
                            GeoPoint2(view->drawLat, view->drawLng),
                            view->focusx,
                            view->focusy,
                            view->drawRotation,
                            view->drawTilt,
                            view->drawMapResolution);

            const Matrix2 xformVerticalFlipScale(
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);

            // account for flipping of y-axis for OpenGL coordinate space
            view->scene.inverseTransform.concatenate(view->verticalFlipTranslate);
            view->scene.inverseTransform.concatenate(xformVerticalFlipScale);

            view->scene.forwardTransform.preConcatenate(xformVerticalFlipScale);
            view->scene.forwardTransform.preConcatenate(view->verticalFlipTranslate);

            {
                // fill the forward matrix for the Model-View
                double matrixD[16];
                view->scene.forwardTransform.get(matrixD, Matrix2::COLUMN_MAJOR);
                for (int i = 0; i < 16; i++)
                    view->sceneModelForwardMatrix[i] = (float)matrixD[i];
            }

            // mark as valid
            view->sceneModelVersion = view->drawVersion;
        }

        return code;
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
        value->uModelViewOffscreen = glGetUniformLocation(value->base.handle, "uModelViewOffscreen");
        value->uTexWidth = glGetUniformLocation(value->base.handle, "uTexWidth");
        value->uTexHeight = glGetUniformLocation(value->base.handle, "uTexHeight");
        value->uElevationScale = glGetUniformLocation(value->base.handle, "uElevationScale");
        value->uLocalTransform = glGetUniformLocation(value->base.handle, "uLocalTransform");
        value->base.aVertexCoords = glGetAttribLocation(value->base.handle, "aVertexCoords");
        // fragment shader handles
        value->base.uTexture = glGetUniformLocation(value->base.handle, "uTexture");
        value->base.uColor = glGetUniformLocation(value->base.handle, "uColor");

        return code;
    }
    TAKErr createTerrainTileShaders(TerrainTileShaders *value,
                                  const char *hiVertShaderSrc, const char *mdVertShaderSrc, const char *loVertShaderSrc,
                                  const char *fragShaderSrc) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(hiVertShaderSrc) {
            code = createTerrainTileShader(&value->hi, hiVertShaderSrc, fragShaderSrc);
            TE_CHECKRETURN_CODE(code);
        }
        if(mdVertShaderSrc) {
            code = createTerrainTileShader(&value->md, mdVertShaderSrc, fragShaderSrc);
            TE_CHECKRETURN_CODE(code);
        }
        if(loVertShaderSrc) {
            code = createTerrainTileShader(&value->lo, loVertShaderSrc, fragShaderSrc);
            TE_CHECKRETURN_CODE(code);
        }

        return code;
    }
}
