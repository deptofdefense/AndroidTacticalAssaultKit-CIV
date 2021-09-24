#include "math/Sphere.h"

#include <cmath>

#include "util/Memory.h"

using namespace atakmap::math;

Sphere::Sphere(std::unique_ptr<TAK::Engine::Math::Sphere2, void(*)(const TAK::Engine::Math::Sphere2 *)> &&impl_) :
    impl(std::move(impl_)),
    center(impl->center.x, impl->center.y, impl->center.z),
    radius(impl->radius)
{}

Sphere::Sphere(Point<double> *c, double r)  :
    center(c->x, c->y, c->z),
    radius(r),
    impl(std::unique_ptr<TAK::Engine::Math::Sphere2, void(*)(const TAK::Engine::Math::Sphere2 *)>
    (new TAK::Engine::Math::Sphere2(TAK::Engine::Math::Point2<double>(c->x, c->y, c->z), r), TAK::Engine::Util::Memory_deleter_const<TAK::Engine::Math::Sphere2>))
{}

Sphere::~Sphere()
{}

bool Sphere::intersect(const Ray<double> *ray, Point<double> *isectPoint) const
{
    Vector3<double> dist(ray->origin.x-center.x,
                         ray->origin.y-center.y,
                         ray->origin.z-center.z);

    const double b=dist.dot(&ray->direction);
    const double c=dist.dot(&dist)-(radius*radius);
    const double d=b*b-c;
    if (d>0)
    {
        const double scale = -b-sqrt(d);
        isectPoint->x = ray->origin.x + (ray->direction.x*scale);
        isectPoint->y = ray->origin.y + (ray->direction.y*scale);
        isectPoint->z = ray->origin.z + (ray->direction.z*scale);

        return true;
    }
    return false;
}

GeometryModel::GeometryClass Sphere::getGeomClass() const
{
    return GeometryModel::SPHERE;
}
