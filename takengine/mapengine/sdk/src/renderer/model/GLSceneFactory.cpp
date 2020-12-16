#include "renderer/model/GLSceneFactory.h"

#include <set>

#include "renderer/model/GLScene.h"
#include "renderer/model/GLProgressiveScene.h"
#include "thread/RWMutex.h"

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    struct Spi
    {
        std::shared_ptr<GLSceneSpi> impl;
        int priority {0};
        std::size_t insert {0};
    };

    struct SpiComparator
    {
        bool operator()(const Spi &a, const Spi &b) const
        {
            if (a.priority > b.priority)
                return true;
            else if (a.priority < b.priority)
                return false;
            else if (a.insert > b.insert)
                return true;
            else if (a.insert < b.insert)
                return false;
            else
                return ((intptr_t)a.impl.get()) < ((intptr_t)b.impl.get());
        }
    };

    RWMutex &spisMutex() NOTHROWS
    {
        static RWMutex rwm;
        return rwm;
    }

    std::set<Spi, SpiComparator> &spis()
    {
        static std::set<Spi, SpiComparator> s;
        return s;
    }

    std::size_t &insert_id()
    {
        static std::size_t i = 0u;
        return i;
    }
}

TAKErr TAK::Engine::Renderer::Model::GLSceneFactory_create(GLMapRenderable2Ptr &value, RenderContext &ctx, const SceneInfo &info, const GLSceneSpi::Options &opts) NOTHROWS
{
    TAKErr code(TE_Ok);
    ReadLock rlock(spisMutex());
    code = rlock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<Spi, SpiComparator> &s = spis();
    std::set<Spi, SpiComparator>::iterator it;
    for (it = s.begin(); it != s.end(); it++) {
        if ((*it).impl->create(value, ctx, info, opts) == TE_Ok)
            return TE_Ok;
    }

    value = GLMapRenderable2Ptr(new GLScene(ctx, info, opts), Memory_deleter_const<GLMapRenderable2, GLScene>);
    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::Model::GLSceneFactory_registerSpi(const std::shared_ptr<GLSceneSpi> &spi, const int priority) NOTHROWS
{
    TAKErr code(TE_Ok);
    WriteLock wlock(spisMutex());
    code = wlock.status;
    TE_CHECKRETURN_CODE(code);

    // check to see if contained and erase if necessary
    std::set<Spi, SpiComparator> &s = spis();
    std::set<Spi, SpiComparator>::iterator it;
    for (it = s.begin(); it != s.end(); it++) {
        if ((*it).impl.get() == spi.get()) {
            s.erase(it);
            break;
        }
    }

    Spi toInsert;
    toInsert.impl = spi;
    toInsert.priority = priority;
    toInsert.insert = ++insert_id();

    s.insert(toInsert);

    return code;
}
TAKErr TAK::Engine::Renderer::Model::GLSceneFactory_registerSpi(std::unique_ptr<GLSceneSpi> &&spi, const int priority) NOTHROWS
{
    if (!spi.get())
        return TE_InvalidArg;

    std::shared_ptr<GLSceneSpi> spi_shared(std::move(spi));
    return GLSceneFactory_registerSpi(spi_shared, priority);
}
TAKErr TAK::Engine::Renderer::Model::GLSceneFactory_unregisterSpi(const GLSceneSpi &spi) NOTHROWS
{
    TAKErr code(TE_Ok);
    WriteLock wlock(spisMutex());
    code = wlock.status;
    TE_CHECKRETURN_CODE(code);

    // check to see if contained and erase if necessary
    std::set<Spi, SpiComparator> &s = spis();
    std::set<Spi, SpiComparator>::iterator it;
    for (it = s.begin(); it != s.end(); it++) {
        if ((*it).impl.get() == &spi) {
            s.erase(it);
            return TE_Ok;
        }
    }

    return TE_InvalidArg;
}
