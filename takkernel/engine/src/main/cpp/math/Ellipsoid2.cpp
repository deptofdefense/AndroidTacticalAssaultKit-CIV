#include "math/Ellipsoid2.h"
#include "math/Matrix2.h"
#include "math/Sphere2.h"
#include "math/Vector4.h"
#include "util/Memory.h"

using namespace TAK::Engine::Math;

using namespace TAK::Engine::Util;

Ellipsoid2::Ellipsoid2(const Point2<double> &c, const double rX, const double rY, const double rZ) :
    center(c.x, c.y, c.z),
    radiusX(rX),
    radiusY(rY),
    radiusZ(rZ)
{}

Ellipsoid2::~Ellipsoid2()
{}

bool Ellipsoid2::intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const
{
    Matrix2 toUnit;
    toUnit.setToScale(1.0 / radiusX, 1.0 / radiusY, 1.0 / radiusZ);

    Matrix2 fromUnit;
    fromUnit.setToScale(radiusX, radiusY, radiusZ);

    Point2<double> unitOrigin(ray.origin.x, ray.origin.y, ray.origin.z);
    toUnit.transform(&unitOrigin, unitOrigin);

    Vector4<double> normDir(ray.direction.x / radiusX,
        ray.direction.y / radiusY,
        ray.direction.z / radiusZ);
    normDir.normalize(&normDir);
    Ray2<double> unitRay(unitOrigin, normDir);

    Point2<double> pt(0, 0, 0);
    Sphere2 unit(pt, 1);
    if (unit.intersect(&pt, unitRay))
    {
        fromUnit.transform(isectPoint, pt);
        return true;
    }
    else {
        return false;
    }
}

GeometryModel2::GeometryClass Ellipsoid2::getGeomClass() const
{
    return GeometryModel2::ELLIPSOID;
}

void Ellipsoid2::clone(GeometryModel2Ptr &value) const
{
    value = GeometryModel2Ptr(new Ellipsoid2(*this), Memory_deleter_const<GeometryModel2, Ellipsoid2>);
}
