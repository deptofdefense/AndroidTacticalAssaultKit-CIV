#ifndef TAK_ENGINE_RENDERER_GLOFFSCREENFRAMEBUFFER_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLOFFSCREENFRAMEBUFFER_H_INCLUDED

#include "util/Error.h"
#include "renderer/Shader.h"
#include "core/RenderContext.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            class GLOffscreenFramebuffer {
            public:
                struct Options {
                    int depthFormat = GL_NONE;
                    int depthInternalFormat = GL_NONE;
                    int depthType = GL_NONE;
                    int colorFormat = GL_NONE;
                    int colorInternalFormat = GL_NONE;
                    int colorType = GL_NONE;
                    int stencilFormat = GL_NONE;
                    int stencilInternalFormat = GL_NONE;
                    int stencilType = GL_NONE;
                    GLenum bufferMask{ GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT };
                };

                GLuint handle = GL_NONE;
                int width;
                int height;
                int textureHeight;
                int textureWidth;
                GLuint colorTexture = GL_NONE;
                GLuint depthTexture = GL_NONE;
                GLuint stencilTexture = GL_NONE;
                GLuint depthStencilTexture = GL_NONE;

                void bind(const bool clear = true) NOTHROWS;

                ~GLOffscreenFramebuffer() NOTHROWS;

            public:
                GLOffscreenFramebuffer() = default;
            private :
                bool releaseOnDestruct{ false };
                GLuint renderBuffers[3]{ GL_NONE, GL_NONE, GL_NONE };
                GLsizei numRenderBuffers{ 0 };

                friend TAK::Engine::Util::TAKErr GLOffscreenFramebuffer_create(std::unique_ptr<GLOffscreenFramebuffer, void (*)(GLOffscreenFramebuffer*)>&, int, int, GLOffscreenFramebuffer::Options) NOTHROWS;
                friend TAK::Engine::Util::TAKErr GLOffscreenFramebuffer_create(GLOffscreenFramebuffer *, int, int, GLOffscreenFramebuffer::Options) NOTHROWS;
                friend TAK::Engine::Util::TAKErr GLOffscreenFramebuffer_release(GLOffscreenFramebuffer &) NOTHROWS;
            };

            typedef std::unique_ptr<GLOffscreenFramebuffer, void (*)(GLOffscreenFramebuffer*)> GLOffscreenFramebufferPtr;

            TAK::Engine::Util::TAKErr GLOffscreenFramebuffer_create(GLOffscreenFramebufferPtr& result, int width, int height, GLOffscreenFramebuffer::Options opts) NOTHROWS;
            TAK::Engine::Util::TAKErr GLOffscreenFramebuffer_create(GLOffscreenFramebuffer *result, int width, int height, GLOffscreenFramebuffer::Options opts) NOTHROWS;
            TAK::Engine::Util::TAKErr GLOffscreenFramebuffer_release(GLOffscreenFramebuffer &offscreen) NOTHROWS;
        }
    }
}

#endif
