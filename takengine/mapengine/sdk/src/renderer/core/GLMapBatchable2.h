#ifndef TAK_ENGINE_RENDERER_CORE_GLMAPBATCHABLE_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLMAPBATCHABLE_H_INCLUDED

#include "port/Platform.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/GLRenderBatch2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class GLMapBatchable2 {
                protected :
                    virtual ~GLMapBatchable2() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr batch(const GLMapView2 &view, const int renderPass, TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS = 0;
                };
            }
        }
    }
}

#endif
