#include "formats/quantizedmesh/impl/EdgeIndices.h"


using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;


EdgeIndices::EdgeIndices(
    int64_t totalSize, 
    std::unique_ptr<Indices> north,
    std::unique_ptr<Indices> south,
    std::unique_ptr<Indices> east,
    std::unique_ptr<Indices> west) :
        totalSize(totalSize),
        edges { std::move(north), std::move(east), std::move(south), std::move(west) }
{
}

EdgeIndices::~EdgeIndices()
{
}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::EdgeIndices_deserializeOneEdge(
    std::unique_ptr<Indices> &result, 
    int64_t *totalSize,
    EdgeIndices::Direction d,
    const VertexData &vertexData,
    bool is32bit,
    Util::DataInput2 &input)
{
    int len;
    Util::TAKErr code = input.readInt(&len);
    TE_CHECKRETURN_CODE(code);
    code = Indices_deserialize(result, len, is32bit, false, input);
    TE_CHECKRETURN_CODE(code);

    result->sort(vertexData.u.get(), vertexData.v.get(), d == EdgeIndices::NORTH || d == EdgeIndices::SOUTH);

    *totalSize += result->getTotalSize();

    return code;
}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::EdgeIndices_deserialize(
    std::unique_ptr<EdgeIndices> &result,
    const VertexData &vertexData,
    bool is32bit,
    Util::DataInput2 &input)
{
    Util::TAKErr code = Util::TE_Ok;
    int64_t totalSize = 8;

    std::unique_ptr<Indices> west;
    code = EdgeIndices_deserializeOneEdge(west, &totalSize, EdgeIndices::WEST, vertexData, is32bit, input);
    TE_CHECKRETURN_CODE(code);
    std::unique_ptr<Indices> south;
    code = EdgeIndices_deserializeOneEdge(south, &totalSize, EdgeIndices::SOUTH, vertexData, is32bit, input);
    TE_CHECKRETURN_CODE(code);
    std::unique_ptr<Indices> east;
    code = EdgeIndices_deserializeOneEdge(east, &totalSize, EdgeIndices::EAST, vertexData, is32bit, input);
    TE_CHECKRETURN_CODE(code);
    std::unique_ptr<Indices> north;
    code = EdgeIndices_deserializeOneEdge(north, &totalSize, EdgeIndices::NORTH, vertexData, is32bit, input);
    TE_CHECKRETURN_CODE(code);

    result.reset(new EdgeIndices(totalSize, std::move(north), std::move(south), std::move(east), std::move(west)));
    return Util::TE_Ok;
}

const Indices *EdgeIndices::getIndicesForEdge(int edge) const
{
    return edges[edge].get();
}

int64_t EdgeIndices::getTotalSize() const
{
    return totalSize;
}


