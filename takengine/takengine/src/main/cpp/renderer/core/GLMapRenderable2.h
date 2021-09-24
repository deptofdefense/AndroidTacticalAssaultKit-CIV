#ifndef TAK_ENGINE_RENDERER_GLMAPRENDERABLE2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLMAPRENDERABLE2_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core
            {
                class ENGINE_API GLGlobeBase;

                class ENGINE_API GLMapRenderable2
                {
                protected :
                    virtual ~GLMapRenderable2() NOTHROWS = 0;
                public:
                    virtual void draw(const GLGlobeBase& view, const int renderPass) NOTHROWS = 0;
                    virtual void release() NOTHROWS = 0;
                    virtual int getRenderPass() NOTHROWS = 0;
                    virtual void start() NOTHROWS = 0;
                    virtual void stop() NOTHROWS = 0;
                };

                typedef std::unique_ptr<GLMapRenderable2, void(*)(const GLMapRenderable2 *)> GLMapRenderable2Ptr;
            }
        }
    }
}

#endif
