#include "feature/ShapefileDriverDefinition2.h"

#include <ogr_feature.h>
#include <ogr_geometry.h>
#include <ogrsf_frmts.h>

#include "util/ConfigOptions.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

#define SHAPEFILE_DRIVER_NAME "ESRI Shapefile"

ShapefileDriverDefinition2::ShapefileDriverDefinition2() NOTHROWS :
    DefaultDriverDefinition2(SHAPEFILE_DRIVER_NAME, "shp", 1)
{}

TAKErr ShapefileDriverDefinition2::Spi::create(OGRDriverDefinition2Ptr &value, const char *path) NOTHROWS
{
    value = OGRDriverDefinition2Ptr(new ShapefileDriverDefinition2(), Memory_deleter_const<OGRDriverDefinition2, ShapefileDriverDefinition2>);
    return TE_Ok;
}

const char *ShapefileDriverDefinition2::Spi::getType() const NOTHROWS
{
    return SHAPEFILE_DRIVER_NAME;
}
