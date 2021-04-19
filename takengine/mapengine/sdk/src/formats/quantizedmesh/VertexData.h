#pragma once

#include <cstdint>

#include "math/Vector.h"
#include "math/Vector4.h"
#include "util/DataInput2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

struct VertexData {
    int vertexCount;
    unsigned short *u;
    unsigned short *v;
    unsigned short *height;
    int totalSize;

    VertexData(Util::FileInput2 *buffer) {
        
        buffer->readInt(&vertexCount);
        totalSize = 8 + 4 + (vertexCount * 6);

        u = new unsigned short[vertexCount*2];
        v = new unsigned short[vertexCount*2];
        height = new unsigned short[vertexCount*2];

        short utmp = 0;
        short vtmp = 0;
        short htmp = 0;
        for (int i = 0; i < vertexCount; i++) {
            utmp = zzDec(buffer, u, utmp, i);
        }
        
        for (int i = 0; i < vertexCount; i++) {
            vtmp = zzDec(buffer, v, vtmp, i);
        }
        
        for (int i = 0; i < vertexCount; i++) {
            htmp =  zzDec(buffer, height, htmp, i);
        }
    }

    int zzDec(int value) {
        return (value >> 1) ^ (-(value & 1));
    }

    int zzDec(Util::FileInput2  * buffer, unsigned short *arr, int previous, int index) {
        short s;
        buffer->readShort(&s);

        unsigned short ss = static_cast<unsigned>(s);
        previous += zzDec(ss);
        arr[index] = previous;
        return previous;
    }

    void get(int index, TAK::Engine::Math::Vector4<double> *vec) {
        vec->x = u[index];
        vec->y = v[index];
        vec->z = height[index];
    }

};

}  // namespace QuantizedMesh
}  // namespace Formats
}  // namespace Engine
}  // namespace TAK
