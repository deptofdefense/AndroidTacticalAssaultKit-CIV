#ifndef TAK_ENGINE_FORMATS_OSMDROID_OSMDROIDCONTAINER_H_INCLUDED
#define TAK_ENGINE_FORMATS_OSMDROID_OSMDROIDCONTAINER_H_INCLUDED

#include "db/Database2.h"
#include "raster/tilematrix/TileContainer.h"
#include "raster/tilematrix/TileContainerFactory.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace OSMDroid {
                ENGINE_API Util::TAKErr OSMDroidContainer_openOrCreate(Raster::TileMatrix::TileContainerPtr& value, const char *path, const char *provider, const int srid) NOTHROWS;
                ENGINE_API std::shared_ptr<Raster::TileMatrix::TileContainerSpi> OSMDroidContainer_spi() NOTHROWS;
            }
        }
    }
}
#endif
