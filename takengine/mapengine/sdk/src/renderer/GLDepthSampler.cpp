
#include <GLES3/gl3.h>
#include "renderer/GLDepthSampler.h"
#include "util/MathUtils.h"
#include "renderer/GLES20FixedPipeline.h"
#include "util/Memory.h"
#include "renderer/Shader.h"
#include "renderer/GLES20FixedPipeline.h"
#include "core/MapSceneModel2.h"
#include "renderer/RenderState.h"

#define PROFILE_DEPTH_TESTS 0

#if PROFILE_DEPTH_TESTS
#include <chrono>
#endif

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;
using namespace atakmap::renderer;

namespace {
    const char* DEPTH_VERT_SHADER =
        "precision highp float;"
        "vec4 PackDepth(float v) {"
            "vec4 r = vec4(1.,255.,65025.,16581375.) * v;" // shift
            "r = fract(r);"
            "r -= r.yzww * vec4(1.0/255.0,1.0/255.0,1.0/255.0,0.0);" // mask
            "return r;"
        "}"       
        "uniform mat4 uMVP;"
        "attribute vec3 aVertexCoords;"
        "varying vec4 vDepthColor;"
        "void main() {"
            "vec4 vcoords = uMVP * vec4(aVertexCoords.xyz, 1.0);"
            "float depth = (vcoords.z + 1.0) * 0.5;"
            "vDepthColor = PackDepth(depth);"
            "gl_Position = vcoords;"
        "}";

    const char* DEPTH_FRAG_SHADER_SRC =
        "precision mediump float;"
        "varying vec4 vDepthColor;"
        "void main(void) {"
            "gl_FragColor = vDepthColor;"
        "}";

    const char* ID_VERT_SHADER =
        "attribute vec3 aVertexCoords;"
        "uniform mat4 uMVP;"
        "void main()"
        "{"
            "gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);"
        "}";


    const char* ID_FRAG_SHADER_SRC =
        "precision highp float;"
        "uniform int uColor;"
        "vec3 UnpackColor(float f) {"
            "vec3 color;"
            "color.b = floor(f / 256.0 / 256.0);"
            "color.g = floor((f - color.b * 256.0 * 256.0) / 256.0);"
            "color.r = floor(f - color.b * 256.0 * 256.0 - color.g * 256.0);"
            "return color / 255.0;"
        "}"
        "void main()"
        "{"
            "gl_FragColor = vec4(UnpackColor(float(uColor)), 0.0);"
        "}";

    const char *FULLSIZE_QUAD_VERT_SHADER_SRC =
        "precision lowp float;"
        "attribute vec2 aVertexCoords;"
        "varying vec2 vTexCoords;"
        "const vec2 scale = vec2(0.5, 0.5);"
        "void main()"
        "{"
            "vTexCoords = aVertexCoords * scale + scale;"
            "gl_Position = vec4(aVertexCoords, 0.0, 1.0);"
        "}";

    const char *FULLSIZE_QUAD_FRAG_SHADER_SRC = 
        "precision highp float;"
        "uniform sampler2D uTexture;"
        "varying vec2 vTexCoords;"
        "void main()"
        "{"
            "gl_FragColor = texture2D(uTexture, vTexCoords);"
        "}";

    const int VIEWPORT_WIDTH = 4;
    const int VIEWPORT_HEIGHT = 4;

    std::shared_ptr<const Shader2> get_shared_id_encoding_program(const TAK::Engine::Core::RenderContext& ctx) {
        static std::shared_ptr<const Shader2> inst;
        if (!inst)
            Shader_compile(inst, ctx, ID_VERT_SHADER, ID_FRAG_SHADER_SRC);
        return inst;
    }

    std::shared_ptr<const Shader2> get_shared_depth_encoding_program(const TAK::Engine::Core::RenderContext& ctx) {
        static std::shared_ptr<const Shader2> inst;
        if (!inst)
            Shader_compile(inst, ctx, DEPTH_VERT_SHADER, DEPTH_FRAG_SHADER_SRC);
        return inst;
    }

    std::shared_ptr<const Shader2> get_shared_fs_quad_program(const TAK::Engine::Core::RenderContext& ctx) {
        static std::shared_ptr<const Shader2> inst;
        if (!inst)
            Shader_compile(inst, ctx, FULLSIZE_QUAD_VERT_SHADER_SRC, FULLSIZE_QUAD_FRAG_SHADER_SRC);
        return inst;
    }
}

