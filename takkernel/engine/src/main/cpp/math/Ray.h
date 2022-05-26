#ifndef ATAKMAP_MATH_RAY_H_INCLUDED
#define ATAKMAP_MATH_RAY_H_INCLUDED

#include "math/Point.h"
#include "math/Vector.h"

namespace atakmap {
namespace math {

template<class T>
class Ray
{
public :
    Ray(Point<T> *origin, Vector3<T> *direction);
    ~Ray();
public :
    Point<T> origin;
    Vector3<T> direction;
};

template<class T>
inline Ray<T>::Ray(Point<T> *o, Vector3<T> *dir) :
    origin(o->x, o->y, o->z),
    direction(dir->x, dir->y, dir->z)
{
    direction.normalize(&direction);
}

template<class T>
inline Ray<T>::~Ray() {}

};
};

#endif // ATAKMAP_MATH_RAY_H_INCLUDED