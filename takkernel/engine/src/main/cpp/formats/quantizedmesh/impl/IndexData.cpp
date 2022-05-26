#include "formats/quantizedmesh/impl/IndexData.h"
#include "formats/quantizedmesh/impl/TerrainData.h"
#include "math/Utils.h"


using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;

namespace {
    // From Java reference "TriangleIndices"
    struct LocalQuadConsts {
        static const int levelCount = IndexData::QUAD_MAX_LEVEL - IndexData::QUAD_MIN_LEVEL + 1;

        int sizes[levelCount];

        LocalQuadConsts()
        {
            for (int i = 0; i < levelCount; ++i)
                sizes[i] = 1 << (IndexData::QUAD_MAX_LEVEL - i);
        }
    };

    const LocalQuadConsts QUAD_CONSTS;
}


IndexData::IndexData(int triangleCount, std::unique_ptr<Indices> indices) : 
    totalSize(0),
    triangleCount(triangleCount),
    indices(std::move(indices)),
    quadtree()
{
}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::IndexData_deserialize(std::unique_ptr<IndexData> &result, const VertexData &vertexData, Util::DataInput2 &input) {
    Util::TAKErr code = Util::TE_Ok;
    int length = 0;

    {
        int triangleCount;
        code = input.readInt(&triangleCount);
        TE_CHECKRETURN_CODE(code);
        bool is32Bit = vertexData.vertexCount > (1 << 16);
        length = triangleCount * 3;

        std::unique_ptr<Indices> indices;
        code = Indices_deserialize(indices, length, is32Bit, true, input);
        TE_CHECKRETURN_CODE(code);

        result.reset(new IndexData(triangleCount, std::move(indices)));
    }

    result->quadtree.resize(QUAD_CONSTS.levelCount);
    for (int l = 0; l < QUAD_CONSTS.levelCount; l++) {
        int m = 1 << l;
        result->quadtree[l].resize(m);
        for (int z = 0; z < m; z++) {
            result->quadtree[l][z].resize(m);
        }
    }

    int indPerTri = 0;
    int triIndex = 0;
    int minX = TerrainData::MAX_RANGE;
    int minY = TerrainData::MAX_RANGE;
    int maxX = 0, maxY = 0;
    int quadTreeSize = 0;
    for (int i = 0; i < length; ++i) {

        int index = result->indices->get(i);

        // Update triangle min/max
        int x = vertexData.u.get()[index];
        int y = vertexData.v.get()[index];
        minX = atakmap::math::min(x, minX);
        minY = atakmap::math::min(y, minY);
        maxX = atakmap::math::max(x, maxX);
        maxY = atakmap::math::max(y, maxY);

        // Index triangle in the quad tree
        if (++indPerTri == 3) {
            int size = atakmap::math::max(maxX - minX, maxY - minY);
            int level = (int) ceil(log2(size));
            int l = IndexData::QUAD_MAX_LEVEL - level;
            if (l >= QUAD_CONSTS.levelCount)
                l = QUAD_CONSTS.levelCount - 1;
            else if (l < 0)
                l = 0;
            int d = QUAD_CONSTS.sizes[l];
            int minTX = minX / d;
            int minTY = minY / d;
            int maxTX = maxX / d;
            int maxTY = maxY / d;
            for (int ty = minTY; ty <= maxTY; ty++) {
                for (int tx = minTX; tx <= maxTX; tx++) {
                    std::vector<int> *ind = result->quadtree[l][tx][ty].get();
                    if (ind == nullptr) {
                        std::unique_ptr<std::vector<int>> nv(new std::vector<int>());
                        ind = nv.get();
                        result->quadtree[l][tx][ty] = std::move(nv);
                        quadTreeSize += 4;
                    }
                    ind->push_back(triIndex);
                    quadTreeSize += 4;
                }
            }

            // Reset for next triangle
            minX = minY = TerrainData::MAX_RANGE;
            maxX = maxY = 0;
            indPerTri = 0;
            triIndex++;
        }
    }

    // Total size in memory
    result->totalSize = 8 + 4 + 4 + result->indices->getTotalSize() + quadTreeSize;
    return Util::TE_Ok;
}


int IndexData::get(int i) const
{
    return indices->get(i);
}

bool IndexData::is32Bit() const
{
    return indices->getIs32Bit();
}

int64_t IndexData::getTotalSize() const
{
    return totalSize;
}

int IndexData::getLength() const
{
    return indices->getLength();
}

const std::vector<int> *IndexData::getTriangleIndices(int level, int x, int y)
{
    if (level < QUAD_MIN_LEVEL)
        level = QUAD_MIN_LEVEL;
    int l = QUAD_MAX_LEVEL - level;
    int tx = x / QUAD_CONSTS.sizes[l];
    int ty = y / QUAD_CONSTS.sizes[l];

    if (tx < 0)
        tx = 0;
    else if (tx >= quadtree[l].size())
        tx = (int)(quadtree[l].size() - 1);

    if (ty < 0)
        ty = 0;
    else if (ty >= quadtree[l][tx].size())
        ty = (int)(quadtree[l][tx].size() - 1);

    return quadtree[l][tx][ty].get();
}