GLDepthSampler::GLDepthSampler() 
: fbo_(nullptr, nullptr),
fullscreen_quad_fbo_(nullptr, nullptr),
bound_fbo_(0),
method_(ENCODED_DEPTH_ENCODED_ID),
x_(0),
y_(0)
{
}

GLDepthSampler::~GLDepthSampler() {
}

void GLDepthSampler::beginProj(const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    // NOTE: We are pushing the far plane very far out as
    // the distribution of depth values is concentrated
    // close to the near plane. During rendering, we want
    // to minimize the distance between the near and far
    // planes to avoid z-fighting, hwoever, during the
    // depth hit-test, we'll push them further apart to get
    // better precision for depth value retrieval.

    // A better implementation of depth value encoding and
    // the use of an actual perspective projection could
    // mitigate the need to modify here

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glOrthof(0, (float)sceneModel.width, 0, (float)sceneModel.height, (float)sceneModel.camera.near, -2.f);
}

void GLDepthSampler::endProj() NOTHROWS {
    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glPopMatrix();
    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MM_GL_MODELVIEW);
}

TAKErr GLDepthSampler::begin(float x, float y, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);

    glGetIntegerv(GL_VIEWPORT, viewport_);
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, (GLint *)&bound_fbo_);
    blend_enabled_ = glIsEnabled(GL_BLEND);

    x_ = x;
    y_ = y;

    return TE_Ok;
}

GLuint GLDepthSampler::attributeVertexCoords() const NOTHROWS {
    return this->encoding_program_->aVertexCoords;
}

GLuint GLDepthSampler::uniformMVP() const NOTHROWS {
    return this->encoding_program_->uMVP;
}

float GLDepthSampler::getX() const NOTHROWS {
    return x_;
}

float GLDepthSampler::getY() const NOTHROWS {
    return y_;
}

void GLDepthSampler::enableEncodingState(const std::shared_ptr<const Shader2>& shader) NOTHROWS {

    fbo_->bind();
    glViewport(-(int)x_ + fbo_->width / 2, -(int)y_ + fbo_->height / 2, viewport_[2], viewport_[3]);

    glEnable(GL_SCISSOR_TEST);
    glScissor(fbo_->width / 2 - 1, fbo_->height / 2 - 1, 3, 3);

    glDisable(GL_BLEND);

    this->encoding_program_ = shader;
    glUseProgram(shader->handle);
}

TAK::Engine::Util::TAKErr GLDepthSampler::setDrawId(uint32_t drawId) NOTHROWS {

    if (drawId > MAX_DRAW_ID)
        return TE_InvalidArg;

#if 0
    float drawIdFloat = (float)drawId;
    glUniform1f(encoding_program_->uColor, drawIdFloat);
#else
    glUniform1i(encoding_program_->uColor, (GLint)drawId);
#endif

    return TE_Ok;
}

TAKErr GLDepthSampler::end() NOTHROWS {

    TAKErr code(TE_Ok);

    glDisable(GL_SCISSOR_TEST);
    glViewport(viewport_[0], viewport_[1], viewport_[2], viewport_[3]);

    if (blend_enabled_)
        glEnable(GL_BLEND);

    glBindFramebuffer(GL_FRAMEBUFFER, bound_fbo_);

    return TE_Ok;
}

uint32_t GLDepthSampler::readPixelAsId() const NOTHROWS {
    uint32_t pixel;
    glReadPixels(fbo_->width / 2, fbo_->height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, &pixel);

    //XXX-- need to flip pixel if GPU endianness != CPU endianness

    uint32_t drawId = 
        pixel;

    return drawId;
}

float GLDepthSampler::readPixelAsDepth() const NOTHROWS {
    uint32_t pixel = 0;
    glReadPixels(fbo_->width / 2, fbo_->height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, &pixel);

    //XXX-- need to flip pixel if GPU endianness != CPU endianness

    float r = (pixel & 0xff) / 255.f;
    float g = ((pixel >> 8) & 0xff) / 255.f;
    float b = ((pixel >> 16) & 0xff) / 255.f;
    float a = ((pixel >> 24) & 0xff) / 255.f;

    return r + g * (1.f / 255.f) + b * (1.f / 65025.f) + a * (1.f / 16581375.f);
}

GLDepthSampler::Method GLDepthSampler::getMethod() const NOTHROWS {
    return method_;
}

