#include "math/Sphere2.h"

#include <algorithm>
#include <cmath>

#include "util/Memory.h"

using namespace TAK::Engine::Math;

using namespace TAK::Engine::Util;

Sphere2::Sphere2(const Point2<double> &c, double r) :
    center(c.x, c.y, c.z),
    radius(r)
{}

Sphere2::~Sphere2()
{}

bool Sphere2::intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const
{
    Vector4<double> dist(ray.origin.x - center.x,
        ray.origin.y - center.y,
        ray.origin.z - center.z);

    const double b = dist.dot(&ray.direction);
    const double c = dist.dot(&dist) - (radius*radius);
    const double d = b*b - c;
    if (d>0)
    {
        const double r = sqrt(d);
        const double t0 = -b - r;
        const double t1 = -b + r;
        if (t0 < 0.0 && t1 < 0.0)
            return false;
        double scale;
        if (t0 < 0.0)
            scale = t1;
        else if (t1 < 0.0)
            scale = t0;
        else
            scale = std::min(t0, t1);
        isectPoint->x = ray.origin.x + (ray.direction.x*scale);
        isectPoint->y = ray.origin.y + (ray.direction.y*scale);
        isectPoint->z = ray.origin.z + (ray.direction.z*scale);

        return true;
    }
    return false;
}

GeometryModel2::GeometryClass Sphere2::getGeomClass() const
{
    return GeometryModel2::SPHERE;
}


void Sphere2::clone(GeometryModel2Ptr &value) const
{
    value = GeometryModel2Ptr(new Sphere2(*this), Memory_deleter_const<GeometryModel2, Sphere2>);
}

double TAK::Engine::Math::Sphere2_getRadius(const double radius, const double offsetFromCenter) NOTHROWS
{
    if(offsetFromCenter >= radius)
        return 0.0;
    return sqrt(radius*radius - offsetFromCenter*offsetFromCenter);
}