#include "elevation/ElevationSourceManager.h"

#include <cstring>
#include <set>

#include "thread/Mutex.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Elevation;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    Mutex &mutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }
    std::set<ElevationSourcesChangedListener *> &listeners() NOTHROWS
    {
        static std::set<ElevationSourcesChangedListener *> l;
        return l;
    }
    std::set<std::shared_ptr<ElevationSource>> &sources() NOTHROWS
    {
        static std::set<std::shared_ptr<ElevationSource>> s;
        return s;
    }
}

ElevationSourcesChangedListener::~ElevationSourcesChangedListener() NOTHROWS
{}

TAKErr TAK::Engine::Elevation::ElevationSourceManager_addOnSourcesChangedListener(ElevationSourcesChangedListener *listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners().insert(listener);
    return code;
}
TAKErr TAK::Engine::Elevation::ElevationSourceManager_removeOnSourcesChangedListener(ElevationSourcesChangedListener *listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners().erase(listener);
    return code;
}
TAKErr TAK::Engine::Elevation::ElevationSourceManager_attach(const std::shared_ptr<ElevationSource> &source) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    sources().insert(source);

    std::set<ElevationSourcesChangedListener *> &l = listeners();
    auto it = l.begin();
    while (it != l.end()) {
        if ((*it)->onSourceAttached(source) == TE_Done)
            it = l.erase(it);
        else
            it++;
    }

    return code;
}
TAKErr TAK::Engine::Elevation::ElevationSourceManager_detach(const ElevationSource &source) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    std::set<std::shared_ptr<ElevationSource>> &s = sources();
    std::set<std::shared_ptr<ElevationSource>>::iterator sourceIter;
    for (sourceIter = s.begin(); sourceIter != s.end(); sourceIter++) {
        if (&source == (*sourceIter).get()) {
            std::shared_ptr<ElevationSource> detached(*sourceIter);
            s.erase(sourceIter);

            std::set<ElevationSourcesChangedListener *> &l = listeners();
            auto it = l.begin();
            while (it != l.end()) {
                if ((*it)->onSourceDetached(*detached) == TE_Done)
                    it = l.erase(it);
                else
                    it++;
            }

            return TE_Ok;
        }
    }

    return TE_InvalidArg;
}
TAKErr TAK::Engine::Elevation::ElevationSourceManager_findSource(std::shared_ptr<ElevationSource> &value, const char *name) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<std::shared_ptr<ElevationSource>> &s = sources();
    std::set<std::shared_ptr<ElevationSource>>::iterator it;
    for (it = s.begin(); it != s.end(); it++) {
        const char *srcName = (*it)->getName();
        if (!srcName ^ !name)
            continue;
        if ((!srcName && !name) || strcmp(srcName, name) == 0) {
            value = *it;
            return TE_Ok;
        }
    }

    return TE_InvalidArg;
}
TAKErr TAK::Engine::Elevation::ElevationSourceManager_getSources(TAK::Engine::Port::Collection<std::shared_ptr<ElevationSource>> &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<std::shared_ptr<ElevationSource>> &s = sources();
    std::set<std::shared_ptr<ElevationSource>>::iterator it;
    for (it = s.begin(); it != s.end(); it++) {
        code = value.add(*it);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr TAK::Engine::Elevation::ElevationSourceManager_visitSources(TAKErr(*visitor)(void *opaque, ElevationSource &src) NOTHROWS, void *opaque) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, mutex());
    TE_CHECKRETURN_CODE(code);

    std::set<std::shared_ptr<ElevationSource>> &s = sources();
    std::set<std::shared_ptr<ElevationSource>>::iterator it;
    for (it = s.begin(); it != s.end(); it++) {
        code = visitor(opaque, **it);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
