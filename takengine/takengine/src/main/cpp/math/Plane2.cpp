#include "math/Plane2.h"

#include "util/Memory.h"

using namespace TAK::Engine::Math;

using namespace TAK::Engine::Util;

Plane2::Plane2() :
    normal(0, 0, 0),
    point(0, 0, 0)
{}

Plane2::Plane2(const Vector4<double> &n, const Point2<double> &p) :
    normal(n.x, n.y, n.z),
    point(p.x, p.y, p.z)
{
    n.normalize(&normal);
}

Plane2::~Plane2()
{}

bool Plane2::intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const
{
    const double ldotn = normal.dot(&ray.direction);

    if (ldotn == 0)
        return false;

    Vector4<double> originV(point.x - ray.origin.x, point.y - ray.origin.y, point.z - ray.origin.z);
    const double n = normal.dot(&originV);
    const double d = (n / ldotn);

    if (d >= 0) {
        isectPoint->x = ray.origin.x + (ray.direction.x*d);
        isectPoint->y = ray.origin.y + (ray.direction.y*d);
        isectPoint->z = ray.origin.z + (ray.direction.z*d);
        return true;
    }
    else {
        return false;
    }
}

GeometryModel2::GeometryClass Plane2::getGeomClass() const
{
    return GeometryModel2::PLANE;
}


void Plane2::clone(GeometryModel2Ptr &value) const
{
    value = GeometryModel2Ptr(new Plane2(*this), Memory_deleter_const<GeometryModel2, Plane2>);
}

