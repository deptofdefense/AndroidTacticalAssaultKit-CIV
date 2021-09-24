#pragma once

#include "renderer/core/GLMapRenderable2.h"
#include "renderer/core/GLResolvable.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class ENGINE_API GLResolvableMapRenderable2 : public GLResolvable, public GLMapRenderable2
                {
                   public:
                    virtual ~GLResolvableMapRenderable2() NOTHROWS = 0;
                };
                typedef std::unique_ptr<GLResolvableMapRenderable2, void (*)(const GLResolvableMapRenderable2 *)> GLResolvableMapRenderable2Ptr;
            }
        }
    }
}

