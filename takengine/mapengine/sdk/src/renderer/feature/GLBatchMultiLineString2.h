#ifndef TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTILINESTRING2_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTILINESTRING2_H_INCLUDED

#include "port/Platform.h"
#include "renderer/feature/GLBatchGeometryCollection2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLBatchMultiLineString2 : public GLBatchGeometryCollection2
                {
                public:
                    GLBatchMultiLineString2(TAK::Engine::Core::RenderContext &surface) NOTHROWS;
                };
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_FEATURE_GLBATCHMULTILINESTRING2_H_INCLUDED
