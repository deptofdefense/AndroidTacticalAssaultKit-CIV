#pragma once

#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"
#include "core/GeoPoint2.h"
#include "math/Point2.h"
#include "raster/ImageInfo.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            class ENGINE_API RasterDataAccess2
            {
               protected:
                virtual ~RasterDataAccess2() NOTHROWS = 0;

               public:
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS = 0;
                virtual Util::TAKErr imageToGround(Core::GeoPoint2 *ground, bool *isPrecise, const Math::Point2<double> &image) NOTHROWS = 0;
                virtual Util::TAKErr groundToImage(Math::Point2<double> *image, bool *isPrecise, const Core::GeoPoint2 &ground) NOTHROWS = 0;
                virtual Util::TAKErr getType(Port::String &value) NOTHROWS = 0;
                virtual Util::TAKErr getSpatialReferenceId(int *value) NOTHROWS = 0;
                virtual Util::TAKErr hasPreciseCoordinates(bool *value) NOTHROWS = 0;
                virtual Util::TAKErr getWidth(int *value) NOTHROWS = 0;
                virtual Util::TAKErr getHeight(int *value) NOTHROWS = 0;
                virtual Util::TAKErr getImageInfo(ImageInfoPtr_const &info) NOTHROWS = 0;

                        
            };
            typedef std::unique_ptr<RasterDataAccess2, void (*)(const RasterDataAccess2 *)> RasterDataAccess2Ptr;
        }
    }
}
