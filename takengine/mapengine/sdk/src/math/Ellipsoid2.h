#ifndef TAK_ENGINE_MATH_ELLIPSOID2_H_INCLUDED
#define TAK_ENGINE_MATH_ELLIPSOID2_H_INCLUDED

#include "math/GeometryModel2.h"
#include "math/Point2.h"
#include "math/Ray2.h"
#include "port/Platform.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {

            class ENGINE_API Ellipsoid2 : public GeometryModel2
            {
            public:
                Ellipsoid2(const Point2<double> &center, const double radiusX, const double radiusY, const double radiusZ);
                virtual ~Ellipsoid2();
            public: // GeometryModel interface
                virtual bool intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const override;
                virtual GeometryModel2::GeometryClass getGeomClass() const override;
                virtual void clone(std::unique_ptr<GeometryModel2, void (*)(const GeometryModel2 *)> &value) const override;
            public:
                const Point2<double> center;
                const double radiusX;
                const double radiusY;
                const double radiusZ;
            };

        }
    }
}

#endif
