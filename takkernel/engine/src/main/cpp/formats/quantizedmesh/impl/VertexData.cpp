#include "formats/quantizedmesh/impl/VertexData.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;


namespace {
    int zzDec(int value)
    {
        return (value >> 1) ^ (-(value & 1));
    }

    Util::TAKErr zzDec(int16_t *dst, int previous, Util::DataInput2 &input)
    {
        int16_t ss;
        Util::TAKErr code = input.readShort(&ss);
        TE_CHECKRETURN_CODE(code);
        int s = ss;
        if (s < 0)
            s += 65536;
        previous += zzDec(s);
        *dst = ((int16_t)previous);
        return Util::TE_Ok;
    }
}

VertexData::VertexData(int vertexCount, std::unique_ptr<int16_t[]> u, 
    std::unique_ptr<int16_t[]> v, std::unique_ptr<int16_t[]> height) : 
    vertexCount(vertexCount),
    u(std::move(u)), v(std::move(v)),
    height(std::move(height)), totalSize(8 + 4 + (vertexCount * 6))
{
}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::VertexData_deserialize(std::unique_ptr<VertexData> &result, Util::DataInput2 &input)
{
    Util::TAKErr code = Util::TE_Ok;
    int vertexCount;
    code = input.readInt(&vertexCount);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<int16_t[]> ubuf(new int16_t[vertexCount]);
    std::unique_ptr<int16_t[]> vbuf(new int16_t[vertexCount]);
    std::unique_ptr<int16_t[]> hbuf(new int16_t[vertexCount]);

    int u = 0, v = 0, height = 0;
    for (int i = 0; i < vertexCount; i++) {
        code = zzDec(ubuf.get() + i, u, input);
        TE_CHECKRETURN_CODE(code);
        u = ubuf[i];
    }
    for (int i = 0; i < vertexCount; i++) {
        code = zzDec(vbuf.get() + i, v, input);
        TE_CHECKRETURN_CODE(code);
        v = vbuf[i];
    }
    for (int i = 0; i < vertexCount; i++) {
        code = zzDec(hbuf.get() + i, height, input);
        TE_CHECKRETURN_CODE(code);
        height = hbuf[i];
    }

    result.reset(new VertexData(vertexCount, std::move(ubuf), std::move(vbuf), std::move(hbuf)));
    return Util::TE_Ok;
}

void VertexData::get(Math::Vector4<double> *result, int index) const
{
    result->x = u[index];
    result->y = v[index];
    result->z = height[index];
}

