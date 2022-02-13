#include "PackedVector.h"

namespace TAK {
    namespace Engine {
        namespace Math {

            PackedVector::PackedVector()
            {}

            PackedVector::PackedVector(float x, float y, float z)
                : PackedVector(_mm_set1_ps(x), _mm_set1_ps(y), _mm_set1_ps(z))
            {}

            PackedVector::PackedVector(float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3)
                    : PackedVector(_mm_set_ps(x3, x2, x1, x0), _mm_set_ps(y3, y2, y1, y0), _mm_set_ps(z3, z2, z1, z0))
            {}

            PackedVector::PackedVector(__m128 x, __m128 y, __m128 z)
                : packedX_(x), packedY_(y), packedZ_(z)
            {}

            PackedVector::~PackedVector()
            {}

            __m128 PackedVector::dotProduct(const PackedVector& pack1) const
            {
                return
                    _mm_add_ps(
                        _mm_mul_ps(packedX_, pack1.packedX_),
                        _mm_add_ps(
                            _mm_mul_ps(packedY_, pack1.packedY_),
                            _mm_mul_ps(packedZ_, pack1.packedZ_)
                        )
                    );
            }

            PackedVector PackedVector::crossProduct(const PackedVector& v1) const
            {
                return {
                    _mm_sub_ps(_mm_mul_ps(packedY_, v1.packedZ_), _mm_mul_ps(packedZ_, v1.packedY_)),
                    _mm_sub_ps(_mm_mul_ps(packedZ_, v1.packedX_), _mm_mul_ps(packedX_, v1.packedZ_)),
                    _mm_sub_ps(_mm_mul_ps(packedX_, v1.packedY_), _mm_mul_ps(packedY_, v1.packedX_))
                };
            }


            PackedVector operator* (const PackedVector& v0, const PackedVector& v1)
            {
                return {
                    _mm_mul_ps(v0.packedX_, v1.packedX_),
                    _mm_mul_ps(v0.packedY_, v1.packedY_),
                    _mm_mul_ps(v0.packedZ_, v1.packedZ_)
                };
            }

            PackedVector operator+ (const PackedVector& v0, const PackedVector& v1)
            {
                return {
                    _mm_add_ps(v0.packedX_, v1.packedX_),
                    _mm_add_ps(v0.packedY_, v1.packedY_),
                    _mm_add_ps(v0.packedZ_, v1.packedZ_)
                };
            }

            PackedVector operator- (const PackedVector& v0, const PackedVector& v1)
            {
                return {
                    _mm_sub_ps(v0.packedX_, v1.packedX_),
                    _mm_sub_ps(v0.packedY_, v1.packedY_),
                    _mm_sub_ps(v0.packedZ_, v1.packedZ_)
                };
            }
        }
    }
}
