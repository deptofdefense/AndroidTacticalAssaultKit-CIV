#ifndef ATAKMAP_MATH_PLAN2_H_INCLUDED
#define ATAKMAP_MATH_PLAN2_H_INCLUDED

#include "math/GeometryModel2.h"

namespace TAK {
    namespace Engine {
        namespace Math {

            class ENGINE_API Plane2 : public GeometryModel2
            {
            public:
                Plane2();
                Plane2(const Vector4<double> &normal, const Point2<double> &point);
                virtual ~Plane2();
            public:
                virtual bool intersect(Point2<double>* isectPoint, const Ray2<double>& ray) const;
                virtual GeometryModel2::GeometryClass getGeomClass() const;
                virtual void clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const;
            private:
                Vector4<double> normal;
                Point2<double> point;
            };

        }
    }
}
#endif // ATAKMAP_MATH_PLAN2_H_INCLUDED
