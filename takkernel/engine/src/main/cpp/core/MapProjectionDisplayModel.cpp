#include "core/MapProjectionDisplayModel.h"

#include <map>

#include <thread/Lock.h>
#include <thread/Mutex.h>

#include "math/Plane2.h"
#include "math/Vector4.h"
#include "util/Memory.h"

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    std::map<int, std::shared_ptr<MapProjectionDisplayModel>> &registry()
    {
        static std::map<int, std::shared_ptr<MapProjectionDisplayModel>> r;
        return r;
    }

    Mutex &mutex()
    {
        static Mutex m;
        return m;
    }

    Plane2 defaultPlane(Vector4<double>(0.0, 0.0, 1.0), Point2<double>(0.0, 0.0, 0.0));
}

MapProjectionDisplayModel::MapProjectionDisplayModel(const int srid_,
                                                     GeometryModel2Ptr &&earth_,
                                                     const double projectionXToNominalMeters_,
                                                     const double projectionYToNominalMeters_,
                                                     const double projectionZToNominalMeters_,
                                                     const bool zIsHeight_) NOTHROWS :
    srid(srid_),
    earth(std::move(earth_)),
    projectionXToNominalMeters(projectionXToNominalMeters_),
    projectionYToNominalMeters(projectionYToNominalMeters_),
    projectionZToNominalMeters(projectionZToNominalMeters_),
    zIsHeight(zIsHeight_)
{}

TAKErr TAK::Engine::Core::MapProjectionDisplayModel_registerModel(MapProjectionDisplayModelPtr &&model) NOTHROWS
{
    return MapProjectionDisplayModel_registerModel(std::shared_ptr<MapProjectionDisplayModel>(std::move(model)));
}

TAKErr TAK::Engine::Core::MapProjectionDisplayModel_registerModel(const std::shared_ptr<MapProjectionDisplayModel> &model) NOTHROWS
{
    if (!model.get())
        return TE_InvalidArg;
    Lock lock(mutex());
    TE_CHECKRETURN_CODE(lock.status);
    registry()[model->srid] = model;
    return TE_Ok;
}


TAKErr TAK::Engine::Core::MapProjectionDisplayModel_unregisterModel(MapProjectionDisplayModel &model) NOTHROWS
{
    Lock lock(mutex());
    TE_CHECKRETURN_CODE(lock.status);
    std::map<int, std::shared_ptr<MapProjectionDisplayModel>> &reg = registry();
    std::map<int, std::shared_ptr<MapProjectionDisplayModel>>::iterator entry;
    entry = reg.find(model.srid);
    if ((entry == reg.end()) || (entry->second.get() != &model))
        return TE_InvalidArg;
    reg.erase(entry);
    return TE_Ok;
}


TAKErr TAK::Engine::Core::MapProjectionDisplayModel_getModel(std::shared_ptr<MapProjectionDisplayModel> &value, const int srid) NOTHROWS
{
    Lock lock(mutex());
    TE_CHECKRETURN_CODE(lock.status);
    std::map<int, std::shared_ptr<MapProjectionDisplayModel>> &reg = registry();
    std::map<int, std::shared_ptr<MapProjectionDisplayModel>>::iterator it;
    it = reg.find(srid);
    if (it == reg.end())
        return TE_InvalidArg;
    value = it->second;
    return TE_Ok;
}

TAKErr TAK::Engine::Core::MapProjectionDisplayModel_createDefaultLLAPlanarModel(MapProjectionDisplayModelPtr &value, const int srid) NOTHROWS
{
    // NOTE: we are using nominal conversions to meters for latitude and
    //       longitude at 0N, derived from
    //https://msi.nga.mil/MSISiteContent/StaticFiles/Calculators/degree.html
    value = MapProjectionDisplayModelPtr(
                new MapProjectionDisplayModel(srid,
                                              std::move(GeometryModel2Ptr(&defaultPlane, Memory_leaker_const<GeometryModel2>)),
                                              111319.0,
                                              110574.0,
                                              1.0,
                                              true),
                Memory_deleter_const<MapProjectionDisplayModel>);
    return TE_Ok;
}



TAKErr TAK::Engine::Core::MapProjectionDisplayModel_createDefaultENAPlanarModel(MapProjectionDisplayModelPtr &value, const int srid) NOTHROWS
{
    value = MapProjectionDisplayModelPtr(
                new MapProjectionDisplayModel(srid,
                                              std::move(GeometryModel2Ptr(&defaultPlane, Memory_leaker_const<GeometryModel2>)),
                                              1.0,
                                              1.0,
                                              1.0,
                                              true),
                Memory_deleter_const<MapProjectionDisplayModel>);
    return TE_Ok;
}