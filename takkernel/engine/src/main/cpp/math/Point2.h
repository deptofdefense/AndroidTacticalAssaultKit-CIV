#ifndef TAK_ENGINE_MATH_POINT2_H_INCLUDED
#define TAK_ENGINE_MATH_POINT2_H_INCLUDED

#include "port/Platform.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {

            template <class T>
            class Point2 {
            public:
                Point2();
                Point2(T x, T y);
                Point2(T x, T y, T z);
                ~Point2();
            public:
                T x;
                T y;
                T z;
            };

            template <class T>
            inline Point2<T>::Point2() : x(0), y(0), z(0) {}

            template <class T>
            inline Point2<T>::Point2(T _x, T _y) : x(_x), y(_y), z(0) {}

            template <class T>
            inline Point2<T>::Point2(T _x, T _y, T _z) : x(_x), y(_y), z(_z) {}

            template <class T>
            inline Point2<T>::~Point2() {}

            template <class T>
            inline bool operator==(const Point2<T>& a, const Point2<T>& b) NOTHROWS {
                return a.x == b.x &&
                    a.y == b.y &&
                    a.z == b.z;
            }

            template <class T>
            inline bool operator!=(const Point2<T>& a, const Point2<T>& b) NOTHROWS {
                return a.x != b.x ||
                    a.y != b.y ||
                    a.z != b.z;
            }

#ifdef MSVC
			template class ENGINE_API Point2<double>;
#endif

        } // end namespace TAK::Engine::math
    } // end namespace TAK::Engine
}
#endif // TAK_ENGINE_MATH_POINT2_H_INCLUDED
