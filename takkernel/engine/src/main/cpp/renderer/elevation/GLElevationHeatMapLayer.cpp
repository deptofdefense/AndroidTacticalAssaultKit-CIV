#include "renderer/elevation/GLElevationHeatMapLayer.h"

#include <vector>

#include "core/RenderContext.h"
#include "feature/GeometryTransformer.h"
#include "math/Frustum2.h"
#include "math/Rectangle2.h"
#include "model/Mesh.h"
#include "port/STLVectorAdapter.h"
#include "renderer/GLMatrix.h"
#include "renderer/GLSLUtil.h"
#include "renderer/core/GLGlobe.h"
#include "renderer/core/controls/SurfaceRendererControl.h"
#include "renderer/elevation/GLTerrainTile.h"
#include "renderer/elevation/TerrainRenderService.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Renderer::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Core::Controls;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;


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

#define ECEF_LO_VSH \
    "#version 100\n" \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "attribute vec3 aVertexCoords;\n" \
    "attribute vec3 aNormals;\n" \
    "attribute float aNoDataFlag;\n" \
    "varying float vEl;\n" \
    "varying float vNoDataFlag;\n" \
    LLA2ECEF_FN_SRC \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);\n" \
    "  lla = lla / lla.w;\n" \
    "  vec3 ecef = lla2ecef(vec3(lla.xy, lla.z));\n" \
    "  vEl = lla.z;\n" \
    "  vNoDataFlag = aNoDataFlag;\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "}"

#define ECEF_MD_VSH \
    "#version 100\n" \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "attribute vec3 aVertexCoords;\n" \
    "attribute vec3 aNormals;\n" \
    "attribute float aNoDataFlag;\n" \
    "varying float vEl;\n" \
    "varying float vNoDataFlag;\n" \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords.xyz, 1.0);\n" \
    "  lla /= lla.w;\n" \
    "  vec4 llaLocal = uLocalTransform[1] * lla;\n" \
    "  vec4 lla2ecef_in = vec4(llaLocal.xy, llaLocal.x*llaLocal.y, 1.0);\n" \
    "  lla2ecef_in /= lla2ecef_in.w;\n" \
    "  vec4 ecefSurface = uLocalTransform[2] * lla2ecef_in;\n" \
    "  ecefSurface /= ecefSurface.w;\n" \
    "  vec3 ecef = vec3(ecefSurface.xy * (1.0 + llaLocal.z / 6378137.0), ecefSurface.z * (1.0 + llaLocal.z / 6356752.3142));\n" \
    "  vEl = lla.z;\n" \
    "  vNoDataFlag = aNoDataFlag;\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "}"

#define PLANAR_SHADER_VSH \
    "#version 100\n" \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalFrame;\n" \
    "attribute vec4 aVertexCoords;\n" \
    "attribute vec3 aNormals;\n" \
    "attribute float aNoDataFlag;\n" \
    "varying float vEl;\n" \
    "varying float vNoDataFlag;\n" \
    "void main() {\n" \
    "  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);\n" \
    "  vEl = (uLocalFrame * vec4(aVertexCoords.xyz, 1.0)).z;\n" \
    "  vNoDataFlag = aNoDataFlag;\n" \
    "}"

    // hsv2rgb derived from https://www.laurivan.com/rgb-to-hsv-to-rgb-for-shaders/
#define SHADER_FSH \
    "#version 100\n" \
    "precision mediump float;\n" \
    "uniform float uMinEl;\n" \
    "uniform float uMaxEl;\n" \
    "uniform float uSaturation;\n" \
    "uniform float uValue;\n" \
    "uniform float uAlpha;\n" \
    "varying float vEl;\n" \
    "varying float vNoDataFlag;\n" \
    "vec3 hsv2rgb(vec3 hsv) {\n" \
    "    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);\n" \
    "    vec3 p = abs(fract(hsv.xxx + K.xyz) * 6.0 - K.www);\n" \
    "    return hsv.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), hsv.y);" \
    "}\n" \
    "void main(void) {\n" \
    "  float hue = (1.0 - (clamp(vEl, uMinEl, uMaxEl)-uMinEl)/(uMaxEl-uMinEl)) * 2.0 / 3.0;\n" \
    "  vec3 hsv = vec3(hue, uSaturation, uValue);\n" \
    "  gl_FragColor = vec4(hsv2rgb(hsv).rgb, uAlpha*vNoDataFlag);\n" \
    "}"
