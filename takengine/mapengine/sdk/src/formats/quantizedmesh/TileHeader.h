#pragma once
#include "util/DataInput2.h"

struct TileHeader {
    // The center of the tile in Earth-centered Fixed coordinates.
    double centerX;
    double centerY;
    double centerZ;
    
    // The minimum and maximum heights in the area covered by this tile.
    // The minimum may be lower and the maximum may be higher than
    // the height of any vertex in this tile in the case that the min/max vertex
    // was removed during mesh simplification, but these are the appropriate
    // values to use for analysis or visualization.
    float minimumHeight;
    float maximumHeight;

    // The tile’s bounding sphere.  The X,Y,Z coordinates are again expressed
    // in Earth-centered Fixed coordinates, and the radius is in meters.
    double boundingSphereCenterX;
    double boundingSphereCenterY;
    double boundingSphereCenterZ;
    double boundingSphereRadius;

    // The horizon occlusion point, expressed in the ellipsoid-scaled Earth-centered Fixed frame.
    // If this point is below the horizon, the entire tile is below the horizon.
    // See http://cesiumjs.org/2013/04/25/Horizon-culling/ for more information.
    double horizonOcclusionPointX;
    double horizonOcclusionPointY;
    double horizonOcclusionPointZ;

    TileHeader(TAK::Engine::Util::FileInput2* buffer) {
        buffer->readDouble(&centerX);
        buffer->readDouble(&centerY);
        buffer->readDouble(&centerZ);

        buffer->readFloat(&minimumHeight);
        buffer->readFloat(&maximumHeight);
        
        buffer->readDouble(&boundingSphereCenterX);
        buffer->readDouble(&boundingSphereCenterY);
        buffer->readDouble(&boundingSphereCenterZ);
        buffer->readDouble(&boundingSphereRadius);
        
        buffer->readDouble(&horizonOcclusionPointX);
        buffer->readDouble(&horizonOcclusionPointY);
        buffer->readDouble(&horizonOcclusionPointZ);
    }

    float getHeight() const {
        return maximumHeight - minimumHeight;
    }
};
