#include "renderer/elevation/GLTerrainSlopeAngleLayer.h"

#include <vector>

#include "core/RenderContext.h"
#include "feature/GeometryTransformer.h"
#include "math/Frustum2.h"
#include "math/Rectangle.h"
#include "model/Mesh.h"
#include "port/STLVectorAdapter.h"
#include "renderer/GLMatrix.h"
#include "renderer/GLSLUtil.h"
#include "renderer/GLText2.h"
#include "renderer/elevation/GLTerrainTile.h"
#include "renderer/elevation/TerrainRenderService.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"

#include "renderer/GLES20FixedPipeline.h"

using namespace TAK::Engine::Renderer::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
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
    "varying float vNoDataFlag;\n" \
    "varying float vAngle;\n" \
    LLA2ECEF_FN_SRC \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords, 1.0);\n" \
    "  lla = lla / lla.w;\n" \
    "  vec3 ecef = lla2ecef(vec3(lla.xy, lla.z));\n" \
    "  vNoDataFlag = aNoDataFlag;\n" \
    "  vAngle = abs(dot(normalize(aNormals), vec3(0.0, 0.0, 1.0)));\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "}"

#define ECEF_MD_VSH \
    "#version 100\n" \
    "uniform mat4 uMVP;\n" \
    "uniform mat4 uLocalTransform[3];\n" \
    "attribute vec3 aVertexCoords;\n" \
    "attribute vec3 aNormals;\n" \
    "attribute float aNoDataFlag;\n" \
    "varying float vNoDataFlag;\n" \
    "varying float vAngle;\n" \
    "void main() {\n" \
    "  vec4 lla = uLocalTransform[0] * vec4(aVertexCoords.xyz, 1.0);\n" \
    "  lla /= lla.w;\n" \
    "  vec4 llaLocal = uLocalTransform[1] * lla;\n" \
    "  vec4 lla2ecef_in = vec4(llaLocal.xy, llaLocal.x*llaLocal.y, 1.0);\n" \
    "  lla2ecef_in /= lla2ecef_in.w;\n" \
    "  vec4 ecefSurface = uLocalTransform[2] * lla2ecef_in;\n" \
    "  ecefSurface /= ecefSurface.w;\n" \
    "  vec3 ecef = vec3(ecefSurface.xy * (1.0 + llaLocal.z / 6378137.0), ecefSurface.z * (1.0 + llaLocal.z / 6356752.3142));\n" \
    "  vNoDataFlag = aNoDataFlag;\n" \
    "  vAngle = abs(dot(normalize(aNormals), vec3(0.0, 0.0, 1.0)));\n" \
    "  gl_Position = uMVP * vec4(ecef.xyz, 1.0);\n" \
    "}"

#define PLANAR_SHADER_VSH \
    "#version 100\n" \
    "uniform mat4 uMVP;\n" \
    "attribute vec4 aVertexCoords;\n" \
    "attribute vec3 aNormals;\n" \
    "attribute float aNoDataFlag;\n" \
    "varying float vNoDataFlag;\n" \
    "varying float vAngle;\n" \
    "void main() {\n" \
    "  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);\n" \
    "  vNoDataFlag = aNoDataFlag;\n" \
    "  vAngle = abs(dot(normalize(aNormals), vec3(0.0, 0.0, 1.0)));\n" \
    "}"

#define SHADER_FSH \
    "#version 100\n" \
    "precision mediump float;\n" \
    "uniform sampler2D uTexture;\n" \
    "uniform float uAlpha;\n" \
    "varying float vNoDataFlag;\n" \
    "varying float vAngle;\n" \
    "void main(void) {\n" \
    "  vec4 color = texture2D(uTexture, vec2(vAngle, 0.5));\n" \
    "  gl_FragColor = vec4(color.rgb, color.a*uAlpha*step(1.0, vNoDataFlag));\n" \
    "}"

