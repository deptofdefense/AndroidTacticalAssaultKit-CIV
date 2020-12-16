
#include <GLES3/gl3.h>
#include "renderer/GLOffscreenFramebuffer.h"
#include "util/Memory.h"
#include "util/MathUtils.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

namespace {
    struct GLFBOGuard {
        GLFBOGuard() { glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, (GLint*)&currentFbo); }
        ~GLFBOGuard() { glBindFramebuffer(GL_FRAMEBUFFER, currentFbo); }
        GLuint currentFbo = GL_NONE;
    };

    static GLuint createTexture(int width, int height, int format, int internalFormat, int type, bool linear) {
        GLuint tex = GL_NONE;
        int filter = linear ? GL_LINEAR : GL_NEAREST;
        if (internalFormat == GL_NONE)
            internalFormat = format;

        glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat,
            width, height, 0,
            format, type, nullptr);
        glBindTexture(GL_TEXTURE_2D, 0);

        return tex;
    }
}

TAKErr TAK::Engine::Renderer::GLOffscreenFramebuffer_create(GLOffscreenFramebufferPtr& resultOut, int width, int height, GLOffscreenFramebuffer::Options options) NOTHROWS {

    GLOffscreenFramebufferPtr result(new (std::nothrow) GLOffscreenFramebuffer(), TAK::Engine::Util::Memory_deleter<GLOffscreenFramebuffer>);
    if (!result)
        return TE_OutOfMemory;

    GLFBOGuard fboGuard;

    result->width = width;
    result->height = height;
    result->textureWidth = MathUtils_isPowerOf2(width) ? width : 1 << MathUtils_nextPowerOf2(width);
    result->textureHeight = MathUtils_isPowerOf2(height) ? height : 1 << MathUtils_nextPowerOf2(height);

    GLuint fbo[3];

    if (options.colorFormat != GL_NONE)
        result->colorTexture = createTexture(result->textureWidth, result->textureHeight, options.colorFormat, options.colorInternalFormat, options.colorType, true);
    if (options.depthFormat != GL_NONE)
        result->depthTexture = createTexture(result->textureWidth, result->textureHeight, options.depthFormat, options.depthInternalFormat, options.depthType, false);
    if (options.stencilFormat != GL_NONE)
        result->stencilTexture = createTexture(result->textureWidth, result->textureHeight, options.stencilFormat, options.stencilInternalFormat, options.stencilType, false);

    bool fboCreated = false;
    do {
        // clear any pending errors
        while (glGetError() != GL_NO_ERROR)
            ;

        glGenFramebuffers(1, fbo);
        result->handle = fbo[0];

        if (options.depthFormat == GL_NONE) {
            glGenRenderbuffers(1, fbo + 1);
            glBindRenderbuffer(GL_RENDERBUFFER, fbo[1]);
            glRenderbufferStorage(GL_RENDERBUFFER,
                GL_DEPTH_COMPONENT24,
                result->textureWidth, result->textureHeight);
            glBindRenderbuffer(GL_RENDERBUFFER, 0);
        }

        // bind the FBO and set all texture attachments
        glBindFramebuffer(GL_FRAMEBUFFER, result->handle);

        // clear any pending errors
        while (glGetError() != GL_NO_ERROR)
            ;

        if (result->colorTexture != GL_NONE) {
            glFramebufferTexture2D(GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, result->colorTexture, 0);
        }

        // XXX - observing hard crash following bind of "complete"
        //       FBO on SM-T230NU. reported error is 1280 (invalid
        //       enum) on glFramebufferTexture2D. I have tried using
        //       the color-renderable formats required by GLES 2.0
        //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
        //       the same outcome.
        if (glGetError() != GL_NO_ERROR)
            break;

        if (result->depthTexture != GL_NONE) {

            int depthAttachment = GL_DEPTH_ATTACHMENT;

            // format indicates GL_DEPTH_STENCIL_ATTACHMENT?
            if (options.depthFormat == GL_DEPTH24_STENCIL8 ||
                options.depthFormat == GL_DEPTH24_STENCIL8_OES ||
                options.depthFormat == GL_DEPTH32F_STENCIL8 ||
                options.depthFormat == GL_DEPTH_STENCIL)
                depthAttachment = GL_DEPTH_STENCIL_ATTACHMENT;

            glFramebufferTexture2D(GL_FRAMEBUFFER,
                depthAttachment,
                GL_TEXTURE_2D, result->depthTexture, 0);
        }
        else {
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, fbo[0]);
        }
        if (glGetError() != GL_NO_ERROR)
            break;

        if (result->stencilTexture != GL_NONE) {
            glFramebufferTexture2D(GL_FRAMEBUFFER,
                GL_STENCIL_ATTACHMENT,
                GL_TEXTURE_2D, result->stencilTexture, 0);
        }
        if (glGetError() != GL_NO_ERROR)
            break;

        int fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (fboStatus != GL_FRAMEBUFFER_COMPLETE)
            break;

        resultOut = std::move(result);
        return TE_Ok;
    } while (false);

    return TE_Err;
}

GLOffscreenFramebuffer::~GLOffscreenFramebuffer() NOTHROWS {
    GLuint textures[] = { colorTexture, depthTexture, stencilTexture };
    glDeleteTextures(3, textures);
    colorTexture = GL_NONE;
    depthTexture = GL_NONE;
    stencilTexture = GL_NONE;

    glDeleteFramebuffers(1, &handle);
    handle = GL_NONE;
}

void GLOffscreenFramebuffer::bind() {
    glBindFramebuffer(GL_FRAMEBUFFER, handle);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
}