#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_INDEXDATA_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_INDEXDATA_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

#include "formats/quantizedmesh/impl/Indices.h"
#include "formats/quantizedmesh/impl/VertexData.h"

#include <vector>

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

/**
 * Indices which make up the triangles in this elevation mesh
 */
class IndexData {
public:
    

    int get(int i) const;
    bool is32Bit() const;
    int getLength() const;
    int64_t getTotalSize() const;
    const std::vector<int> *getTriangleIndices(int level, int x, int y);

    static const int QUAD_MIN_LEVEL = 10;
    static const int QUAD_MAX_LEVEL = 15;

private:
    int64_t totalSize;
    const int triangleCount;
    const std::unique_ptr<Indices> indices;
    std::vector<std::vector<std::vector<std::unique_ptr<std::vector<int>>>>> quadtree;

    IndexData(int triangleCount, std::unique_ptr<Indices> indices);

    friend Util::TAKErr IndexData_deserialize(std::unique_ptr<IndexData> &result, const VertexData &vertexData, Util::DataInput2 &input);
};

Util::TAKErr IndexData_deserialize(std::unique_ptr<IndexData> &result, const VertexData &vertexData, Util::DataInput2 &input);

}
}
}
}
}

#endif
