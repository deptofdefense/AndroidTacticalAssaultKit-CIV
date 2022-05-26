#ifndef TAK_ENGINE_RENDERER_RENDERATTRIBUTES_H_INCLUDED
#define TAK_ENGINE_RENDERER_RENDERATTRIBUTES_H_INCLUDED

#include "model/Mesh.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {

            struct ENGINE_API RenderAttributes
            {
                RenderAttributes() NOTHROWS;

                /** if 'true' renderable content is opaque, if 'false' may have alpha */
                bool opaque;
                int textureIds[8];
                bool colorPointer;
                bool normals;
                bool lighting;
                bool points;
                TAK::Engine::Model::WindingOrder windingOrder;
            };
        }
    }
}
#endif