void GLDepthSampler::drawDepthToColor() NOTHROWS {

    fullscreen_quad_fbo_->bind();
    
    glViewport(0, 0, fullscreen_quad_fbo_->width, fullscreen_quad_fbo_->height);

    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_SCISSOR_TEST);
    glDepthMask(GL_FALSE);

    glUseProgram(cached_fs_program_->handle);
    glEnableVertexAttribArray(cached_fs_program_->aVertexCoords);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, fbo_->depthTexture);

    glUniform1i(cached_fs_program_->uTexture, 0);

    float quad[] = {
        -1.f, -1.f,
        -1.f, 1.0f,
        1.0f, -1.f,
        1.0f, 1.0f
    };
    glVertexAttribPointer(cached_fs_program_->aVertexCoords, 2, GL_FLOAT, false, 0, quad);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

static std::vector<TAK::Engine::Renderer::GLDepthSamplerDrawable*> depth_sampler_drawables_;

TAKErr GLDepthSampler::performDepthSample(double* resultZ, TAK::Engine::Math::Matrix2* proj, GLDepthSamplerDrawable** resultDrawable,
    GLDepthSamplerDrawable& drawable, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

    TAKErr code(TE_Unsupported);
    this->beginProj(sceneModel);

    if (method_ == ENCODED_ID_SAMPLED_DEPTH) {
        if (resultDrawable && resultZ)
            code = performSinglePassDepthSample(resultZ, resultDrawable, drawable, sceneModel, x, y);
        else
            code = performDoublePassDepthSample(resultZ, resultDrawable, drawable, sceneModel, x, y);
    } else if (method_ == ENCODED_DEPTH_ENCODED_ID) {
        code = performDoublePassDepthSample(resultZ, resultDrawable, drawable, sceneModel, x, y);
    }

    if (code == TE_Ok && proj) {
        float mxf[16];
        atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, mxf);
        for (std::size_t i = 0u; i < 16u; i++)
            proj->set(i % 4u, i / 4u, mxf[i]);
    }

    this->endProj();

    return code;
}

TAK::Engine::Util::TAKErr GLDepthSampler::performDoublePassDepthSample(
    double* resultZ,
    class GLDepthSamplerDrawable** resultDrawable,
    GLDepthSamplerDrawable& drawable,
    const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

#if PROFILE_DEPTH_TESTS
    auto profile_start = std::chrono::high_resolution_clock::now();
    int id_pass = resultDrawable != nullptr;
    int depth_pass = resultZ != nullptr;
#endif

    depth_sampler_drawables_.clear();

    // all the way to the leaves
    TAKErr code = drawable.gatherDepthSamplerDrawables(depth_sampler_drawables_, INT_MAX, sceneModel, x, y);
    if (code != TE_Ok)
        return code;

    size_t drawableCount = depth_sampler_drawables_.size();

    // Very unlikely, but check for it anyway
    if (resultDrawable && drawableCount > (GLDepthSampler::MAX_DRAW_ID - 1))
        return TE_Unsupported;

    if (drawableCount == 0)
        return TE_Done;

    RenderState restore = RenderState_getCurrent();

    this->begin(x, y, sceneModel);

    if (resultZ) {
        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        this->enableEncodingState(this->cached_depth_program_);
        for (size_t i = 0; i < drawableCount; ++i) {
            depth_sampler_drawables_[i]->depthSamplerDraw(*this, sceneModel);
        }
        float depth = this->readPixelAsDepth();
        if (depth >= 1.0f)
            code = TE_Done;
        else
            *resultZ = depth * 2.0 - 1.0;
    }

    if (resultDrawable && code != TE_Done) {
        glClearColor(1.0f, 1.0f, 0.0f, 0.0f);
        this->enableEncodingState(this->cached_id_program_);
        for (size_t i = 0; i < drawableCount; ++i) {
            // id 0 is reserved for "nothing"
            setDrawId(static_cast<uint32_t>(i + 1));
            depth_sampler_drawables_[i]->depthSamplerDraw(*this, sceneModel);
        }
        uint32_t id = this->readPixelAsId();
        if (id > 0 && id <= depth_sampler_drawables_.size())
            *resultDrawable = depth_sampler_drawables_[id - 1];
        else
            *resultDrawable = nullptr; // shouldn't be the case really
    }

    this->end();

    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    RenderState_makeCurrent(restore);

#if PROFILE_DEPTH_TESTS
    auto profile_end = std::chrono::high_resolution_clock::now();
    auto profile_span = profile_end - profile_start;
    auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(profile_span).count();
    TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Debug, "GLDepthSampler: performDoublePassDepthSample: id_pass=%d, depth_pass=%d, millis=%lld", id_pass, depth_pass, millis);
