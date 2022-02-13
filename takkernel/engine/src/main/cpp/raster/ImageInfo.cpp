#include "raster/ImageInfo.h"

using namespace TAK::Engine::Raster;

using namespace TAK::Engine::Core;

ImageInfo::ImageInfo() NOTHROWS :
    precisionImagery(false),
    maxGsd(0.0),
    width(0),
    height(0),
    srid(-1)
{}
ImageInfo::ImageInfo(const char *path_,
                     const char *type_,
                     const bool precisionImagery_,
                     const GeoPoint2 &upperLeft_,
                     const GeoPoint2 &upperRight_,
                     const GeoPoint2 &lowerRight_,
                     const GeoPoint2 &lowerLeft_,
                     const double maxGsd_,
                     const int width_,
                     const int height_,
                     const int srid_) NOTHROWS :
    path(path_),
    type(type_),
    precisionImagery(precisionImagery_),
    upperLeft(upperLeft_),
    upperRight(upperRight_),
    lowerRight(lowerRight_),
    lowerLeft(lowerLeft_),
    maxGsd(maxGsd_),
    width(width_),
    height(height_),
    srid(srid_)
{}

ImageInfo::~ImageInfo() NOTHROWS
{ }

ImageInfo &ImageInfo::operator=(const ImageInfo &) = default;