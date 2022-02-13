#include "elevation/ElevationData.h"

using namespace TAK::Engine::Elevation;

#if 0
ElevationData::~ElevationData() NOTHROWS
{}

ElevationData::Hints::Hints() NOTHROWS :
    preferSpeed(false),
    resolution(NAN),
    interpolate(true),
    bounds(atakmap::feature::Envelope(NAN, NAN, NAN, NAN, NAN, NAN))
{}

ElevationData::Hints::Hints(const Hints &other) NOTHROWS :
    preferSpeed(other.preferSpeed),
    resolution(other.resolution),
    interpolate(other.interpolate),
    bounds(other.bounds)
{}

ElevationData::Hints::Hints(const bool preferSpeed, const double resolution, const bool interpolate, const atakmap::feature::Envelope &bounds) NOTHROWS :
    preferSpeed(preferSpeed),
    resolution(resolution),
    interpolate(interpolate),
    bounds(atakmap::feature::Envelope(
    bounds.minX, bounds.minY, bounds.minZ,
    bounds.maxX, bounds.maxY, bounds.maxZ))
{}
#endif
