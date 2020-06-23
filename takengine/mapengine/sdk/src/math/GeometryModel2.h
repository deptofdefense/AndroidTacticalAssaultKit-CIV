#ifndef TAK_ENGINE_MATH_GEOMETRYMODEL2_H_INCLUDED
#define TAK_ENGINE_MATH_GEOMETRYMODEL2_H_INCLUDED

#include <memory>

#include "math/Point2.h"
#include "math/Ray2.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Math {

            class ENGINE_API GeometryModel2
            {
            public:
                enum GeometryClass {
                    PLANE,
                    ELLIPSOID,
                    SPHERE,
                    TRIANGLE,
                    MESH,
                    AABB,
                    UNDEFINED,
                };

            public:
                virtual ~GeometryModel2() = 0;
            public:
                virtual bool intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const = 0;
                virtual GeometryClass getGeomClass() const = 0;
                virtual void clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const = 0;
            };

            typedef std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> GeometryModel2Ptr;
        }
    }
}
#endif // TAK_ENGINE_MATH_GEOMETRYMODEL2_H_INCLUDED
