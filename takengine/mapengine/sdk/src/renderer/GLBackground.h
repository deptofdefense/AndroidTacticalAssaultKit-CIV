#ifndef TAK_ENGINE_RENDERER_GLBACKGROUND_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLBACKGROUND_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            TAK::Engine::Util::TAKErr GLBackground_draw(float x0, float y0, float x1, float y1, float r, float g, float b, float a) NOTHROWS;
        }
    }
}

#endif // TAK_ENGINE_RENDERER_GLBACKGROUND_H_INCLUDED
