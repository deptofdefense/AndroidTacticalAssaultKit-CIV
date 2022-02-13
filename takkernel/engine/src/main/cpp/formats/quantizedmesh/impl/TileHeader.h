#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILEHEADER_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_TILEHEADER_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"
#include "util/DataInput2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

class TileHeader {
public:
    static const int TOTAL_SIZE = (10 * 8) + (3 * 4);

    // The center of the tile in Earth-centered Fixed coordinates.
    const double centerX;
    const double centerY;
    const double centerZ;
    
    // The minimum and maximum heights in the area covered by this tile.
    // The minimum may be lower and the maximum may be higher than
    // the height of any vertex in this tile in the case that the min/max vertex
    // was removed during mesh simplification, but these are the appropriate
    // values to use for analysis or visualization.
    const float minimumHeight;
    const float maximumHeight;

    // The tile’s bounding sphere.  The X,Y,Z coordinates are again expressed
    // in Earth-centered Fixed coordinates, and the radius is in meters.
    const double boundingSphereCenterX;
    const double boundingSphereCenterY;
    const double boundingSphereCenterZ;
    const double boundingSphereRadius;

    // The horizon occlusion point, expressed in the ellipsoid-scaled Earth-centered Fixed frame.
    // If this point is below the horizon, the entire tile is below the horizon.
    // See http://cesiumjs.org/2013/04/25/Horizon-culling/ for more information.
    const double horizonOcclusionPointX;
    const double horizonOcclusionPointY;
    const double horizonOcclusionPointZ;

    float getHeight() const;

  private:
    TileHeader(double cx, double cy, double cz, float minH, float maxH, double bscX, double bscY, double bscZ, double bsr, double hoX, double hoY, double hoZ);

    friend Util::TAKErr TileHeader_deserialize(std::unique_ptr<TileHeader> &result, Util::DataInput2 &input);
};

Util::TAKErr TileHeader_deserialize(std::unique_ptr<TileHeader> &result, Util::DataInput2 &input);

}
}
}
}
}

#endif
