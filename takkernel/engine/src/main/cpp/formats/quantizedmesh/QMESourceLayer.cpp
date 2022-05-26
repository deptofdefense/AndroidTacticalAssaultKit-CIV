#include "formats/quantizedmesh/QMESourceLayer.h"
#include "formats/quantizedmesh/impl/QMElevationSource.h"
#include "formats/quantizedmesh/impl/TerrainDataCache.h"

#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "elevation/ElevationSourceManager.h"

#include <map>

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;


namespace {
    Thread::Mutex &mutex() NOTHROWS
    {
        static Thread::Mutex m;
        return m;
    }
    std::map<std::shared_ptr<QMESourceLayer>, std::shared_ptr<Impl::QMElevationSource>> &sources() NOTHROWS
    {
        static std::map<std::shared_ptr<QMESourceLayer>, std::shared_ptr<Impl::QMElevationSource>> s;
        return s;
    }
}




Util::TAKErr TAK::Engine::Formats::QuantizedMesh::QMESourceLayer_attach(std::shared_ptr<QMESourceLayer> &layer)
{
    Util::TAKErr code(Util::TE_Ok);
    Thread::Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::shared_ptr<Impl::QMElevationSource> source(new Impl::QMElevationSource(layer));
    code = Elevation::ElevationSourceManager_attach(source);
    TE_CHECKRETURN_CODE(code);

    sources().insert(std::pair<std::shared_ptr<QMESourceLayer>, std::shared_ptr<Impl::QMElevationSource>>(layer, source));

    return Util::TE_Ok;
}


Util::TAKErr TAK::Engine::Formats::QuantizedMesh::QMESourceLayer_detach(const QMESourceLayer &source)
{
    Util::TAKErr code(Util::TE_Ok);
    Thread::Lock lock(mutex());
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    std::map<std::shared_ptr<QMESourceLayer>, std::shared_ptr<Impl::QMElevationSource>> &s = sources();
    std::map<std::shared_ptr<QMESourceLayer>, std::shared_ptr<Impl::QMElevationSource>>::iterator sourceIter;
    for (sourceIter = s.begin(); sourceIter != s.end(); sourceIter++) {
        if (&source == sourceIter->first.get()) {
            Elevation::ElevationSourceManager_detach(*(sourceIter->second));
            s.erase(sourceIter);

            // Purge the cache entries for this layer
            Port::String dir;
            code = source.getDirectory(&dir);
            if (code == Util::TE_Ok)
                Impl::TerrainDataCache_dispose(dir.get());

            return Util::TE_Ok;
        }
    }

    return Util::TE_InvalidArg;
}

