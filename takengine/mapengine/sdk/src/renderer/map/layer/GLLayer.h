#ifndef ATAKMAP_RENDERER_GLLAYER_H_INCLUDED
#define ATAKMAP_RENDERER_GLLAYER_H_INCLUDED

#include <memory>

#include "renderer/map/GLMapRenderable.h"
#include "core/Layer.h"

namespace atakmap
{
    namespace renderer
    {
        namespace map
        {
            namespace layer
            {
                class GLLayer : public virtual GLMapRenderable {
                public:
                    virtual ~GLLayer() {};
                    virtual atakmap::core::Layer *getSubject() = 0;
                };
                
                typedef std::unique_ptr<GLLayer, void(*)(const GLLayer *)> GLLayerPtr;
            }
        }
    }
}

#endif
