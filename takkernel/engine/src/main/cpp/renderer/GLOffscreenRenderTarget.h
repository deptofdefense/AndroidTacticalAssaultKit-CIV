#ifndef TAK_ENGINE_RENDERER_GLOFFSCREENRENDERTARGET_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLOFFSCREENRENDERTARGET_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            class ENGINE_API GLOffscreenRenderTarget
            {
            public :
                GLOffscreenRenderTarget(const std::size_t width, const std::size_t height, const int format, const int type, const int bufferType, const int bufferBits) NOTHROWS;
                GLOffscreenRenderTarget() NOTHROWS;
            public :
                Util::TAKErr create() NOTHROWS;
                int getTextureId() const NOTHROWS;
                int getFBO() const NOTHROWS;
                void release() NOTHROWS;
            private :
                int _fbo;
                int _texid;
            };
        }
    }
}

#endif // TAK_ENGINE_RENDERER_GLOFFSCREENRENDERTARGET_H_INCLUDED
