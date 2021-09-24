
#ifndef ATAKMAP_MATH_RECTANGLE2_H_INCLUDED
#define ATAKMAP_MATH_RECTANGLE2_H_INCLUDED

#include <algorithm>

#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {
            template <class T>
            class Rectangle2
            {
            public:
                Rectangle2(T x, T y, T width, T height);
                Rectangle2();
                ~Rectangle2();

            public:    
                
                T x;
				T y;
				T width;
				T height;
            };

            template<typename T>
            Util::TAKErr Rectangle2_intersects(bool& intersects, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2);

            template<typename T>
            Util::TAKErr Rectangle2_intersects(bool& intersects, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2, bool strict);

            template<typename T>
            Util::TAKErr Rectangle2_contains(bool& contains, T aX1, T aY1, T aX2, T aY2, T pX, T pY);

            /**
             * Returns <code>true</code> result if rectangle <I>a</I> contains rectangle
             * <I>b</I>.
             *
             * @param result
             * @param aX1
             * @param aY1
             * @param aX2
             * @param aY2
             * @param bX1
             * @param bY1
             * @param bX2
             * @param bY2
             * @return error code
             */
            template<typename T>
            Util::TAKErr Rectangle2_contains(bool& contains, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2);

            /**
             * Subtracts rectangle <I>b</I> from rectangle <I>a</I>. Any remainder
             * rectangles are inclusive to <I>a</I>.
             *
             * @param result The number of remainder rectangles resulting from the
             *               subtraction. At most, <code>4</code> <code>Rectangle</code>
             *               instances will be returned. Note that in the case of no
             *               intersection, a value of <code>1</code> will be returned and
             *               the remainder will be equal to <I>a</I>.
             * @param aX1
             * @param aY1
             * @param aX2
             * @param aY2
             * @param bX1
             * @param bY1
             * @param bX2T
             * @param bY2
             *
             * @return  error code
             */
            template<typename T>
            Util::TAKErr Rectangle2_subtract(int& remainders, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2, Rectangle2<T> remainder[4]);


            template<typename T>
            inline Rectangle2<T>::Rectangle2() : x(0), y(0), width(0), height(0) {}

            template<typename T>
            inline Rectangle2<T>::Rectangle2(T _x, T _y, T _w, T _h) : x(_x), y(_y), width(_w), height(_h) {}

            template<typename T>
            inline Rectangle2<T>::~Rectangle2() {}

            template<typename T>
            inline Util::TAKErr Rectangle2_intersects(bool& intersects, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2)
            {
                return Rectangle2_intersects(intersects, aX1, aY1, aX2, aY2, bX1, bY1, bX2, bY2, false);
            }

            template<typename T>
            inline Util::TAKErr Rectangle2_intersects(bool& intersects, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2, bool edgeIsect)
            {
                const bool strictIsect = aX1 < bX2 &&
                                         aY1 < bY2 &&
                                         aX2 > bX1 &&
                                         aY2 > bY1;
                if(strictIsect || !edgeIsect)
                    intersects = strictIsect;
                else
                    intersects = (aX1 == bX2) || (aY1 == bY2) || (aX2 == bX1) || (aY2 == bY1);
                return Util::TE_Ok;
            }

            template<typename T>
            inline Util::TAKErr Rectangle2_contains(bool& contains, T aX1, T aY1, T aX2, T aY2, T pX, T pY)
            {
                contains = ((pX >= aX1) && (pX <= aX2)) && ((pY >= aY1) && (pY <= aY2));
                return Util::TE_Ok;
            }

            template<typename T>
            inline Util::TAKErr Rectangle2_contains(bool& contains, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2)
            {
                contains = aX1 <= bX1 && aY1 <= bY1 && aX2 >= bX2 && aY2 >= bY2;
                return Util::TE_Ok;
            }

            template<typename T>
            inline Util::TAKErr Rectangle2_subtract(int& remainders, T aX1, T aY1, T aX2, T aY2, T bX1, T bY1, T bX2, T bY2, Rectangle2<T> remainder[4])
            {
                bool contains(false);
                Rectangle2_contains(contains, bX1, bY1, bX2, bY2, aX1, aY1, aX2, aY2);
                if (contains) {
                    remainders = 0;
                    return Util::TE_Ok;
                }

                bool intersects(true);
                Rectangle2_intersects(intersects, aX1, aY1, aX2, aY2, bX1, bY1, bX2, bY2);
                if (!intersects) {
                    remainder[0] = Rectangle2<T>(aX1, aY1, aX2, aY2);
                    remainders = 1;
                    return Util::TE_Ok;
                }

                // compute the intersection
                T isectX1 = std::max(aX1, bX1);
                T isectY1 = std::max(aY1, bY1);
                T isectX2 = std::min(aX2, bX2);
                T isectY2 = std::min(aY2, bY2);

                remainders = 0;

                // compute top remainder
                const T topX1 = aX1;
                const T topY1 = aY1;
                const T topX2 = aX2;
                const T topY2 = isectY1;
                if (topX2 > topX1 && topY2 > topY1) remainder[remainders++] = Rectangle2<T>(topX1, topY1, topX2, topY2);
                // compute right remainder
                const T rightX1 = isectX2;
                const T rightY1 = isectY1;
                const T rightX2 = aX2;
                const T rightY2 = isectY2;
                if (rightX2 > rightX1 && rightY2 > rightY1) remainder[remainders++] = Rectangle2<T>(rightX1, rightY1, rightX2, rightY2);
                // compute bottom remainder
                const T bottomX1 = aX1;
                const T bottomY1 = isectY2;
                const T bottomX2 = aX2;
                const T bottomY2 = aY2;
                if (bottomX2 > bottomX1 && bottomY2 > bottomY1) remainder[remainders++] = Rectangle2<T>(bottomX1, bottomY1, bottomX2, bottomY2);
                // compute right remainder
                const T leftX1 = aX1;
                const T leftY1 = isectY1;
                const T leftX2 = isectX1;
                const T leftY2 = isectY2;
                if (leftX2 > leftX1 && leftY2 > leftY1) remainder[remainders++] = Rectangle2<T>(leftX1, leftY1, leftX2, leftY2);

                return Util::TE_Ok;
            }

        }
    }
}

#endif
