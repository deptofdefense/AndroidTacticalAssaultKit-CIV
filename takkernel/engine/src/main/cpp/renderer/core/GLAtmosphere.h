//
// Created by GeoDev on 1/18/2021.
//

#ifndef TAK_ENGINE_RENDERER_CORE_GLATMOSPHERE_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLATMOSPHERE_H_INCLUDED

#include <renderer/RenderAttributes.h>
#include "port/Platform.h"
#include "math/Vector4.h"
#include "renderer/core/GLGlobeBase.h"
namespace atakmap
{
    namespace renderer
    {
        struct Program;
    }
}

namespace TAK {
    namespace Engine {
        namespace Math{
            class Matrix2;
        }
        namespace Core {
            class RenderContext;
        }
        namespace Renderer {
            namespace Core {
                class ENGINE_API GLAtmosphere
                {
                public:
                    GLAtmosphere() = default;
                    Util::TAKErr draw(const GLGlobeBase &view) NOTHROWS;
                private:
                    void DrawAtmosphere(const GLGlobeBase& view) const NOTHROWS;
                    void DrawQuad(const TAK::Engine::Math::Matrix2 &inv, const TAK::Engine::Math::Vector4<double>&cameraPos) const NOTHROWS;
                    void init() NOTHROWS;
                    void ComputeInsideTexture();
                    void ComputeOutsideTexture();

                    std::shared_ptr<atakmap::renderer::Program> program;

                    int uCampos;
                    int uSunpos;
                    int uAlpha;
                    int aVertexCoordsHandle;
                    int aEyeRayHandle;
                    unsigned int insideTextureHandle;
                    unsigned int outsideTextureHandle;
                    unsigned int vbo{0u};

                };
            }
        }
    }
}
#endif //TAK_ENGINE_RENDERER_CORE_GLATMOSPHERE_H_INCLUDED
