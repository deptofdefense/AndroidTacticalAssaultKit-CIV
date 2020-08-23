#ifndef TAK_ENGINE_RENDERER_CORE_GLLAYER2_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLAYER2_H_INCLUDED

#include <memory>

#include "core/Layer2.h"
#include "renderer/core/GLMapRenderable2.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core
            {
                class ENGINE_API GLLayer2 : public virtual GLMapRenderable2
                {
                protected :
                    virtual ~GLLayer2() NOTHROWS = 0;
                public:
                    virtual TAK::Engine::Core::Layer2 &getSubject() NOTHROWS = 0;
                };

                typedef std::unique_ptr<GLLayer2, void(*)(const GLLayer2 *)> GLLayer2Ptr;
            }
        }
    }
}

#endif