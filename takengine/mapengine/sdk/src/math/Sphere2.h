#ifndef ATAKMAP_MATH_SPHERE2_H_INCLUDED
#define ATAKMAP_MATH_SPHERE2_H_INCLUDED

#include "math/Point2.h"
#include "math/Ray2.h"
#include "math/GeometryModel2.h"

namespace TAK
{
    namespace Engine 
    {
        namespace Math
        {

            class ENGINE_API Sphere2 : public GeometryModel2
            {
            public:
                Sphere2(const Point2<double> &center, double radius);
                virtual ~Sphere2();
            public:
                virtual bool intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const override;
                virtual GeometryModel2::GeometryClass getGeomClass() const override;
                virtual void clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const override;
            public:
                Point2<double> center;
                double radius;
            };

            ENGINE_API double Sphere2_getRadius(const double radius, const double offsetFromCenter) NOTHROWS;
        }
    }
}

#endif // ATAKMAP_MATH_SPHERE2_H_INCLUDED