namespace
{
    struct ShaderImpl
    {
        TerrainTileShader base;
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
        
        s.base.handle = prog.program;
        // vertex shader
        s.base.uMVP = glGetUniformLocation(s.base.base.handle, "uMVP");
        s.base.uLocalTransform = glGetUniformLocation(s.base.base.handle, "uLocalTransform");
        s.base.aVertexCoords = glGetAttribLocation(s.base.base.handle, "aVertexCoords");
        s.base.aNormals = glGetAttribLocation(s.base.base.handle, "aNormals");
        s.base.aNoDataFlag = glGetAttribLocation(s.base.base.handle, "aNoDataFlag");
        s.base.uTexture = glGetUniformLocation(s.base.base.handle, "uTexture");
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
    unsigned int createTexture() NOTHROWS
    {
        unsigned int texid;
        glGenTextures(1u, &texid);

        struct {
            double minAngle;
            union {
                uint8_t rgba[4u];
                unsigned int packed;
            } color;
        } lut[8u];

        lut[0].minAngle = 0.0;  lut[0].color.rgba[0] = 0x0u; lut[0].color.rgba[1] = 0x00u; lut[0].color.rgba[2] = 0x00u; lut[0].color.rgba[3] = 0x00u;
        lut[1].minAngle = 27.0; lut[1].color.rgba[0] = 0xFFu; lut[1].color.rgba[1] = 0xFFu; lut[1].color.rgba[2] = 0x00u; lut[1].color.rgba[3] = 0xFFu;
        lut[2].minAngle = 29.0; lut[2].color.rgba[0] = 0xFFu; lut[2].color.rgba[1] = 0xC0u; lut[2].color.rgba[2] = 0x00u; lut[2].color.rgba[3] = 0xFFu;
        lut[3].minAngle = 31.0; lut[3].color.rgba[0] = 0xFFu; lut[3].color.rgba[1] = 0x7Fu; lut[3].color.rgba[2] = 0x00u; lut[3].color.rgba[3] = 0xFFu;
        lut[4].minAngle = 35.0; lut[4].color.rgba[0] = 0xFFu; lut[4].color.rgba[1] = 0x00u; lut[4].color.rgba[2] = 0x00u; lut[4].color.rgba[3] = 0xFFu;
        lut[5].minAngle = 45.0; lut[5].color.rgba[0] = 0xFFu; lut[5].color.rgba[1] = 0x00u; lut[5].color.rgba[2] = 0xFFu; lut[5].color.rgba[3] = 0xFFu;
        lut[6].minAngle = 50.0; lut[6].color.rgba[0] = 0x00u; lut[6].color.rgba[1] = 0x00u; lut[6].color.rgba[2] = 0xFFu; lut[6].color.rgba[3] = 0xFFu;
        lut[7].minAngle = 59.0; lut[7].color.rgba[0] = 0x00u; lut[7].color.rgba[1] = 0x00u; lut[7].color.rgba[2] = 0x00u; lut[7].color.rgba[3] = 0xFFu;

        const unsigned int texsize = 128u;
        unsigned int rgba[texsize];
        for(unsigned int i = 0u; i < texsize; i++) {
            const float u = (float)i / (float)(texsize-1u);
            const double theta = acos(u)/M_PI*180.0;
            for(unsigned int j = 8u; j > 0u; j--) {
                if(lut[j-1u].minAngle <= theta) {
                    rgba[i] = lut[j-1u].color.packed;
                    break;
                }
            }
        }

        glBindTexture(GL_TEXTURE_2D, texid);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texsize, 1u, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glBindTexture(GL_TEXTURE_2D, 0u);

        return texid;
    }
}

GLTerrainSlopeAngleLayer::GLTerrainSlopeAngleLayer(TerrainSlopeAngleLayer &subject) NOTHROWS :
    subject_(subject),
    alpha_(subject.getAlpha()),
    visible_(subject.isVisible()),
    texture_id_(0u),
    legend_vbo_(0u)
{}
GLTerrainSlopeAngleLayer::~GLTerrainSlopeAngleLayer() NOTHROWS
{}

Layer2 &GLTerrainSlopeAngleLayer::getSubject() NOTHROWS
{
    return subject_;
}
void GLTerrainSlopeAngleLayer::draw(const GLGlobeBase& view, const int renderPass) NOTHROWS
{

    if (!(renderPass & getRenderPass()))
        return;
    // not visible, return
    if (!visible_)
        return;

    if(renderPass&GLGlobeBase::Surface)
        drawSlopeAngle(view);
    if(renderPass&GLGlobeBase::UserInterface)
        drawLegend(view);
}
void GLTerrainSlopeAngleLayer::drawSlopeAngle(const GLGlobeBase& view) NOTHROWS
{
    if(!texture_id_)
        texture_id_ = createTexture();

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    ShadersImpl shaders = getShader(view.context);
    GLGlobeBase::State renderTilePass(*view.renderPass);
    renderTilePass.texture = texture_id_;
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

    glUniform1f(s.uAlpha, alpha_);
    GLTerrainTile_bindTexture(ctx, texture_id_, 1u, 1u);

    for(std::size_t i = 0u; i < renderTilePass.renderTiles.count; i++) {
        const auto &gltt = renderTilePass.renderTiles.value[i];
        if (!gltt.tile->hasData)
            continue;
        GLTerrainTile_drawTerrainTiles(ctx, &renderTilePass, 1u, &gltt, 1u);
    }
    GLTerrainTile_end(ctx);

    glDisable(GL_BLEND);
}
void GLTerrainSlopeAngleLayer::drawLegend(const GLGlobeBase& view) NOTHROWS
{
    using namespace atakmap::renderer;

#define LEGEND_TEXT \
    "27-29\n" \
    "30-31\n" \
    "32-34\n" \
    "35-45\n" \
    "46-50\n" \
    "51-59\n" \
    "60+"

    auto gltext = GLText2_intern(TextFormatParams(14.f));
    if(!gltext)
        return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float textHeight = gltext->getTextFormat().getStringHeight(LEGEND_TEXT);
    const float textWidth = gltext->getTextFormat().getStringWidth(LEGEND_TEXT);
    const float legendBoundsLeft = 16.f;
    const float legendBoundsTop = view.renderPass->bottom+(view.renderPass->top-view.renderPass->bottom)/2.f;
    const float legendBoundsRight = legendBoundsLeft+textWidth;
    const float legendBoundsBottom = legendBoundsTop-textHeight;

    auto &glfp = *GLES20FixedPipeline::getInstance();
    glfp.glPushMatrix();
    glfp.glTranslatef(legendBoundsLeft, legendBoundsBottom, 0.f);

    std::shared_ptr<const Shader> shader;
    RenderAttributes attr;
    attr.colorPointer = true;
    attr.lighting = false;
    attr.normals = false;
    attr.opaque = true;
    Shader_get(shader, view.context, attr);

    glUseProgram(shader->handle);

    // uniforms
    float mxp[16];
    glfp.readMatrix(GLES20FixedPipeline::MM_GL_PROJECTION, mxp);
    glUniformMatrix4fv(shader->uProjection, 1, false, mxp);
    float mxmv[16];
    glfp.readMatrix(GLES20FixedPipeline::MM_GL_MODELVIEW, mxmv);
    glUniformMatrix4fv(shader->uModelView, 1, false, mxmv);
    glUniform4f(shader->uColor, 1.f, 1.f, 1.f, 1.f);
    // attributes
    glEnableVertexAttribArray(shader->aVertexCoords);
    glEnableVertexAttribArray(shader->aColorPointer);
    if(!legend_vbo_) {
        glGenBuffers(1u, &legend_vbo_);
        glBindBuffer(GL_ARRAY_BUFFER, legend_vbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(float)*84u, nullptr, GL_STATIC_DRAW);
        float *c = reinterpret_cast<float *>((uint8_t *)glMapBufferRange(GL_ARRAY_BUFFER, 0, sizeof(float)*84u, GL_MAP_WRITE_BIT));
        unsigned int idx = 0;
        c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 0.f; c[idx++] = c[1] + gltext->getTextFormat().getCharHeight(); c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 1.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = c[7] + gltext->getTextFormat().getCharHeight(); c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 1.f; c[idx++] = 1.f;
        c[idx++] = 0.f; c[idx++] = c[13] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.f; c[idx++] = 1.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = c[19] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.f; c[idx++] = 1.f; c[idx++] = 1.f;
        c[idx++] = 0.f; c[idx++] = c[25] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = c[31] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 0.f; c[idx++] = c[37] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.5f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = c[43] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.5f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 0.f; c[idx++] = c[49] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.75f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = c[55] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 0.75f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 0.f; c[idx++] = c[61] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 1.f; c[idx++] = 0.f; c[idx++] = 1.f;
        c[idx++] = 16.f; c[idx++] = c[67] + gltext->getTextFormat().getCharHeight(); c[idx++] = 1.f; c[idx++] = 1.f; c[idx++] = 0.f; c[idx++] = 1.f;
        glUnmapBuffer(GL_ARRAY_BUFFER);
    } else {
        glBindBuffer(GL_ARRAY_BUFFER, legend_vbo_);
    }
    glVertexAttribPointer(shader->aVertexCoords, 2, GL_FLOAT, false, 24, (const void *)0u);
    glVertexAttribPointer(shader->aColorPointer, 4, GL_FLOAT, false, 24, (const void *)8u);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 14);

