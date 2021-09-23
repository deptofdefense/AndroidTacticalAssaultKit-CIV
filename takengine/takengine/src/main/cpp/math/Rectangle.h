#ifndef ATAKMAP_MATH_RECTANGLE_H_INCLUDED
#define ATAKMAP_MATH_RECTANGLE_H_INCLUDED

#include "math/Utils.h"

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
    static bool contains(T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2);
    static int subtract(T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2, Rectangle<T> remainder[4]);

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

/**
 * Returns <code>true</code> if rectangle <I>a</I> contains rectangle
 * <I>b</I>.
 *
 * @param aX1
 * @param aY1
 * @param aX2
 * @param aY2
 * @param bX1
 * @param bY1
 * @param bX2
 * @param bY2
 * @return
 */
template <class T>
inline bool Rectangle<T>::contains(T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2) {
    return aX1 <= bX1 && aY1 <= bY1 && aX2 >= bX2 && aY2 >= bY2;
}



/**
 * Subtracts rectangle <I>b</I> from rectangle <I>a</I>. Any remainder
 * rectangles are inclusive to <I>a</I>.
 *
 * @param aX1
 * @param aY1
 * @param aX2
 * @param aY2
 * @param bX1
 * @param bY1
 * @param bX2T 
 * @param bY2
 *
 * @return  The number of remainder rectangles resulting from the
 *          subtraction. At most, <code>4</code> <code>Rectangle</code>
 *          instances will be returned. Note that in the case of no
 *          intersection, a value of <code>1</code> will be returned and
 *          the remainder will be equal to <I>a</I>.
 */
template<class T>
inline int Rectangle<T>::subtract(T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2,
                                  Rectangle<T> remainder[4]) {
    if (contains(bX1, bY1, bX2, bY2, aX1, aY1, aX2, aY2)) return 0;
    if (!intersects(aX1, aY1, aX2, aY2, bX1, bY1, bX2, bY2)) {
        remainder[0] = Rectangle<T>(aX1, aY1, aX2, aY2);
        return 1;
    }

    // compute the intersection
    T isectX1 = atakmap::math::max(aX1, bX1);
    T isectY1 = atakmap::math::max(aY1, bY1);
    T isectX2 = atakmap::math::min(aX2, bX2);
    T isectY2 = atakmap::math::min(aY2, bY2);

    int remainders = 0;

    // compute top remainder
    const T topX1 = aX1;
    const T topY1 = aY1;
    const T topX2 = aX2;
    const T topY2 = isectY1;
    if (topX2 > topX1 && topY2 > topY1) remainder[remainders++] = Rectangle<T>(topX1, topY1, topX2, topY2);
    // compute right remainder
    const T rightX1 = isectX2;
    const T rightY1 = isectY1;
    const T rightX2 = aX2;
    const T rightY2 = isectY2;
    if (rightX2 > rightX1 && rightY2 > rightY1) remainder[remainders++] = Rectangle<T>(rightX1, rightY1, rightX2, rightY2);
    // compute bottom remainder
    const T bottomX1 = aX1;
    const T bottomY1 = isectY2;
    const T bottomX2 = aX2;
    const T bottomY2 = aY2;
    if (bottomX2 > bottomX1 && bottomY2 > bottomY1) remainder[remainders++] = Rectangle<T>(bottomX1, bottomY1, bottomX2, bottomY2);
    // compute right remainder
    const T leftX1 = aX1;
    const T leftY1 = isectY1;
    const T leftX2 = isectX1;
    const T leftY2 = isectY2;
    if (leftX2 > leftX1 && leftY2 > leftY1) remainder[remainders++] = Rectangle<T>(leftX1, leftY1, leftX2, leftY2);

    return remainders;
}

    
}
}

#endif // ATAKMAP_MATH_RECTANGLE_H_INCLUDED
