#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILEEXTENTS_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILEEXTENTS_H_INCLUDED

#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

/**
 * Tile extents metadata
 */
struct ENGINE_API TileExtents {

    TileExtents();

    TileExtents(int startX, int startY, int endX, int endY, int level);
    
    int level;

    int startX;
    int startY;
    int endX;
    int endY;

    bool hasTile(int x, int y) const;

    bool operator==(const TileExtents &b) const;
};


}
}
}
}

#endif
