#ifndef TAK_ENGINE_RENDERER_RENDERSTATE_H_INCLUDED
#define TAK_ENGINE_RENDERER_RENDERSTATE_H_INCLUDED

#include "renderer/GL.h"

#include "port/Platform.h"
#include "renderer/Shader.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            struct ENGINE_API RenderState
            {
            private :
                RenderState() NOTHROWS;
            public :
                struct {
                    GLboolean enabled {false};
                    GLint func {0};
                    GLboolean mask {false};
                } depth;

                struct {
                    GLboolean enabled {false};
                    /** face to be culled */
                    GLint face {0};
                    /** the front face wind order*/
                    GLint front {0};
                } cull;

                struct
                {
                    GLboolean enabled {false};
                    GLint src {0};
                    GLint dst {0};
                } blend;

                std::shared_ptr<const Shader> shader;
            private :
                friend RenderState RenderState_getCurrent() NOTHROWS;
            };

            RenderState RenderState_getCurrent() NOTHROWS;
            void RenderState_makeCurrent(const RenderState &state) NOTHROWS;
        }
    }
}

#endif
