#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_EDGEINDICES_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_EDGEINDICES_H_INCLUDED

#include "Indices.h"
#include "VertexData.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {
namespace Impl {

    /**
     * Indices which point to vertices that are on the edge of the tile.
     * Note: Marked ENGINE_API for test purposes only
     */
    class ENGINE_API EdgeIndices {

    public:
        typedef enum {
            NORTH = 0,
            EAST = 1,
            SOUTH = 2,
            WEST = 3
        } Direction;

        ~EdgeIndices();

        const Indices *getIndicesForEdge(int edge) const;
        int64_t getTotalSize() const;

    private:
        const int64_t totalSize;
        std::unique_ptr<Indices> edges[4];

        EdgeIndices(int64_t totalSize, std::unique_ptr<Indices> north, std::unique_ptr<Indices> south, std::unique_ptr<Indices> east, std::unique_ptr<Indices> west);

        friend Util::TAKErr EdgeIndices_deserialize(std::unique_ptr<EdgeIndices> &result, const VertexData &vertexData, bool is32bit, Util::DataInput2 &input);
    };

    Util::TAKErr EdgeIndices_deserialize(std::unique_ptr<EdgeIndices> &result, const VertexData &vertexData, bool is32bit, Util::DataInput2 &input);
    Util::TAKErr EdgeIndices_deserializeOneEdge(std::unique_ptr<Indices> &result, int64_t *totalSize, EdgeIndices::Direction d, const VertexData &vertexData, bool is32bit, Util::DataInput2 &input);
}
}
}
}
}

#endif
