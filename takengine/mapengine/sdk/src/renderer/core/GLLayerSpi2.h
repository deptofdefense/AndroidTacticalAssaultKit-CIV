#ifndef TAK_ENGINE_RENDERER_CORE_GLLAYERSPI2_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLAYERSPI2_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/core/GLMapView2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class ENGINE_API GLLayerSpi2
                {
                public :
                    virtual Util::TAKErr create(GLLayer2Ptr &value, GLGlobeBase &renderer, TAK::Engine::Core::Layer2 &subject) NOTHROWS = 0;
                    virtual ~GLLayerSpi2() NOTHROWS = 0;
                };

                typedef std::unique_ptr<GLLayerSpi2, void(*)(const GLLayerSpi2 *)> GLLayerSpi2Ptr;
            }
        }
    }
}
#endif
