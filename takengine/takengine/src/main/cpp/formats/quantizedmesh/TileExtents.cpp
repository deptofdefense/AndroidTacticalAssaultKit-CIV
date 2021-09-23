#include "formats/quantizedmesh/TileExtents.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;

TileExtents::TileExtents() : level(0), startX(0), startY(0), endX(0), endY(0)
{
}

TileExtents::TileExtents(int startX, int startY, int endX, int endY, int level) : 
    level(level),
    startX(startX),
    startY(startY),
    endX(endX),
    endY(endY)
{
}
    
bool TileExtents::hasTile(int x, int y) const
{
    return x >= startX && x <= endX && y >= startY && y <= endY;
}

bool TileExtents::operator==(const TileExtents &b) const
{
    return level == b.level && startX == b.startX && startY == b.startY && endX == b.endX && endY == b.endY;
}