//    "  gl_FragColor = vec4(hsv2rgb(hsv).rgb, uAlpha*vNoDataFlag);\n" \

namespace
{
    struct ShaderImpl
    {
        TerrainTileShader base;
        GLint uLocalFrame{ -1 };
        GLint uMinEl{ -1 };
        GLint uMaxEl{ -1 };
        GLint uSaturation{ -1 };
        GLint uValue{ -1 };
        GLint uAlpha{ -1 };
    };

    struct ShadersImpl
    {
        struct
        {
            ShaderImpl lo;
            ShaderImpl md;
            ShaderImpl hi;

            TerrainTileShaders base;
        } ecef;
        struct
        {
            ShaderImpl lo;
            ShaderImpl md;
            ShaderImpl hi;

            TerrainTileShaders base;
        } flat;
    };

    Mutex& mutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }
    std::map<const RenderContext*, ShadersImpl>& shaders() NOTHROWS
    {
        static std::map<const RenderContext*, ShadersImpl> m;
        return m;
    }
    TAKErr createShader(ShaderImpl& s, const char* vsh, const char* fsh) NOTHROWS
    {
        ShaderProgram prog;
        prog.program = GL_NONE;
        prog.vertShader = GL_NONE;
        prog.fragShader = GL_NONE;
        GLSLUtil_createProgram(&prog, vsh, fsh);
        glDeleteShader(prog.vertShader);
        glDeleteShader(prog.fragShader);
        if (!prog.program)
            return TE_Err;
        
        s.base.base.handle = prog.program;
        // vertex shader
        s.base.base.uMVP = glGetUniformLocation(s.base.base.handle, "uMVP");
        s.base.uLocalTransform = glGetUniformLocation(s.base.base.handle, "uLocalTransform");
        s.uLocalFrame = glGetUniformLocation(s.base.base.handle, "uLocalFrame");
        s.base.base.aVertexCoords = glGetAttribLocation(s.base.base.handle, "aVertexCoords");
        s.base.base.aNormals = glGetAttribLocation(s.base.base.handle, "aNormals");
        s.base.aNoDataFlag = glGetAttribLocation(s.base.base.handle, "aNoDataFlag");
        s.uMinEl = glGetUniformLocation(s.base.base.handle, "uMinEl");
        s.uMaxEl = glGetUniformLocation(s.base.base.handle, "uMaxEl");
        s.uSaturation = glGetUniformLocation(s.base.base.handle, "uSaturation");
        s.uValue = glGetUniformLocation(s.base.base.handle, "uValue");
        s.uAlpha = glGetUniformLocation(s.base.base.handle, "uAlpha");
        return TE_Ok;
    }
    TAKErr createShaders(const RenderContext &ctx, ShadersImpl& s) NOTHROWS
    {
        TAKErr code(TE_Ok);

        // copy thresholds
        s.ecef.base = GLTerrainTile_getColorShader(ctx, 4978);

        // create custom shaders
        code = createShader(s.ecef.lo, ECEF_LO_VSH, SHADER_FSH);
        TE_CHECKRETURN_CODE(code);
        s.ecef.base.lo = s.ecef.lo.base;
        createShader(s.ecef.md, ECEF_MD_VSH, SHADER_FSH);
        TE_CHECKRETURN_CODE(code);
        s.ecef.base.md = s.ecef.md.base;
        createShader(s.ecef.hi, PLANAR_SHADER_VSH, SHADER_FSH);
        TE_CHECKRETURN_CODE(code);
        s.ecef.base.hi = s.ecef.hi.base;

        // copy thresholds
        s.flat.base = GLTerrainTile_getColorShader(ctx, 4326);

        // create custom shaders
        code = createShader(s.flat.lo, PLANAR_SHADER_VSH, SHADER_FSH);
        TE_CHECKRETURN_CODE(code);
        s.flat.base.lo = s.flat.lo.base;
        createShader(s.flat.md, PLANAR_SHADER_VSH, SHADER_FSH);
        TE_CHECKRETURN_CODE(code);
        s.flat.base.md = s.flat.md.base;
        createShader(s.flat.hi, PLANAR_SHADER_VSH, SHADER_FSH);
        TE_CHECKRETURN_CODE(code);
        s.flat.base.hi = s.flat.hi.base;

        return code;
    }
    ShadersImpl getShader(const RenderContext& ctx) NOTHROWS
    {
        Lock lock(mutex());
        auto entry = shaders().find(&ctx);
        if (entry != shaders().end())
            return entry->second;

        ShadersImpl s;
        if(createShaders(ctx, s) == TE_Ok)
            shaders()[&ctx] = s;
        return s;
    }
    void glOrtho(Matrix2 &value, float left, float right, float bottom, float top, float near, float far) NOTHROWS
    {
        float mx[16u];
        atakmap::renderer::GLMatrix::orthoM(mx, left, right, bottom, top, near, far);
        for (std::size_t i = 0u; i < 16u; i++)
            value.set(i % 4, i / 4, mx[i]);
    }
    void glUniformMatrix4(GLuint location, const Matrix2 &m) NOTHROWS
    {
        float mx[16u];
        for (std::size_t i = 0u; i < 16u; i++) {
            double v;
            m.get(&v, i % 4, i / 4);
            mx[i] = (float)v;
        }
        glUniformMatrix4fv(location, 1u, false, mx);
    }
    TAKErr adapt(GLenum& gltype, const DataType tedt) NOTHROWS
    {
        switch (tedt) {
        case TEDT_UInt8 :
            gltype = GL_UNSIGNED_BYTE;
            break;
        case TEDT_Int8 :
            gltype = GL_BYTE;
            break;
        case TEDT_UInt16 :
            gltype = GL_UNSIGNED_SHORT;
            break;
        case TEDT_Int16 :
            gltype = GL_SHORT;
            break;
        case TEDT_UInt32 :
            gltype = GL_UNSIGNED_INT;
            break;
        case TEDT_Int32 :
            gltype = GL_INT;
            break;
        case TEDT_Float32:
            gltype = GL_FLOAT;
            break;
        default:
            // type is not supported
            return TE_InvalidArg;
        }
        return TE_Ok;
    }
    TAKErr adapt(GLenum& glmode, const DrawMode tedm) NOTHROWS
    {
        switch (tedm) {
        case TEDM_Triangles :
            glmode = GL_TRIANGLES;
            break;
        case TEDM_TriangleStrip :
            glmode = GL_TRIANGLE_STRIP;
            break;
        case TEDM_Points :
            glmode = GL_POINTS;
            break;
        default:
            // type is not supported
            return TE_InvalidArg;
        }
        return TE_Ok;
    }
    //https://stackoverflow.com/questions/1903954/is-there-a-standard-sign-function-signum-sgn-in-c-c
    template <typename T>
    int sgn(T val)
    {
        return (T(0) < val) - (val < T(0));
    }

    bool intersects(const GLGlobeBase &view, const bool handleIdlCrossing, const Frustum2 &frustum, const Envelope2 &aabbWCS) NOTHROWS
    {
        typedef TAK::Engine::Math::Point2<double> PointD;

        return (frustum.intersects(AABB(PointD(aabbWCS.minX, aabbWCS.minY, aabbWCS.minZ), PointD(aabbWCS.maxX, aabbWCS.maxY, aabbWCS.maxZ))) ||
            (handleIdlCrossing && view.renderPass->drawLng * ((aabbWCS.minX + aabbWCS.maxX) / 2.0) < 0 &&
                frustum.intersects(
                    AABB(PointD(aabbWCS.minX - (360.0 * sgn((aabbWCS.minX + aabbWCS.maxX) / 2.0)), aabbWCS.minY, aabbWCS.minZ),
                        PointD(aabbWCS.maxX - (360.0 * sgn((aabbWCS.minX + aabbWCS.maxX) / 2.0)), aabbWCS.maxY, aabbWCS.maxZ)))));
    }
}

