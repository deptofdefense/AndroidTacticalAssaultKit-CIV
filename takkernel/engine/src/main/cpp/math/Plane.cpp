#include "math/Plane.h"
#include "math/Ray2.h"
#include "math/Vector4.h"
#include "util/Memory.h"
#include "util/Error.h"
#include <stdexcept>

using namespace atakmap::math;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

Plane::Plane(std::unique_ptr<TAK::Engine::Math::Plane2, void(*)(const TAK::Engine::Math::Plane2 *)> &&impl_) :
    impl(std::move(impl_)),
    normal(0, 0, 0), //These have to be filled with something
    dist(0) 
{

}

Plane::Plane() :
    normal(0, 0, 0),
    impl(std::unique_ptr<TAK::Engine::Math::Plane2, void(*)(const TAK::Engine::Math::Plane2 *)>(new Plane2(Vector4<double>(0,0,0), Point2<double>(0, 0, 0)), nullptr)),
    dist(0)
{
}

Plane::Plane(const Vector3<double> *n, const Vector3<double> *point) :
    normal(0, 0, 0),
    impl(std::unique_ptr<TAK::Engine::Math::Plane2, void(*)(const TAK::Engine::Math::Plane2 *)>(new Plane2(Vector4<double>(n->x, n->y, n->z), Point2<double>(point->x, point->y, point->z)), nullptr))
{
    n->normalize(&normal);
    dist = normal.dot(point);
}

Plane::~Plane()
{}

bool Plane::intersect(const Ray<double> *ray, Point<double> *isectPoint) const
{
    // populate Ray2 from legacy
    Ray2<double> ray2(Point2<double>(ray->origin.x, ray->origin.y, ray->origin.z), 
        Vector4<double>(ray->direction.x, ray->direction.y, ray->direction.z));
    Point2<double> isect2;
    bool code = impl->intersect(&isect2, ray2);
    if (!code)
        return false;
    isectPoint->x = isect2.x;
    isectPoint->y = isect2.y;
    isectPoint->z = isect2.z;
    return true;
}

bool Plane::intersectV(const Ray<double> *ray, Vector3<double> *result) const
{
    Ray2<double> ray2(Point2<double>(ray->origin.x, ray->origin.y, ray->origin.z),
        Vector4<double>(ray->direction.x, ray->direction.y, ray->direction.z));
    Point2<double> res = Point2<double>(result->x, result->y, result->z);
    bool retVal;
    retVal = impl->intersect(&res, ray2);
    if (!retVal)
    {
        return false;
    }
    result->x = res.x;
    result->y = res.y;
    result->z = res.z;
    return true;
    /*
    double d = normal.dot(&ray->direction);

    if (d == 0)
        return false;

    Vector3<double> tv(ray->origin.x, ray->origin.y, ray->origin.z);
    double n = normal.dot(&tv);
    double t = -(n / d);

    if (t >= 0) {
        Vector3<double> tv2(0, 0, 0);
        ray->direction.multiply(t, &tv2);
        tv.add(&tv2, result);
        return true;
    } else {
        return false;
    }*/
}

double Plane::distance(const Vector3<double> *point) const
{
    throw std::runtime_error("not implemented"); //Added as per Chris Lawrence's instruction
//    Vector4<double> pt = Vector4<double>(point->x, point->y, point->z);
//    return impl->distance(pt);
}

void Plane::normalize(Plane *result) const
{
    return; //No-op'd as per Chris Lawrence's instruction
    //double m = 1.0 / normal.length();
    //normal.multiply(m, &result->normal);
    //result->dist = this->dist * m;
}

GeometryModel::GeometryClass Plane::getGeomClass() const
{
    return GeometryModel::PLANE;
}
