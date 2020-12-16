#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOLYGON2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOLYGON2_H_INCLUDED

#include "renderer/feature/GLBatchGeometryCollection2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchMultiPolygon2 : public GLBatchGeometryCollection2
                {
                public:
                    GLBatchMultiPolygon2(TAK::Engine::Core::RenderContext &surface);
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTIPOLYGON2_H_INCLUDED
