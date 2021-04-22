#ifndef TAK_ENGINE_RENDERER_CORE_GLLAYERFACTORY2_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLLAYERFACTORY2_H_INCLUDED

#include <memory>

#include "port/Platform.h"
#include "renderer/core/GLLayer2.h"
#include "renderer/core/GLLayerSpi2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                ENGINE_API Util::TAKErr GLLayerFactory2_registerSpi(const std::shared_ptr<GLLayerSpi2> &spi, const int priority) NOTHROWS;
                ENGINE_API Util::TAKErr GLLayerFactory2_unregisterSpi(const GLLayerSpi2 &spi) NOTHROWS;
                ENGINE_API Util::TAKErr GLLayerFactory2_create(GLLayer2Ptr &value, GLGlobeBase &view, TAK::Engine::Core::Layer2 &subject) NOTHROWS;

                /**
                 * Utility function to create a 'GLLayer2' given a subject and
                 * a 'GLRenderable2' that was created to render that layer by
                 * some other client code. The returned 'GLLayer2'
                 * implementation will take care of visibility state changes;
                 * any other implementation detail specific to rendering the
                 * source layer is delegated to the renderable. The 'GLLayer2'
                 * instance will forward calls to the renderable's 'start()',
                 * 'stop()', 'draw()', 'release()' and 'getRenderPass()'
                 * functions on invocation.
                 */
                ENGINE_API Util::TAKErr GLLayerFactory2_create(GLLayer2Ptr &value, TAK::Engine::Core::Layer2 &subject, GLMapRenderable2Ptr &&renderer) NOTHROWS;
            }
        }
    }
}
#endif
