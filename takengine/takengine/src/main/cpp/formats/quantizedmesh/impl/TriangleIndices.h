#pragma once

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

struct TriangleIndices {
    static const int MIN_LEVEL = 10;
    static const int MAX_LEVEL = 15;

    static const int LEVEL_COUNT = (MAX_LEVEL - MIN_LEVEL) + 1;
    // static int* sizes;
};

}
}
}
}
