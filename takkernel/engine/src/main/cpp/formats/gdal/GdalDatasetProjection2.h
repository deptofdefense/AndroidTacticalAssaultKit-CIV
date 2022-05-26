#ifndef TAK_ENGINE_FORMATS_GDAL_GDALDATASETPROJECTION2_H_INCLUDED
#define TAK_ENGINE_FORMATS_GDAL_GDALDATASETPROJECTION2_H_INCLUDED

#include "port/Platform.h"
#include "raster/DatasetProjection2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace GDAL {
                ENGINE_API Util::TAKErr GdalDatasetProjection2_create(Raster::DatasetProjection2Ptr &value, const char *path) NOTHROWS;
            }
        }
    }
}
#endif
