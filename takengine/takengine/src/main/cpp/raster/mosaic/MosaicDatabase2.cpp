#include "raster/mosaic/MosaicDatabase2.h"

#include <cmath>

#include "port/Collections.h"
#include "port/STLSetAdapter.h"
#include "port/String.h"
#include "util/Memory.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Raster::Mosaic;
using namespace TAK::Engine::Util;

namespace
{
    double min(double a, double b, double c, double d)
    {
        double     v = a;
        if (b < v) v = b;
        if (c < v) v = c;
        if (d < v) v = d;
        return v;
    }

    double max(double a, double b, double c, double d)
    {
        double     v = a;
        if (b > v) v = b;
        if (c > v) v = c;
        if (d > v) v = d;
        return v;
    }
}

MosaicDatabase2::~MosaicDatabase2() NOTHROWS
{}

/**************************************************************************/
// MosaicDatabase2::QueryParameters

MosaicDatabase2::QueryParameters::QueryParameters() NOTHROWS :
    path(nullptr),
    spatialFilter(nullptr, nullptr),
    minGsd(NAN),
    maxGsd(NAN),
    types(nullptr, nullptr),
    srid(-1),
    imagery(QueryParameters::AllImagery),
    minGsdCompare(MaximumGsd),
    maxGsdCompare(MaximumGsd),
    order(MaxGsdDesc)
{}
        
MosaicDatabase2::QueryParameters::QueryParameters(const QueryParameters &other) NOTHROWS :
    path(other.path),
    spatialFilter(nullptr, nullptr),
    minGsd(other.minGsd),
    maxGsd(other.maxGsd),
    types(nullptr, nullptr),
    srid(other.srid),
    imagery(other.imagery),
    minGsdCompare(other.minGsdCompare),
    maxGsdCompare(other.maxGsdCompare),
    order(other.order)
{
    if (other.spatialFilter.get()) {
        TAKErr code(TE_Ok);
        code = Geometry_clone(spatialFilter, *other.spatialFilter);
        if (code != TE_Ok) {
            // XXX - 
        }
    }

    if (other.types.get()) {
        types = TAK_UNIQUE_PTR(Port::Set<Port::String>)(new Port::STLSetAdapter<Port::String, Port::StringLess>(), Util::Memory_deleter_const<Port::Set<Port::String>, Port::STLSetAdapter<Port::String, Port::StringLess>>);
        Port::Collections_addAll(*types, *other.types);
    }
}

MosaicDatabase2::QueryParameters::~QueryParameters() NOTHROWS
{}

MosaicDatabase2::Cursor::~Cursor() NOTHROWS
{}


MosaicDatabase2::Frame::Frame(const int id_,
                              const char *path_,
                              const char *type_,
                              const bool precisionImagery_,
                              const GeoPoint2 &upperLeft_,
                              const GeoPoint2 &upperRight_,
                              const GeoPoint2 &lowerRight_,
                              const GeoPoint2 &lowerLeft_,
                              const double minGsd_,
                              const double maxGsd_,
                              const int width_,
                              const int height_,
                              const int srid_) NOTHROWS :
    Raster::ImageInfo(path_,
                      type_,
                      precisionImagery_,
                      upperLeft_,
                      upperRight_,
                      lowerRight_,
                      lowerLeft_,
                      maxGsd_,
                      width_,
                      height_,
                      srid_),
    id(id_),
    minLat(min(upperLeft_.latitude, upperRight_.latitude, lowerRight_.latitude, lowerLeft_.latitude)),
    minLon(min(upperLeft_.longitude, upperRight_.longitude, lowerRight_.longitude, lowerLeft_.longitude)),
    maxLat(max(upperLeft_.latitude, upperRight_.latitude, lowerRight_.latitude, lowerLeft_.latitude)),
    maxLon(max(upperLeft_.longitude, upperRight_.longitude, lowerRight_.longitude, lowerLeft_.longitude)),
    minGsd(minGsd_)
{}
    
