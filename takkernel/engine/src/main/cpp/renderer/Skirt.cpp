#include "renderer/Skirt.h"

using namespace TAK::Engine::Util;

std::size_t TAK::Engine::Renderer::Skirt_getNumOutputVertices(std::size_t count) NOTHROWS
{
    return count;
}

TAKErr TAK::Engine::Renderer::Skirt_getNumOutputIndices(std::size_t *value, const int mode, const std::size_t count) NOTHROWS
{
    switch(mode) {
    case GL_TRIANGLES :
        if(count <= 1u)
            return Util::TE_InvalidArg;
        *value = 6u*(count-1u);
        break;
    case GL_TRIANGLE_STRIP :
        *value = 2u*count;
        break;
    default :
        return Util::TE_InvalidArg;
    }
    return Util::TE_Ok;
}
