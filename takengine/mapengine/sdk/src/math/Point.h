#ifndef ATAKMAP_MATH_POINT_H_INCLUDED
#define ATAKMAP_MATH_POINT_H_INCLUDED

namespace atakmap
{
namespace math
{

template <class T>
class Point {
public :
    Point();
    Point(T x, T y);
    Point(T x, T y, T z);
    ~Point();
public :
    T x;
    T y;
    T z;
};

typedef Point<double> PointD;


template <class T>
inline Point<T>::Point() : x(0), y(0), z(0) {}

template <class T>
inline Point<T>::Point(T _x, T _y) : x(_x), y(_y), z(0) {}

template <class T>
inline Point<T>::Point(T _x, T _y, T _z) : x(_x), y(_y), z(_z) {}

template <class T>
inline Point<T>::~Point() {}

} // end namespace atakmap::math
} // end namespace atakmap

#endif // ATAKMAP_MATH_POINT_H_INCLUDED