GLElevationHeatMapLayer::GLElevationHeatMapLayer(ElevationHeatMapLayer &subject) NOTHROWS :
    subject_(subject),
    saturation_(subject.getSaturation()),
    value_(subject.getValue()),
    alpha_(subject.getAlpha()),
    visible_(subject.isVisible()),
    draw_version_(-1),
    terrain_version_(-1)
{
    range.dynamic_ = subject_.isDynamicRange();
    if (!range.dynamic_)
        subject_.getAbsoluteRange(&range.absolute.min_, &range.absolute.max_);
}
GLElevationHeatMapLayer::~GLElevationHeatMapLayer() NOTHROWS
{}

Layer2 &GLElevationHeatMapLayer::getSubject() NOTHROWS
{
    return subject_;
}
void GLElevationHeatMapLayer::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS
{

    if (!(renderPass & getRenderPass()))
        return;
    // not visible, return
    if (!visible_)
        return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    if (!view.renderPass->drawTilt) {
        glDisable(GL_DEPTH_TEST);
    }

    SurfaceRendererControl *surfaceControl = view.getSurfaceRendererControl();
    const GLGlobeBase::State &pass = !!dynamic_cast<const GLGlobe *>(&view) ? view.renderPasses[0] : *view.renderPass;
    const int terrainVersion = view.getTerrainRenderService().getTerrainVersion();
    if(pass.drawVersion != draw_version_ || terrainVersion != terrain_version_) {
        draw_version_ = pass.drawVersion;
        terrain_version_ = terrainVersion;

        const TAK::Engine::Math::Point2<double> nominalMetersScale(pass.scene.displayModel->projectionXToNominalMeters, pass.scene.displayModel->projectionYToNominalMeters, pass.scene.displayModel->projectionZToNominalMeters);
        const double camtgt = Vector2_length(
            Vector2_subtract(
                Vector2_multiply(pass.scene.camera.location, nominalMetersScale),
                Vector2_multiply(pass.scene.camera.target, nominalMetersScale)));

        // establish min/max
        double minEl = NAN;
        double maxEl = NAN;
        for (std::size_t i = 0u; i < pass.renderTiles.count; i++) {
            const auto &tile = *pass.renderTiles.value[i].tile;
            if (!tile.hasData)
                continue;
            if (tile.aabb_wgs84.maxZ == 19000.0)
                continue;
            // exclude any tiles that are sufficiently far away
            TAK::Engine::Math::Point2<double> tileCentroid;
            pass.scene.projection->forward(&tileCentroid, GeoPoint2((tile.aabb_wgs84.minY+tile.aabb_wgs84.maxY)/2.0, (tile.aabb_wgs84.minX+tile.aabb_wgs84.maxX)/2.0, (tile.aabb_wgs84.minZ+tile.aabb_wgs84.maxZ)/2.0, AltitudeReference::HAE));
            const double slant = Vector2_length(
                Vector2_subtract(
                    Vector2_multiply(pass.scene.camera.location, nominalMetersScale),
                    Vector2_multiply(tileCentroid, nominalMetersScale)));

            TAK::Engine::Math::Point2<double> tileMin;
            pass.scene.projection->forward(&tileMin, GeoPoint2(tile.aabb_wgs84.minY, tile.aabb_wgs84.minX, tile.aabb_wgs84.minZ, AltitudeReference::HAE));
            const double radius = Vector2_length(
                Vector2_subtract(
                    Vector2_multiply(tileMin, nominalMetersScale),
                    Vector2_multiply(tileCentroid, nominalMetersScale)));

            if(log((slant-radius)/camtgt)/log(2.0) > 3.0)
                continue;
            const double skirtHeight = 500.0;
            if(TE_ISNAN(minEl) || (tile.aabb_wgs84.minZ+skirtHeight) < minEl)
                minEl = tile.aabb_wgs84.minZ+skirtHeight;
            if(TE_ISNAN(maxEl) || tile.aabb_wgs84.maxZ > maxEl)
                maxEl = tile.aabb_wgs84.maxZ;
        }
        if(range.dynamic_) {
            range.absolute.min_ = minEl;
            range.absolute.max_ = maxEl;
        }
    }
    if(TE_ISNAN(range.absolute.min_) || TE_ISNAN(range.absolute.max_))
        return;

    ShadersImpl shaders = getShader(view.context);
    GLGlobeBase::State renderTilePass(*view.renderPass);
    renderTilePass.texture = 1;
    TerrainTileRenderContext ctx = GLTerrainTile_begin(renderTilePass.scene, (view.renderPass->drawSrid == 4978) ? shaders.ecef.base : shaders.flat.base);

    ShaderImpl s;
    if (view.renderPass->drawSrid == 4978) {
        if (ctx.shader.base.handle == shaders.ecef.hi.base.base.handle)
            s = shaders.ecef.hi;
        else if (ctx.shader.base.handle == shaders.ecef.md.base.base.handle)
            s = shaders.ecef.md;
        else // ctx.shader.base.handle == shaders.ecef.lo.base.base.handle
            s = shaders.ecef.lo;
    } else { // flat
        if (ctx.shader.base.handle == shaders.flat.hi.base.base.handle)
            s = shaders.flat.hi;
        else if (ctx.shader.base.handle == shaders.flat.md.base.base.handle)
            s = shaders.flat.md;
        else // ctx.shader.base.handle == shaders.flat.lo.base.base.handle
            s = shaders.flat.lo;
    }

    glUniform1f(s.uMinEl, (float)range.absolute.min_);
    glUniform1f(s.uMaxEl, (float)range.absolute.max_);
    glUniform1f(s.uSaturation, saturation_);
    glUniform1f(s.uValue, value_);
    glUniform1f(s.uAlpha, alpha_);

    Matrix2 proj;
    glOrtho(proj, (float)view.renderPass->left, (float)view.renderPass->right, (float)view.renderPass->bottom, (float)view.renderPass->top, (float)view.renderPass->scene.camera.near, (float)view.renderPass->scene.camera.far);

    for(std::size_t i = 0u; i < renderTilePass.renderTiles.count; i++) {
        const auto &gltt = renderTilePass.renderTiles.value[i];
        if (!gltt.tile->hasData)
            continue;
        if(s.uLocalFrame >= 0)
            glUniformMatrix4(s.uLocalFrame, gltt.tile->data.localFrame);
        GLTerrainTile_drawTerrainTiles(ctx, &renderTilePass, 1u, &gltt, 1u);
    }
    GLTerrainTile_end(ctx);

    glDisable(GL_BLEND);
}
void GLElevationHeatMapLayer::release() NOTHROWS
{}
int GLElevationHeatMapLayer::getRenderPass() NOTHROWS
{
    return GLGlobeBase::Surface;
}
void GLElevationHeatMapLayer::start() NOTHROWS
{
    // register listeners
    subject_.addVisibilityListener(this);
    subject_.addListener(*this);

    // sync the state
    saturation_ = subject_.getSaturation();
    value_ = subject_.getValue();
    alpha_ = subject_.getAlpha();
    range.dynamic_ = subject_.isDynamicRange();
    if (!range.dynamic_)
        subject_.getAbsoluteRange(&range.absolute.min_, &range.absolute.max_);
    visible_ = subject_.isVisible();
}
void GLElevationHeatMapLayer::stop() NOTHROWS
{
    // unregister listeners
    subject_.removeVisibilityListener(this);
    subject_.removeListener(*this);
}
TAKErr GLElevationHeatMapLayer::onColorChanged(const ElevationHeatMapLayer& subject, const float saturation, const float value, const float alpha) NOTHROWS
{
    saturation_ = saturation;
    value_ = value;
    alpha_ = alpha;
    return TE_Ok;
}
TAKErr GLElevationHeatMapLayer::onRangeChanged(const ElevationHeatMapLayer& subject, const double min, const double max, const bool dynamicRange) NOTHROWS
{
    range.dynamic_ = dynamicRange;
    range.absolute.min_ = min;
    range.absolute.max_ = max;
    return TE_Ok;
}
TAKErr GLElevationHeatMapLayer::layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS
{
    visible_ = visible;
    return TE_Ok;
}
