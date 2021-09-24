#ifndef TAK_ENGINE_RENDERER_CORE_GLMAPVIEWDEBUG_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLMAPVIEWDEBUG_H_INCLUDED

#include "renderer/core/GLGlobe.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                /**
                 * Renders the visible contents of the globe surface using an
                 * ortho projection, filling the viewport.
                 */
                void GLMapViewDebug_drawVisibleSurface(GLGlobe &view) NOTHROWS;
                void GLMapViewDebug_drawCameraRangeRing(const GLGlobe &view, const std::size_t size, const double radiusMeters) NOTHROWS;
                void GLMapViewDebug_drawFrustum(const GLGlobe &view, const std::size_t size) NOTHROWS;
                void GLMapViewDebug_drawLine(const GLGlobe &view, const GLenum mode, const std::size_t size, const TAK::Engine::Core::GeoPoint2 *points, const std::size_t count) NOTHROWS;
            }
        }
    }
}

#endif // TAK_ENGINE_RENDERER_CORE_GLMAPVIEWDEBUG_H_INCLUDED
