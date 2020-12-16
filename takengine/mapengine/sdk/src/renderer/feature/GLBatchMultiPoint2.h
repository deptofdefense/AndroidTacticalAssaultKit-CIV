#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOINT2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOINT2_H_INCLUDED

#include "renderer/feature/GLBatchGeometryCollection2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchMultiPoint2 : public GLBatchGeometryCollection2
                {
                public:
                    GLBatchMultiPoint2(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOINT2_H_INCLUDED
