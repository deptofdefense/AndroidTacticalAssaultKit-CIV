#include "renderer/RenderAttributes.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Model;

RenderAttributes::RenderAttributes() NOTHROWS :
    opaque(true),
    colorPointer(false),
    normals(false),
    lighting(false),
    windingOrder(TEWO_Undefined)
{
    for (std::size_t i = 0u; i < 8u; i++)
        textureIds[i] = 0;
}
