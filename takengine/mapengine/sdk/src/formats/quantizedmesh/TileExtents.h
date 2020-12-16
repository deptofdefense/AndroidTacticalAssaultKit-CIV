#pragma once

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

struct TileExtents {

    TileExtents(int startX, int startY, int endX, int endY, int level) {
        this->level = level;
        this->start_x = startX;
        this->startY = startY;
        this->endX = endX;
        this->endY = endY;
    }
    
    int level;

    int start_x;
    int startY;
    int endX;
    int endY;

    bool hasTile(int x, int y) {
        return x >= start_x && x <= endX && y >= startY && y <= endY;
    }
};

}
}
}
}
