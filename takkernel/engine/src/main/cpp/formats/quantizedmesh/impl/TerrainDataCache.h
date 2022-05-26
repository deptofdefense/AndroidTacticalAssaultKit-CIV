#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_TERRAINDATACACHE_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_TERRAINDATACACHE_H_INCLUDED

#include "formats/quantizedmesh/impl/TerrainData.h"
#include "formats/quantizedmesh/impl/TileCoordImpl.h"
#include "formats/quantizedmesh/QMESourceLayer.h"
#include "port/Platform.h"
#include "util/Error.h"


namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

    /**
     * Singlton-backed implementation that caches terrain data so we don't need to decompress the file every read
     */

     
     /**
      * Clear the entire cache
      */
    void TerrainDataCache_clear();

    /**
      * Dispose all cache data under a given directory
      * @param dir Root directory to search
      */
    void TerrainDataCache_dispose(const char *dir);
    std::shared_ptr<TerrainData> TerrainDataCache_getData(const char *file, int level);
    std::shared_ptr<TerrainData> TerrainDataCache_getData(const QMESourceLayer &layer, const TileCoordImpl &tile);

}
}
}
}
}

#endif

