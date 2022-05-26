#ifndef ATAK_PACKEDVECTOR_H
#define ATAK_PACKEDVECTOR_H

#include "math/Vector4.h"
#include "simd/simd.h"

namespace TAK {
    namespace Engine {
        namespace Math {
            class ENGINE_API PackedVector{
                    friend PackedVector operator * (const PackedVector& v0, const PackedVector& v1);
                    friend PackedVector operator + (const PackedVector& v0, const PackedVector& v1);
                    friend PackedVector operator - (const PackedVector& v0, const PackedVector& v1);
            public:
                PackedVector();
                PackedVector(float x, float y, float z);
                PackedVector(float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3);
                PackedVector(__m128 x, __m128 y, __m128 z);

                ~PackedVector();

                __m128 dotProduct(const PackedVector& pack1) const;
                PackedVector crossProduct(const PackedVector& v1) const;

                __m128 packedX_;
                __m128 packedY_;
                __m128 packedZ_;
            };
        }
    }
}

#endif //ATAK_PACKEDVECTOR_H
