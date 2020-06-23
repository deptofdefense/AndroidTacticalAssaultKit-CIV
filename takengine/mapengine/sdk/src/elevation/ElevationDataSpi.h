#ifndef TAK_ENGINE_ELEVATION_ELEVATIONDATASPI_H_INCLUDED
#define TAK_ENGINE_ELEVATION_ELEVATIONDATASPI_H_INCLUDED

#include "elevation/ElevationData.h"
#include "port/Platform.h"
#include "raster/ImageInfo.h"
#include "util/Error.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace Elevation {
            class ENGINE_API ElevationDataSpi : TAK::Engine::Util::NonCopyable
            {
            protected:
                virtual ~ElevationDataSpi() NOTHROWS = 0;
            public:
                virtual int getPriority() const NOTHROWS = 0;
                virtual Util::TAKErr create(ElevationDataPtr &value, const Raster::ImageInfo &info) NOTHROWS = 0;
            };
        }
    }
}
#endif
