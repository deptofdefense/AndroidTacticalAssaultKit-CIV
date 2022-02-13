#ifndef TAK_ENGINE_RASTER_IMAGEINFO_H_INCLUDED
#define TAK_ENGINE_RASTER_IMAGEINFO_H_INCLUDED

#include <memory>

#include "core/GeoPoint2.h"
#include "port/Platform.h"
#include "port/String.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            class ENGINE_API ImageInfo
            {
            public :
                ImageInfo() NOTHROWS;
                ImageInfo(const char *path,
                          const char *type,
                          const bool precisionImagery,
                          const Core::GeoPoint2 &upperLeft,
                          const Core::GeoPoint2 &upperRight,
                          const Core::GeoPoint2 &lowerRight,
                          const Core::GeoPoint2 &lowerLeft,
                          const double maxGsd,
                          const int width,
                          const int height,
                          const int srid) NOTHROWS;
                ~ImageInfo() NOTHROWS;
                ImageInfo &operator=(const ImageInfo &);
            public :
                Port::String path;
                Port::String type;
                bool precisionImagery;
                Core::GeoPoint2 upperLeft;
                Core::GeoPoint2 upperRight;
                Core::GeoPoint2 lowerRight;
                Core::GeoPoint2 lowerLeft;
                double maxGsd;
                size_t width;
                size_t height;
                int srid;
            };

            typedef std::unique_ptr<ImageInfo, void(*)(const ImageInfo *)> ImageInfoPtr;
            typedef std::unique_ptr<const ImageInfo, void(*)(const ImageInfo *)> ImageInfoPtr_const;
        }
    }
}

#endif

