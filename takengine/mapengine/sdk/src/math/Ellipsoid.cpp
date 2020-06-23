#include "math/Ellipsoid.h"
#include "math/Matrix.h"
#include "math/Sphere.h"
#include "math/Vector.h"
#include "util/Memory.h"

using namespace atakmap::math;

Ellipsoid::Ellipsoid(std::unique_ptr<TAK::Engine::Math::Ellipsoid2, void(*)(const TAK::Engine::Math::Ellipsoid2 *)> &&impl_) :
    impl(std::move(impl_)),
    center(impl->center.x, impl->center.y, impl->center.z),
    radiusX(impl->radiusX),
    radiusY(impl->radiusY),
    radiusZ(impl->radiusZ)
{
}

Ellipsoid::Ellipsoid(const Point<double> *c, const double rX, const double rY, const double rZ) :
    center(c->x, c->y, c->z),
    radiusX(rX),
    radiusY(rY),
    radiusZ(rZ),
    impl(std::unique_ptr<TAK::Engine::Math::Ellipsoid2, void(*)(const TAK::Engine::Math::Ellipsoid2 *)>
    (new TAK::Engine::Math::Ellipsoid2(TAK::Engine::Math::Point2<double>(c->x, c->y, c->z), rX, rY, rZ), TAK::Engine::Util::Memory_deleter_const<TAK::Engine::Math::Ellipsoid2>))
{}

Ellipsoid::~Ellipsoid()
{}

bool Ellipsoid::intersect(const Ray<double> *ray, Point<double> *isectPoint) const
{
    Matrix toUnit;
    toUnit.setToScale(1.0 / radiusX, 1.0 / radiusY, 1.0 / radiusZ);

    Matrix fromUnit;
    fromUnit.setToScale(radiusX, radiusY, radiusZ);

    Point<double> unitOrigin(ray->origin.x, ray->origin.y, ray->origin.z);
    toUnit.transform(&unitOrigin, &unitOrigin);

    Vector3<double> normDir(ray->direction.x / radiusX,
                            ray->direction.y / radiusY,
                            ray->direction.z / radiusZ);
    normDir.normalize(&normDir);
    Ray<double> unitRay(&unitOrigin, &normDir);

    Point<double> pt(0, 0, 0);
    Sphere unit(&pt,1);
    if (unit.intersect(&unitRay, &pt))
    {
        fromUnit.transform(&pt, isectPoint);
        return true;
    } else {
        return false;
    }
}

GeometryModel::GeometryClass Ellipsoid::getGeomClass() const
{
    return GeometryModel::ELLIPSOID;
}
