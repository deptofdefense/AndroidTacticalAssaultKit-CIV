#ifndef TAK_ENGINE_RENDERER_CORE_LEGACYADAPTERS_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_LEGACYADAPTERS_H_INCLUDED

#include "port/Platform.h"
#include "renderer/core/GLLayer2.h"
#include "renderer/core/GLLayerSpi2.h"
#include "renderer/core/GLMapRenderable2.h"
#include "renderer/map/GLMapRenderable.h"
#include "renderer/map/layer/GLLayer.h"
#include "renderer/map/layer/GLLayerSpi.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                Util::TAKErr LegacyAdapters_adapt(GLMapRenderable2Ptr &value, atakmap::renderer::map::GLMapRenderablePtr &&legacy) NOTHROWS;
                Util::TAKErr LegacyAdapters_adapt(GLLayer2Ptr &value, atakmap::renderer::map::layer::GLLayerPtr &&legacy) NOTHROWS;
                Util::TAKErr LegacyAdapters_adapt(GLLayerSpi2Ptr &value, atakmap::renderer::map::layer::GLLayerSpiPtr &&legacy) NOTHROWS;
            }
        }
    }
}
#endif
