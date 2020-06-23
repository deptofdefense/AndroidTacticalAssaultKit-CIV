#ifndef ATAKMAP_MATH_RECTANGLE_H_INCLUDED
#define ATAKMAP_MATH_RECTANGLE_H_INCLUDED

namespace atakmap {
namespace math {

template<class T>
class Rectangle
{
public :
    Rectangle(T x, T y, T width, T height);
    Rectangle();
    ~Rectangle();
    
    static bool intersects(T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2);
    static bool contains(T aX1, T aY1, T aX2, T aY2, T pX, T pY);

public :
    T x;
    T y;
    T width;
    T height;
};

template<class T>
inline Rectangle<T>::Rectangle(T _x, T _y, T _w, T _h) :
    x(_x), y(_y), width(_w), height(_h)
{
}

template<class T>
inline Rectangle<T>::Rectangle() :
x(0), y(0), width(0), height(0)
{
}

template<class T>
inline Rectangle<T>::~Rectangle()
{}

template<class T>
inline bool Rectangle<T>::intersects(T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2)
{
    return !((bX2 < aX1) || (bX1 > aX2) || (bY2 < aY1) || (bY1 > aY2));
}

template<class T>
inline bool Rectangle<T>::contains(T aX1, T aY1, T aX2, T aY2, T pX, T pY)
{
    return ((pX >= aX1) && (pX <= aX2)) && ((pY >= aY1) && (pY <= aY2));
}
    
}
}

#endif // ATAKMAP_MATH_RECTANGLE_H_INCLUDED
