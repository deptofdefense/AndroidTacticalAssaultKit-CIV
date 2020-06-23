#ifndef TAK_ENGINE_MATH_AABB_H_INCLUDED
#define TAK_ENGINE_MATH_AABB_H_INCLUDED

#include "math/GeometryModel2.h"
#include "math/Point2.h"
#include "port/Platform.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {
            class ENGINE_API AABB : public GeometryModel2
            {
            public:
                AABB(const Point2<double> &min, const Point2<double> &max);

                virtual bool intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const;
                virtual GeometryClass getGeomClass() const;
                virtual void clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const;
            public :
                const double minX;
                const double minY;
                const double minZ;
                const double maxX;
                const double maxY;
                const double maxZ;
            };
        }
    }
}

#endif
