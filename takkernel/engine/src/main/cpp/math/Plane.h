#ifndef ATAKMAP_MATH_PLAN_H_INCLUDED
#define ATAKMAP_MATH_PLAN_H_INCLUDED

#include "math/GeometryModel.h"
#include "math/Plane2.h"
#include "port/Platform.h"
#include "util/Memory.h"

namespace atakmap {
namespace math {

class ENGINE_API Plane : public GeometryModel
{
public :
    Plane();
    Plane(const Vector3<double> *normal, const Vector3<double> *point);
    Plane(std::unique_ptr<TAK::Engine::Math::Plane2, void(*)(const TAK::Engine::Math::Plane2 *)> &&impl);
    virtual ~Plane();
public :
    virtual bool intersect(const Ray<double> *ray, Point<double> *isectPoint) const override;
    bool intersectV(const Ray<double> *ray, Vector3<double> *result) const;
    double distance(const Vector3<double> *point) const;
    void normalize(Plane *result) const;
    virtual GeometryModel::GeometryClass getGeomClass() const override;
private :
    std::unique_ptr<TAK::Engine::Math::Plane2, void(*)(const TAK::Engine::Math::Plane2 *)> impl;
    Vector3<double> normal;
    double dist;
};

}
}

#endif // ATAKMAP_MATH_PLAN_H_INCLUDED