#endif

    return code;
}

TAK::Engine::Util::TAKErr GLDepthSampler::performSinglePassDepthSample(
    double* resultZ, // output
    class GLDepthSamplerDrawable** resultDrawable, // output
    GLDepthSamplerDrawable& drawable, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

    depth_sampler_drawables_.clear();

    // all the way to the leaves
    TAKErr code = drawable.gatherDepthSamplerDrawables(depth_sampler_drawables_, INT_MAX, sceneModel, x, y);
    if (code != TE_Ok)
        return code;

    size_t drawableCount = depth_sampler_drawables_.size();

    // Very unlikely, but check for it anyway
    if (drawableCount > (GLDepthSampler::MAX_DRAW_ID - 1))
        return TE_Unsupported;

    if (drawableCount == 0)
        return TE_Done;

    RenderState restore = RenderState_getCurrent();

    this->begin(x, y, sceneModel);

    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    this->enableEncodingState(this->cached_id_program_);
    for (size_t i = 0; i < drawableCount; ++i) {
        // id 0 is reserved for "nothing"
        uint32_t drawId = static_cast<uint32_t>(i + 1);
        setDrawId(drawId);
        depth_sampler_drawables_[i]->depthSamplerDraw(*this, sceneModel);
    }
    
    uint32_t id = this->readPixelAsId();
    if (id > 0 && id <= depth_sampler_drawables_.size())
        *resultDrawable = depth_sampler_drawables_[id - 1];
    
    this->drawDepthToColor();
    float depth = this->readPixelAsDepth();
    if (depth < 1.0f)
        *resultZ = depth * 2.0 - 1.0;
    else
        *resultZ = 1.0;

    this->end();

    RenderState_makeCurrent(restore);
    return code;
}

TAKErr TAK::Engine::Renderer::GLDepthSampler_create(GLDepthSamplerPtr& resultOut, const TAK::Engine::Core::RenderContext& ctx, GLDepthSampler::Method method) NOTHROWS {

    GLDepthSamplerPtr result(new (std::nothrow) GLDepthSampler(), Memory_deleter<GLDepthSampler>);
    if (!result)
        return TE_OutOfMemory;

    bool requiresFSQuadProg = false;
    TAKErr code (TE_Ok);

    // gather up required programs

    if (method == GLDepthSampler::ENCODED_ID_SAMPLED_DEPTH) {
        result->cached_fs_program_ = get_shared_fs_quad_program(ctx);
        if (!result->cached_fs_program_)
            return TE_Unsupported;
        requiresFSQuadProg = true;
    } else if (method != GLDepthSampler::ENCODED_DEPTH_ENCODED_ID) {
        code = TE_InvalidArg;
    }

    result->cached_depth_program_ = get_shared_depth_encoding_program(ctx);
    if (!result->cached_depth_program_)
        return TE_Unsupported;

    result->cached_id_program_ = get_shared_id_encoding_program(ctx);
    if (!result->cached_id_program_)
        return TE_Unsupported;

    if (code != TE_Ok)
        return code;

    GLOffscreenFramebuffer::Options opts;
    opts.colorFormat = GL_RGBA;
    opts.colorInternalFormat = GL_RGBA8;
    opts.colorType = GL_UNSIGNED_BYTE;

    if (requiresFSQuadProg) {
        // On some systems, this still comes out to be a 16-bit buffer...
        opts.depthFormat = GL_DEPTH_COMPONENT;
        opts.depthInternalFormat = GL_DEPTH_COMPONENT24;
        opts.depthType = GL_UNSIGNED_INT;
    }

    code = GLOffscreenFramebuffer_create(result->fbo_, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, opts);
    if (code != TE_Ok && opts.depthType != GL_NONE) {

        // attempt a 16-bit depth depth texture
        opts.depthFormat = GL_DEPTH_COMPONENT;
        opts.depthInternalFormat = GL_DEPTH_COMPONENT16;
        opts.depthType = GL_UNSIGNED_INT;
        code = GLOffscreenFramebuffer_create(result->fbo_, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, opts);
    }

    if (code != TE_Ok)
        return code;

    result->method_ = method;

    resultOut = std::move(result);
    return TE_Ok;
}

//
// GLDepthSamplerDrawable
//

TAKErr GLDepthSamplerDrawable::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
    return TE_Done;
}

void GLDepthSamplerDrawable::depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {

}
