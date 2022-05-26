#ifndef ATAKMAP_MATH_ELLIPSOID_H_INCLUDED
#define ATAKMAP_MATH_ELLIPSOID_H_INCLUDED

#include "math/GeometryModel.h"
#include "math/Point.h"
#include "math/Ellipsoid2.h"
#include "math/Ray.h"
#include "port/Platform.h"
#include "util/Memory.h"

namespace atakmap
{
namespace math
{

class ENGINE_API Ellipsoid : public GeometryModel
{
public :
    Ellipsoid(const Point<double> *center, const double radiusX, const double radiusY, const double radiusZ);
    Ellipsoid(std::unique_ptr<TAK::Engine::Math::Ellipsoid2, void(*)(const TAK::Engine::Math::Ellipsoid2 *)> &&impl);
    virtual ~Ellipsoid();
public : // GeometryModel interface
    virtual bool intersect(const Ray<double> *ray, Point<double> *isectPoint) const override;
    virtual GeometryModel::GeometryClass getGeomClass() const override;
private:
    std::unique_ptr<TAK::Engine::Math::Ellipsoid2, void(*)(const TAK::Engine::Math::Ellipsoid2 *)> impl;
public :
    const Point<double> center;
    const double radiusX;
    const double radiusY;
    const double radiusZ;
};

}
}

#endif // ATAKMAP_MATH_ELLIPSOID_H_INCLUDED
