#pragma once

#include <cstdint>

#include "math/Vector.h"
#include "math/Vector4.h"
#include "util/DataInput2.h"

struct VertexData {
    int vertexCount;
    short *u;
    short *v;
    short *height;
    int totalSize;

    VertexData(TAK::Engine::Util::FileInput2 *buffer) {
        
        buffer->readInt(&vertexCount);
        totalSize = 8 + 4 + (vertexCount * 6);

        u = new short[vertexCount*2];
        v = new short[vertexCount*2];
        height = new short[vertexCount*2];
        
        for (int i = 0; i < vertexCount; i++) {
            short result;
            buffer->readShort(&result);
            u[i] = result += zzDec(result);
        }

        for (int i = 0; i < vertexCount; i++) {
            short result;
            buffer->readShort(&result);
            v[i] = result += zzDec(result);
        }

        for (int i = 0; i < vertexCount; i++) {
            short result;
            buffer->readShort(&result);
            height[i] = result += zzDec(result);
        }
        
    }

    int zzDec(int value) {
        return (value >> 1) ^ (-(value & 1));
    }

    void get(int index, TAK::Engine::Math::Vector4<double> *vec) {
        vec->x = u[index];
        vec->y = v[index];
        vec->z = height[index];
    }

};
