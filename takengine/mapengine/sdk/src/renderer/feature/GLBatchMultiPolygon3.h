#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOLYGON3_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOLYGON3_H_INCLUDED

#include "renderer/feature/GLBatchGeometryCollection3.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class GLBatchMultiPolygon3 : public GLBatchGeometryCollection3
                {
                public:
                    GLBatchMultiPolygon3(TAK::Engine::Core::RenderContext &surface);
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOLYGON2_H_INCLUDED
