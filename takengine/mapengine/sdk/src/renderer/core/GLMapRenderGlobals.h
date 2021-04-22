#ifndef TAK_ENGINE_RENDERER_CORE_GLMAPRENDERGLOBALS_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLMAPRENDERGLOBALS_H_INCLUDED

#include "core/RenderContext.h"
#include "port/Platform.h"
#include "renderer/AsyncBitmapLoader2.h"
#include "renderer/GLTextureAtlas.h"
#include "renderer/GLTextureAtlas2.h"
#include "renderer/GLTextureCache.h"
#include "renderer/GLTextureCache2.h"
#ifndef __ANDROID__
#include "renderer/core/GLLabelManager.h"
#endif
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getTextureAtlas(atakmap::renderer::GLTextureAtlas **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getIconAtlas(atakmap::renderer::GLTextureAtlas **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getTextureCache(atakmap::renderer::GLTextureCache **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
				ENGINE_API std::size_t GLMapRenderGlobals_getNominalIconSize() NOTHROWS;
				ENGINE_API float GLMapRenderGlobals_getRelativeDisplayDensity() NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_setRelativeDisplayDensity(float density_value) NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getBitmapLoader(TAK::Engine::Renderer::AsyncBitmapLoader2 **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;

				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getTextureAtlas2(TAK::Engine::Renderer::GLTextureAtlas2 **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getIconAtlas2(TAK::Engine::Renderer::GLTextureAtlas2 **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getTextureCache2(TAK::Engine::Renderer::GLTextureCache2 **value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
#ifndef __ANDROID__
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getLabelManager(TAK::Engine::Renderer::Core::GLLabelManager** value, const TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
#endif

				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_getTextureUnitLimit(std::size_t *limit) NOTHROWS;
				ENGINE_API TAK::Engine::Util::TAKErr GLMapRenderGlobals_setTextureUnitLimit(const std::size_t limit) NOTHROWS;
            }
        }
    }
}

#endif
