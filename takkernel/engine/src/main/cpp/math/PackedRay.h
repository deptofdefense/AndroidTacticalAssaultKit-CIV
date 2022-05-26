#ifndef ATAK_PACKEDRAY_H
#define ATAK_PACKEDRAY_H

#include "math/PackedVector.h"
#include "math/Ray2.h"
#include "simd/simd.h"

namespace TAK {
    namespace Engine {
        namespace Math {
            class ENGINE_API PackedRay  {
            public:
                PackedRay() = default;
                ~PackedRay() = default;

                PackedRay(const Ray2<double>& ray);

                PackedVector origin;
                PackedVector direction;
            };
        }
    }
}

#endif //ATAK_PACKEDRAY_H
