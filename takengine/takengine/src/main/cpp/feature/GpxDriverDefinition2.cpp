#include "feature/GpxDriverDefinition2.h"

#include <ogr_feature.h>
#include <ogr_geometry.h>
#include <ogrsf_frmts.h>

#include "util/ConfigOptions.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

#define GPX_DRIVER_NAME "GPX"

GpxDriverDefinition2::GpxDriverDefinition2() NOTHROWS :
    DefaultDriverDefinition2(GPX_DRIVER_NAME, "GPX", 1)
{}

TAKErr GpxDriverDefinition2::skipLayer(bool *value, const OGRLayer& layer) NOTHROWS
{
    DefaultDriverDefinition2::skipLayer(value, layer);
    // Include 4 of 5 GDAL layers for GPX files: waypoints, routes, tracks, route_points
    // Skip track_points
    const char *layerName = const_cast<OGRLayer&>(layer).GetName();
    *value = !(TAK::Engine::Port::String_strcasecmp(layerName, "track_points") &&
               TAK::Engine::Port::String_strcasecmp(layerName, "route_points"));
    return TE_Ok;
}

TAKErr GpxDriverDefinition2::skipFeature(bool *value, const OGRFeature& feature) NOTHROWS
{
    *value = false;
    return TE_Ok;
}

TAKErr GpxDriverDefinition2::Spi::create(OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS
{
    value = OGRDriverDefinition2Ptr(new GpxDriverDefinition2(), Memory_deleter_const<OGRDriverDefinition2, GpxDriverDefinition2>);
    return TE_Ok;
}

const char *GpxDriverDefinition2::Spi::getType() const NOTHROWS
{
    return GPX_DRIVER_NAME;
}
