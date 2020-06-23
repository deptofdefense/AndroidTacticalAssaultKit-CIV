#ifndef TAK_ENGINE_MATH_TRIANGLE_H_INCLUDED
#define TAK_ENGINE_MATH_TRIANGLE_H_INCLUDED

#include "math/GeometryModel2.h"
#include "math/Vector4.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {
            class ENGINE_API Triangle : public GeometryModel2
            {
            public: 
                Triangle(const Point2<double> &a, const Point2<double> &b, const Point2<double> &c) NOTHROWS;
                virtual ~Triangle();
            public: // GeometryModel interface
                virtual bool intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const NOTHROWS;
                virtual GeometryModel2::GeometryClass getGeomClass() const NOTHROWS;
                virtual void clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const NOTHROWS;
            private :
                Point2<double> V0;
                Point2<double> V1;
                Point2<double> V2;
            };

			ENGINE_API
            Util::TAKErr Triangle_intersect(Point2<double> *value,
                                            const double V0x, const double V0y, const double V0z,
                                            const double V1x, const double V1y, const double V1z,
                                            const double V2x, const double V2y, const double V2z,
                                            const Ray2<double> &ray) NOTHROWS;
			ENGINE_API
            Util::TAKErr Triangle_intersect(Point2<double> *value,
                                            const double ULx, const double ULy, const double ULz,
                                            const double URx, const double URy, const double URz,
                                            const double LRx, const double LRy, const double LRz,
                                            const double LLx, const double LLy, const double LLz,
                                            const Ray2<double> &ray) NOTHROWS;
        }
    }
}
#endif // TAK_ENGINE_MATH_TRIANGLE_H_INCLUDED
