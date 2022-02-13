#include "PackedRay.h"

namespace TAK {
    namespace Engine {
        namespace Math {
            PackedRay::PackedRay(const Ray2<double>& ray)
            : origin((float)ray.origin.x, (float)ray.origin.y, (float)ray.origin.z)
            , direction((float)ray.direction.x, (float)ray.direction.y, (float)ray.direction.z)
            {}
        }
    }
}
