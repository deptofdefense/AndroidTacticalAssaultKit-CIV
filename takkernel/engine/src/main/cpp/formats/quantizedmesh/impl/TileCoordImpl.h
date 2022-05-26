#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILECOORDIMPL_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILECOORDIMPL_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"
#include "feature/Polygon2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

    /**
     * A tile coordinate vector
     */
    struct TileCoordImpl {
        const int x;
        const int y;
        const int z;

        TileCoordImpl(int x, int y, int z);
        TileCoordImpl(double lat, double lon, int z);

        Util::TAKErr getCoverage(Feature::Geometry2Ptr &result);
    };
}
}
}
}
}

#endif

