#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTILINESTRING3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTILINESTRING3_H_INCLUDED

#include "renderer/feature/GLBatchGeometryCollection3.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class GLBatchMultiLineString3 : public GLBatchGeometryCollection3
                {
                public:
                    GLBatchMultiLineString3(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTILINESTRING2_H_INCLUDED
