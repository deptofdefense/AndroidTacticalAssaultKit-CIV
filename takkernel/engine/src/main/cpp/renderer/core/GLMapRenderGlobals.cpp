#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/AsyncBitmapLoader2.h"
#ifndef __ANDROID__
#include "renderer/core/GLLabelManager.h"
#endif
#include <util/NonCopyable.h>

#include "core/AtakMapView.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/ConfigOptions.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

// defaults
#define TE_GLMRG_TEXTURE_CACHE_SIZE (100*1024*1024)
#define TE_GLMRG_TEXTURE_ATLAS_SIZE 1024
#define TE_GLMRG_ASYNC_BITMAP_LOADER_THREADS 8

// platform specific adjustments
#ifdef MSVC
#undef TE_GLMRG_TEXTURE_ATLAS_SIZE
#define TE_GLMRG_TEXTURE_ATLAS_SIZE 2048
#endif

namespace
{
    struct Context : TAK::Engine::Util::NonCopyable
    {
        std::unique_ptr<GLTextureAtlas> atlas;
        std::unique_ptr<GLTextureAtlas> iconAtlas;
        std::unique_ptr<GLTextureCache> cache;
        std::unique_ptr<TAK::Engine::Renderer::AsyncBitmapLoader2> bitmapLoader;
        std::unique_ptr<TAK::Engine::Renderer::Core::GLLabelManager> labelManager;
        std::unique_ptr<GLTextureAtlas2> atlas2;
        std::unique_ptr<GLTextureAtlas2> iconAtlas2;
        std::unique_ptr<GLTextureCache2> cache2;
    };

    std::map<const RenderContext *, std::unique_ptr<Context>> contextMap;
    Mutex contextMapMutex;

    std::size_t maxTextureUnits(0);
    std::size_t textureUnitLimit;
    float relativeDisplayDensity(1.0f);
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getTextureAtlas(GLTextureAtlas **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->atlas.get())
        entry->second->atlas.reset(new GLTextureAtlas(TE_GLMRG_TEXTURE_ATLAS_SIZE));
    *value = entry->second->atlas.get();
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getIconAtlas(GLTextureAtlas **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->iconAtlas.get())
        entry->second->iconAtlas.reset(new GLTextureAtlas(TE_GLMRG_TEXTURE_ATLAS_SIZE, static_cast<int>(GLMapRenderGlobals_getNominalIconSize()*atakmap::core::AtakMapView::DENSITY)));
    *value = entry->second->iconAtlas.get();
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getTextureCache(GLTextureCache **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->cache.get()) {
        std::size_t textureCacheSize = TE_GLMRG_TEXTURE_CACHE_SIZE;
        TAK::Engine::Port::String pref;
        if (ConfigOptions_getOption(pref, "texture-cache-size") == TE_Ok) {
            int i = atoi(pref);
            if (!i)
                i = 1;
            if (i > 0)
                textureCacheSize = i;
        }
        entry->second->cache.reset(new GLTextureCache(textureCacheSize));
    }
    *value = entry->second->cache.get();
    return TE_Ok;
}

std::size_t TAK::Engine::Renderer::Core::GLMapRenderGlobals_getNominalIconSize() NOTHROWS
{
#ifdef MSVC
    return 64u;
#else
    return 32u;
#endif
}

float TAK::Engine::Renderer::Core::GLMapRenderGlobals_getRelativeDisplayDensity() NOTHROWS { return relativeDisplayDensity; }

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_setRelativeDisplayDensity(float density_value) NOTHROWS {
    if (density_value <= 0.0f) return TE_InvalidArg;
    relativeDisplayDensity = density_value;
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getBitmapLoader(TAK::Engine::Renderer::AsyncBitmapLoader2 **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->bitmapLoader.get()) {
        // See comments on AsyncBitmapLoader2 constructor as to why this is needed
#ifdef WIN32
        entry->second->bitmapLoader.reset(new AsyncBitmapLoader2(TE_GLMRG_ASYNC_BITMAP_LOADER_THREADS, false));
#else
        entry->second->bitmapLoader.reset(new AsyncBitmapLoader2(TE_GLMRG_ASYNC_BITMAP_LOADER_THREADS));
#endif
    }
    *value = entry->second->bitmapLoader.get();
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getTextureAtlas2(GLTextureAtlas2 **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->atlas2.get())
        entry->second->atlas2.reset(new GLTextureAtlas2(TE_GLMRG_TEXTURE_ATLAS_SIZE));
    *value = entry->second->atlas2.get();
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getIconAtlas2(GLTextureAtlas2 **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->iconAtlas2.get()) {
        entry->second->iconAtlas2.reset(new GLTextureAtlas2(TE_GLMRG_TEXTURE_ATLAS_SIZE, static_cast<std::size_t>(GLMapRenderGlobals_getNominalIconSize()*atakmap::core::AtakMapView::DENSITY)));
    }
    *value = entry->second->iconAtlas2.get();
    return TE_Ok;
}


TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getTextureCache2(GLTextureCache2 **value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext *, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);

    if (!entry->second->cache2.get())
        entry->second->cache2.reset(new GLTextureCache2(TE_GLMRG_TEXTURE_CACHE_SIZE));
    *value = entry->second->cache2.get();
    return TE_Ok;
}


TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getLabelManager(TAK::Engine::Renderer::Core::GLLabelManager** value, const RenderContext &ctx) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    std::map<const RenderContext*, std::unique_ptr<Context>>::iterator entry;
    do {
        entry = contextMap.find(&ctx);
        if (entry == contextMap.end()) {
            contextMap[&ctx] = std::unique_ptr<Context>(new Context);
            continue;
        }
        break;
    } while (true);
    if (!entry->second->labelManager.get())
        entry->second->labelManager.reset(new TAK::Engine::Renderer::Core::GLLabelManager());
    *value = entry->second->labelManager.get();
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_getTextureUnitLimit(std::size_t *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (!maxTextureUnits) {
        int glLimit;
        glGetIntegerv(GL_MAX_TEXTURE_IMAGE_UNITS, &glLimit);

        // per the spec, GL must provide at least 2 texture units
        if (glLimit < 2)
            maxTextureUnits = 2;
        else
            maxTextureUnits = glLimit;
        textureUnitLimit = maxTextureUnits;
    }

    *value = std::min(maxTextureUnits, textureUnitLimit);
    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::Core::GLMapRenderGlobals_setTextureUnitLimit(const std::size_t limit) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(contextMapMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    textureUnitLimit = limit;
    return TE_Ok;
}
