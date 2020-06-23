#ifndef ATAKMAP_MATH_VECTOR_H_INCLUDED
#define ATAKMAP_MATH_VECTOR_H_INCLUDED

#include <cmath>

namespace atakmap {
namespace math {

// XXX - derive from Point<T> ???

template<class T>
class Vector3
{
public :
    Vector3(T x, T y, T z);
    ~Vector3();
public :
    void add(const Vector3<T> *v, Vector3<T> *result) const;
    void subtract(const Vector3<T> *v, Vector3<T> *result) const;
    void multiply(const T v, Vector3<T> *result) const;

    double dot(const Vector3<T> *v) const;
    void normalize(Vector3<T> *v) const; // XXX - const precludes use of 'this'?
    double length() const;
public :
    T x;
    T y;
    T z;
};

template<class T>
inline Vector3<T>::Vector3(T _x, T _y, T _z) :
    x(_x), y(_y), z(_z)
{}

template<class T>
inline Vector3<T>::~Vector3()
{}

template<class T>
inline void Vector3<T>::add(const Vector3<T> *v, Vector3<T> *result) const
{
    result->x = x + v->x;
    result->y = y + v->y;
    result->z = z + v->z;
}

template<class T>
inline void Vector3<T>::subtract(const Vector3<T> *v, Vector3<T> *result) const
{
    result->x = x - v->x;
    result->y = y - v->y;
    result->z = z - v->z;
}

template<class T>
inline void Vector3<T>::multiply(const T v, Vector3<T> *result) const
{
    result->x = x * v;
    result->y = y * v;
    result->z = z * v;
}

template<class T>
inline double Vector3<T>::dot(const Vector3<T> *v) const
{
    return x*v->x + y*v->y + z*v->z;
}

template<class T>
inline void Vector3<T>::normalize(Vector3<T> *v) const
{
    const double length = sqrt((x*x) + (y*y) + (z*z));
    multiply(1.0 / length, v);
}

template<class T>
inline double Vector3<T>::length() const
{
    return sqrt(this->dot(this));
}

}
}

#endif // ATAKMAP_MATH_VECTOR_H_INCLUDED