    glDisableVertexAttribArray(shader->aVertexCoords);
    glDisableVertexAttribArray(shader->aColorPointer);
    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);

    glfp.glTranslatef(40.f, textHeight-gltext->getTextFormat().getCharHeight(), 0.f);
    int lineColors[7u] =
            {(int)0xFFFFFF00,
             (int)0xFFFFC000,
             (int)0xFFFF7F00,
             (int)0xFFFF0000,
             (int)0xFFFF00FF,
             (int)0xFF0000FF,
             (int)0xFF000000,
             };
    gltext->draw(LEGEND_TEXT, lineColors, 7u);
    glfp.glPopMatrix();
}
void GLTerrainSlopeAngleLayer::release() NOTHROWS
{
    if(texture_id_) {
        glDeleteTextures(1u, &texture_id_);
        texture_id_ = 0u;
    }
    if(legend_vbo_) {
        glDeleteBuffers(1u, &legend_vbo_);
        legend_vbo_ = 0u;
    }
}
int GLTerrainSlopeAngleLayer::getRenderPass() NOTHROWS
{
    return GLMapView2::Surface|GLMapView2::UserInterface;
}
void GLTerrainSlopeAngleLayer::start() NOTHROWS
{
    // register listeners
    subject_.addVisibilityListener(this);
    subject_.addListener(*this);

    // sync the state
    alpha_ = subject_.getAlpha();
    visible_ = subject_.isVisible();
}
void GLTerrainSlopeAngleLayer::stop() NOTHROWS
{
    // unregister listeners
    subject_.removeVisibilityListener(this);
    subject_.removeListener(*this);
}
TAKErr GLTerrainSlopeAngleLayer::onColorChanged(const TerrainSlopeAngleLayer& subject, const float alpha) NOTHROWS
{
    alpha_ = alpha;
    // XXX - mark surface dirty
    return TE_Ok;
}
TAKErr GLTerrainSlopeAngleLayer::layerVisibilityChanged(const Layer2 &layer, const bool visible) NOTHROWS
{
    visible_ = visible;
    return TE_Ok;
}
