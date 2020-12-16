#include "core/ProjectionFactory.h"

#include <map>

#include "core/LegacyAdapters.h"
#include "core/Projection.h"
#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "core/ProjectionSpi.h"
#include "core/ProjectionSpi3.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Error.h"
#include "util/Memory.h"


using namespace atakmap::core;

using namespace atakmap::math;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace // unnamed namespace
{
    Mutex &mutex() NOTHROWS;
    std::map<ProjectionSpi *, ProjectionSpi3 *>& adaptedSpis() NOTHROWS;
}; // end unnamed namespace

/******************************************************************************/
// Projection Factory definition

ProjectionFactory::ProjectionFactory()
{}

ProjectionFactory::~ProjectionFactory()
{}

Projection *ProjectionFactory::getProjection(int srid)
{
    // obtain the projection
    Projection2Ptr value(nullptr, nullptr);
    if (ProjectionFactory3_create(value, srid) != TE_Ok)
        return nullptr;

    // adapt the projection back to legacy
    ProjectionPtr2 lvalue(nullptr, nullptr);
    if (LegacyAdapters_adapt(lvalue, std::move(value)) != TE_Ok)
        return nullptr;

    // release the raw pointer. we're depending on implementation of the
    // LegacyAdapter code here!!!
    return lvalue.release();
}

void ProjectionFactory::registerSpi(ProjectionSpi *lspi)
{
    ProjectionSpi3Ptr spi(nullptr, nullptr);
    {
        Lock lock(mutex());

        std::map<ProjectionSpi *, ProjectionSpi3 *> &spis = adaptedSpis();
        std::map<ProjectionSpi *, ProjectionSpi3 *>::iterator entry;
        entry = spis.find(lspi);
        if (entry != spis.end())
            return;
        
        if (LegacyAdapters_adapt(spi, std::unique_ptr<ProjectionSpi, void(*)(const ProjectionSpi *)>(lspi, Memory_leaker_const<ProjectionSpi>)) != TE_Ok)
            return;

        spis[lspi] = spi.get();
    }

    ProjectionFactory3_registerSpi(std::move(spi), 0);
}

void ProjectionFactory::unregisterSpi(ProjectionSpi *lspi)
{
    ProjectionSpi3 *spi = nullptr;
    {
        Lock lock(mutex());

        std::map<ProjectionSpi *, ProjectionSpi3 *> &spis = adaptedSpis();
        std::map<ProjectionSpi *, ProjectionSpi3 *>::iterator entry;
        entry = spis.find(lspi);
        if (entry == spis.end())
            return;

        spi = entry->second;
        spis.erase(entry);
    }

    ProjectionFactory3_unregisterSpi(*spi);
}

void ProjectionFactory::setPreferSdkProjections(bool sdk)
{
    ProjectionFactory3_setPreferSdkProjections(sdk);
}

/*****************************************************************************/

namespace
{
    Mutex &mutex() NOTHROWS
    {
        static Mutex m;
        return m;
    }

    std::map<ProjectionSpi *, ProjectionSpi3 *>& adaptedSpis() NOTHROWS
    {
        static std::map<ProjectionSpi *, ProjectionSpi3 *> s;
        return s;
    }
} // end unnamed namespace

