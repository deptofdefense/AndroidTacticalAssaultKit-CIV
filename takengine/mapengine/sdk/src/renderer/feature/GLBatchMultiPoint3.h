#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOINT3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOINT3_H_INCLUDED

#include "renderer/feature/GLBatchGeometryCollection3.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class GLBatchMultiPoint3 : public GLBatchGeometryCollection3
                {
                public:
                    GLBatchMultiPoint3(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOINT2_H_INCLUDED
