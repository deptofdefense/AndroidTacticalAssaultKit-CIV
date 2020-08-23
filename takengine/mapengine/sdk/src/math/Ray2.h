#pragma once
#ifndef ATAKMAP_MATH_RAY2_H_INCLUDED
#define ATAKMAP_MATH_RAY2_H_INCLUDED

#include "math/Point2.h"
#include "math/Vector4.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math 
        {

            template<class T>
            class Ray2
            {
            public:
                Ray2(const Point2<T> &origin, const Vector4<T> &direction);
                ~Ray2();
            public:
                Point2<T> origin;
                Vector4<T> direction;
            };

            template<class T>
            inline Ray2<T>::Ray2(const Point2<T> &o, const Vector4<T> &dir) :
                origin(o.x, o.y, o.z),
                direction(dir.x, dir.y, dir.z)
            {
                direction.normalize(&direction);
            }

            template<class T>
            inline Ray2<T>::~Ray2() {}

#ifdef MSVC
			template class ENGINE_API Ray2<double>;
#endif
        }
    }
}

#endif // ATAKMAP_MATH_RAY2_H_INCLUDED