#include "elevation/AbstractElevationData.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;

AbstractElevationData::AbstractElevationData(int model, Port::String type, double resolution) NOTHROWS :
    model(model),
    type(type),
    resolution(resolution)
{}

AbstractElevationData::~AbstractElevationData() NOTHROWS
{
    // Nothing to do. Defined because the base method is defined as virtual.
}

int AbstractElevationData::getElevationModel() NOTHROWS 
{
    return model;
}

Util::TAKErr AbstractElevationData::getType(Port::String &datatype) NOTHROWS
{
    datatype = Port::String(type);

    return Util::TE_Ok;
}

double AbstractElevationData::getResolution() NOTHROWS
{
    return resolution;
}

Util::TAKErr AbstractElevationData::getElevation(
    double *elevations,
    Port::Collection<GeoPoint2>::IteratorPtr &points,
    const Hints &hint) NOTHROWS
{
    // Note: Hint is ignored in this implementation, which is based on the ATAK implementation.

    Util::TAKErr code(Util::TE_Ok);
    std::size_t idx = 0;
    do {
        GeoPoint2 point;
        code = points->get(point);
        TE_CHECKBREAK_CODE(code);

        double el(NAN);
        getElevation(&el, point.latitude, point.longitude);
        elevations[idx++] = el;

        code = points->next();
        TE_CHECKBREAK_CODE(code);
    } while (true);

    if (code == Util::TE_Done)
        code = Util::TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}
