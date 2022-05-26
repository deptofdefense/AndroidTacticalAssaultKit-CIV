#ifndef ATAKMAP_MATH_GEOMETRY_MODEL_H_INCLUDED
#define ATAKMAP_MATH_GEOMETRY_MODEL_H_INCLUDED

#include "math/Ray.h"

namespace atakmap {
namespace math {

class GeometryModel
{
public :
    enum GeometryClass {
        PLANE,
        ELLIPSOID,
        SPHERE,
    };

public :
    virtual ~GeometryModel() = 0;
public :
    virtual bool intersect(const Ray<double> *ray, Point<double> *isectPoint) const = 0;
    virtual GeometryClass getGeomClass() const = 0;
};

}
};

#endif // ATAKMAP_MATH_GEOMETRY_MODEL_H_INCLUDED