#include "formats/quantizedmesh/impl/Indices.h"

#include <algorithm>


using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;


namespace {
    template <typename CmpInt>
    struct AdaptCmp16
    {
        CmpInt &cmp;

        AdaptCmp16(CmpInt &cmp) : cmp(cmp)
        {
        }

        virtual bool operator()(const int16_t &a16, const int16_t &b16) const
        {
            int a = a16;
            int b = b16;
            return cmp(a16, b16);
        }
    };


    struct IndexCompare {
        const int16_t *vertexDataU;
        const int16_t *vertexDataV;
        const bool isNS;

        IndexCompare(const int16_t *vertexDataU, const int16_t *vertexDataV, bool isNS);
        bool operator()(const int32_t &i1, const int32_t &i2) const;
    };
}


Indices::Indices(int length, bool is32bit) : length(length), is32bit(is32bit), indices16(), indices32()
{

}

Util::TAKErr TAK::Engine::Formats::QuantizedMesh::Impl::Indices_deserialize(std::unique_ptr<Indices> &result, int length, bool is32bit, bool isCompressed, Util::DataInput2 &input)
{
    Util::TAKErr rc = Util::TE_Ok;
    result.reset(new Indices(length, is32bit));

    if (is32bit) {
        result->indices32.reserve(length);
    } else {
        result->indices16.reserve(length);
    }

    if (isCompressed) {
        int highest = 0;
        for (int i = 0; i < length; ++i) {
            int code;
            int16_t code16;
            if (is32bit) {
                rc = input.readInt(&code);
                TE_CHECKRETURN_CODE(rc);
                int index = highest - code;
                result->indices32.push_back(index);
                if (code == 0)
                    ++highest;
            } else {
                rc = input.readShort(&code16);
                TE_CHECKRETURN_CODE(rc);
                int16_t index = static_cast<int16_t>(highest) - code16;
                result->indices16.push_back(index);
                if (code16 == 0)
                    ++highest;
            }
        }
    } else {
        for (int i = 0; i < length; ++i) {
            if (is32bit) {
                int code;
                rc = input.readInt(&code);
                TE_CHECKRETURN_CODE(rc);
                result->indices32.push_back(code);
            } else {
                int16_t code;
                rc = input.readShort(&code);
                TE_CHECKRETURN_CODE(rc);
                result->indices16.push_back(code);
            }
        }
    }

    return Util::TE_Ok;
}

int Indices::get(int i) const
{
    return is32bit ? indices32[i] : indices16[i];
}

void Indices::put(int i, int value)
{
    if (is32bit) {
        indices32[i] = value;
    } else {
        indices16[i] = static_cast<int16_t>(value);
    }
}

int64_t Indices::getTotalSize() const
{
    return length * (int64_t)(is32bit ? 4 : 2);
}

bool Indices::getIs32Bit() const
{
    return is32bit;
}

int Indices::getLength() const
{
    return length;
}

void Indices::sort(const int16_t *vertexDataU, const int16_t *vertexDataV, bool isNorthSouth)
{
    // For the sake of a faster skirt function, sorts indices by X/Y along
    // their respective edges
    IndexCompare cmp(vertexDataU, vertexDataV, isNorthSouth);
    if (is32bit)
        std::sort(indices32.begin(), indices32.end(), cmp);
    else
        std::sort(indices16.begin(), indices16.end(), AdaptCmp16<IndexCompare>(cmp));
}


IndexCompare::IndexCompare(const int16_t *vertexDataU, const int16_t *vertexDataV, bool isNS) : 
    vertexDataU(vertexDataU), vertexDataV(vertexDataV), isNS(isNS)
{
}


bool IndexCompare::operator()(const int32_t &i1, const int32_t &i2) const
{
    if (isNS)
        return vertexDataU[i1] < vertexDataU[i2];
    else
        return vertexDataV[i1] < vertexDataV[i2];
}
