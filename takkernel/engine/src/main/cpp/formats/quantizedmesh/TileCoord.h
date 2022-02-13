#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILECOORD_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILECOORD_H_INCLUDED

#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

    /*
     * Utility functions for dealing with QM Tile Coordinate Vectors
     */

    ENGINE_API double TileCoord_getLatitude(double yCoord, int level);
    ENGINE_API double TileCoord_getLongitude(double xCoord, int level);
    ENGINE_API double TileCoord_getSpacing(int level);
    ENGINE_API double TileCoord_getTileX(double lng, int level);
    ENGINE_API double TileCoord_getTileY(double lat, int level);
    ENGINE_API int TileCoord_getLevel(double gsd);
    ENGINE_API double TileCoord_getGSD(int level);

}
}
}
}

#endif