MosaicDatabase2::Frame::Frame(const int id_,
                              const char *path_,
                              const char *type_,
                              const bool precisionImagery_,
                              const double minLat_,
                              const double minLon_,
                              const double maxLat_,
                              const double maxLon_,
                              const GeoPoint2 &upperLeft_,
                              const GeoPoint2 &upperRight_,
                              const GeoPoint2 &lowerRight_,
                              const GeoPoint2 &lowerLeft_,
                              const double minGsd_,
                              const double maxGsd_,
                              const int width_,
                              const int height_,
                              const int srid_) NOTHROWS :
    Raster::ImageInfo(path_,
                      type_,
                      precisionImagery_,
                      upperLeft_,
                      upperRight_,
                      lowerRight_,
                      lowerLeft_,
                      maxGsd_,
                      width_,
                      height_,
                      srid_),
    id(id_),
    minLat(minLat_),
    minLon(minLon_),
    maxLat(maxLat_),
    maxLon(maxLon_),
    minGsd(minGsd_)
{}

Util::TAKErr MosaicDatabase2::Frame::createFrame(MosaicDatabase2::FramePtr_const &frame, MosaicDatabase2::Cursor &row) NOTHROWS
{
    TAKErr code(TE_Ok);

    int id;
    code = row.getId(&id);
    TE_CHECKRETURN_CODE(code);

    const char *path;
    code = row.getPath(&path);
    TE_CHECKRETURN_CODE(code);

    const char *type;
    code = row.getType(&type);
    TE_CHECKRETURN_CODE(code);

    bool precisionImagery;
    code = row.isPrecisionImagery(&precisionImagery);
    TE_CHECKRETURN_CODE(code);

    double minLat;
    code = row.getMinLat(&minLat);
    TE_CHECKRETURN_CODE(code);

    double minLon;
    code = row.getMinLon(&minLon);
    TE_CHECKRETURN_CODE(code);

    double maxLat;
    code = row.getMaxLat(&maxLat);
    TE_CHECKRETURN_CODE(code);

    double maxLon;
    code = row.getMaxLon(&maxLon);
    TE_CHECKRETURN_CODE(code);

    GeoPoint2 upperLeft;
    code = row.getUpperLeft(&upperLeft);
    TE_CHECKRETURN_CODE(code);

    GeoPoint2 upperRight;
    code = row.getUpperRight(&upperRight);
    TE_CHECKRETURN_CODE(code);

    GeoPoint2 lowerRight;
    code = row.getLowerRight(&lowerRight);
    TE_CHECKRETURN_CODE(code);

    GeoPoint2 lowerLeft;
    code = row.getLowerLeft(&lowerLeft);
    TE_CHECKRETURN_CODE(code);

    double minGsd;
    code = row.getMinGSD(&minGsd);
    TE_CHECKRETURN_CODE(code);

    double maxGsd;
    code = row.getMaxGSD(&maxGsd);
    TE_CHECKRETURN_CODE(code);

    int width;
    code = row.getWidth(&width);
    TE_CHECKRETURN_CODE(code);

    int height;
    code = row.getHeight(&height);
    TE_CHECKRETURN_CODE(code);

    int srid;
    code = row.getSrid(&srid);
    TE_CHECKRETURN_CODE(code);

    frame = FramePtr_const(new Frame(id,
                                     path,
                                     type,
                                     precisionImagery,
                                     minLat,
                                     minLon,
                                     maxLat,
                                     maxLon,
                                     upperLeft,
                                     upperRight,
                                     lowerRight,
                                     lowerLeft,
                                     minGsd,
                                     maxGsd,
                                     width,
                                     height,
                                     srid),
                           Memory_deleter_const<Frame>);

    return code;
}


MosaicDatabase2::Coverage::Coverage(Geometry2Ptr_const &&geometry_, const double minGSD_, const double maxGSD_) NOTHROWS :
    geometry(std::move(geometry_)),
    minGSD(minGSD_),
    maxGSD(maxGSD_)
{}
