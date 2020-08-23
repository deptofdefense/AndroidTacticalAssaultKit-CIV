#ifndef ATAKMAP_MATH_SPHERE_H_INCLUDED
#define ATAKMAP_MATH_SPHERE_H_INCLUDED

#include "math/GeometryModel.h"
#include "math/Sphere2.h"
#include "port/Platform.h"
#include "util/Memory.h"

namespace atakmap
{
namespace math
{

class ENGINE_API Sphere : public GeometryModel
{
public :

    Sphere(Point<double> *center, double radius);
    Sphere(std::unique_ptr<TAK::Engine::Math::Sphere2, void(*)(const TAK::Engine::Math::Sphere2 *)> &&impl);
    virtual ~Sphere();
public :
    virtual bool intersect(const Ray<double> *ray, Point<double> *isectPoint) const override;
    virtual GeometryModel::GeometryClass getGeomClass() const override;
public :
    const Point<double> center;
    const double radius;
private:
    std::unique_ptr<TAK::Engine::Math::Sphere2, void(*)(const TAK::Engine::Math::Sphere2 *)> impl;
};

}
}

#endif // ATAKMAP_MATH_SPHERE_H_INCLUDED
