#include "formats/quantizedmesh/TileCoord.h"

#include <cmath>

namespace {
    struct LocalConsts {
    private:
        static const int numSpacings = 32;

    public:
        // Geodetic spacing at each level
        // Here we calculate the spacing of each level ahead of time to save processing
        double spacing[numSpacings];

        LocalConsts()
        {
            for (int i = 0; i < numSpacings; ++i)
                spacing[i] = 180.0 / (1 << i);
        }
    };

    const LocalConsts LOCAL_CONSTS;

    // The max GSD used for mobile imagery datasets
    const double DEFAULT_MAX_GSD = 156542.9878125;
}

double TAK::Engine::Formats::QuantizedMesh::TileCoord_getLatitude(double y, int level)
{
    return (y * TileCoord_getSpacing(level)) - 90;
}

double TAK::Engine::Formats::QuantizedMesh::TileCoord_getLongitude(double x, int level)
{
    return (x * TileCoord_getSpacing(level)) - 180;
}

double TAK::Engine::Formats::QuantizedMesh::TileCoord_getTileX(double lng, int level)
{
    return (lng + 180) / TileCoord_getSpacing(level);
}

double TAK::Engine::Formats::QuantizedMesh::TileCoord_getTileY(double lat, int level)
{
    return (lat + 90) / TileCoord_getSpacing(level);
}

int TAK::Engine::Formats::QuantizedMesh::TileCoord_getLevel(double gsd)
{
    return (int) round(log2(DEFAULT_MAX_GSD / gsd));
}

double TAK::Engine::Formats::QuantizedMesh::TileCoord_getSpacing(int level)
{
    return LOCAL_CONSTS.spacing[level];
}

double TAK::Engine::Formats::QuantizedMesh::TileCoord_getGSD(int level)
{
    return DEFAULT_MAX_GSD / (1 << level);
}
