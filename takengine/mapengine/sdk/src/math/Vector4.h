#ifndef TAK_ENGINE_MATH_VECTOR4_H_INCLUDED
#define TAK_ENGINE_MATH_VECTOR4_H_INCLUDED

#include <cmath>

#include "math/Point2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK 
{
    namespace Engine
    {
        namespace Math 
        {
            template<class T>
            Point2<T> Vector2_add(const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                const T x = a.x + b.x;
                const T y = a.y + b.y;
                const T z = a.z + b.z;
                return Point2<T>(x, y, z);

            }
            template<class T>
            Util::TAKErr Vector2_add(Point2<T> *value, const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                if (!value)
                    return Util::TE_InvalidArg;
                *value = Vector2_add(a, b);
                return Util::TE_Ok;

            }
            template<class T>
            Point2<T> Vector2_subtract(const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                const T x = a.x - b.x;
                const T y = a.y - b.y;
                const T z = a.z - b.z;
                return Point2<T>(x, y, z);
            }
            template<class T>
            Util::TAKErr Vector2_subtract(Point2<T> *value, const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                if (!value)
                    return Util::TE_InvalidArg;
                *value = Vector2_subtract(a, b);
                return Util::TE_Ok;
            }
            template<class T>
            Point2<T> Vector2_multiply(const Point2<T> &src, const T v) NOTHROWS
            {
                const T x = src.x * v;
                const T y = src.y * v;
                const T z = src.z * v;
                return Point2<T>(x, y, z);
            }
            template<class T>
            Util::TAKErr Vector2_multiply(Point2<T> *value, const Point2<T> &src, const T v) NOTHROWS
            {
                if (!value)
                    return Util::TE_InvalidArg;
                *value = Vector2_multiply(src, v);
                return Util::TE_Ok;
            }
            template<class T>
            T Vector2_dot(const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                const T x = a.x * b.x;
                const T y = a.y * b.y;
                const T z = a.z * b.z;
                return x + y + z;
            }
            template<class T>
            Util::TAKErr Vector2_dot(T *value, const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                if (!value)
                    return Util::TE_InvalidArg;
                *value = Vector2_dot(a, b);
                return Util::TE_Ok;
            }
            template<class T>
            Util::TAKErr Vector2_normalize(Point2<T> *value, const Point2<T> &src) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);

                double mag;
                code = Vector2_length(&mag, src);
                TE_CHECKRETURN_CODE(code);

                if (!mag)
                    return Util::TE_Err;

                code = Vector2_multiply(value, src, 1.0 / mag);
                TE_CHECKRETURN_CODE(code);

                return code;
            }
            template<class T>
            Point2<T> Vector2_normalize(const Point2<T>& src) NOTHROWS
            {
                return Vector2_multiply(src, 1.0 / Vector2_length(src));
            }
            template<class T>
            Point2<T> Vector2_cross(const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                const double x = a.y*b.z - a.z*b.y;
                const double y = a.z*b.x - a.x*b.z;
                const double z = a.x*b.y - a.y*b.x;
                return Point2<T>(x, y, z);
            }
            template<class T>
            Util::TAKErr Vector2_cross(Point2<T> *value, const Point2<T> &a, const Point2<T> &b) NOTHROWS
            {
                if (!value)
                    return Util::TE_InvalidArg;
                *value = Vector2_cross(a, b);
                return Util::TE_Ok;
            }
            template<class T>
            double Vector2_length(const Point2<T> &src) NOTHROWS
            {
                return sqrt(src.x*src.x + src.y*src.y + src.z*src.z);
            }
            template<class T>
            Util::TAKErr Vector2_length(double *value, const Point2<T> &src) NOTHROWS
            {
                if (!value)
                    return Util::TE_InvalidArg;
                *value = Vector2_length(src);
                return Util::TE_Ok;
            }
            template<class T>
            bool Vector2_polygonContainsPoint(const Point2<T> &point, const Point2<T> *polygon, size_t polygonLen) NOTHROWS
            {
                size_t i, j;
                bool result = false;
                if (polygonLen <= 0)
                    return false;
                for (i = 0, j = polygonLen - 1; i < polygonLen; j = i++) {
                    if ((polygon[i].y > point.y) != (polygon[j].y > point.y)
                        &&
                        (point.x < (polygon[j].x - polygon[i].x)
                            * (point.y - polygon[i].y)
                            / (polygon[j].y - polygon[i].y) + polygon[i].x)) {
                        result = !result;
                    }
                }
                return result;
            }


            template<class T>
            class Vector4
            {
            public:
                Vector4(T x, T y, T z);
                ~Vector4();
            public:
                void add(const Vector4<T> *v, Vector4<T> *result) const;
                void subtract(const Vector4<T> *v, Vector4<T> *result) const;
                void multiply(const T v, Vector4<T> *result) const;

                double dot(const Vector4<T> *v) const;
                void normalize(Vector4<T> *v) const; // XXX - const precludes use of 'this'?
                void cross(Vector4<T> *v, Vector4<T>* result) const;
                double length() const;
            public:
                T x;
                T y;
                T z;
            };

            template<class T>
            inline Vector4<T>::Vector4(T _x, T _y, T _z) :
                x(_x), y(_y), z(_z)
            {}

            template<class T>
            inline Vector4<T>::~Vector4()
            {}

            template<class T>
            inline void Vector4<T>::add(const Vector4<T> *v, Vector4<T> *result) const
            {
                result->x = x + v->x;
                result->y = y + v->y;
                result->z = z + v->z;
            }

            template<class T>
            inline void Vector4<T>::subtract(const Vector4<T> *v, Vector4<T> *result) const
            {
                result->x = x - v->x;
                result->y = y - v->y;
                result->z = z - v->z;
            }

            template<class T>
            inline void Vector4<T>::multiply(const T v, Vector4<T> *result) const
            {
                result->x = x * v;
                result->y = y * v;
                result->z = z * v;
            }

            template<class T>
            inline double Vector4<T>::dot(const Vector4<T> *v) const
            {
                return x*v->x + y*v->y + z*v->z;
            }

            template<class T>
            inline void Vector4<T>::normalize(Vector4<T> *v) const
            {
                const double length = sqrt((x*x) + (y*y) + (z*z));
                multiply(1.0 / length, v);
            }

            template<class T>
            inline double Vector4<T>::length() const
            {
                return sqrt(this->dot(this));
            }

            template<class T>
            inline void Vector4<T>::cross(Vector4<T> *v, Vector4<T>* result) const
            {
                result->x = this->y*v->z - this->z*v->y;
                result->y = this->z*v->x - this->x*v->z;
                result->z = this->x*v->y - this->y*v->x;
            }
        }
    }
}

#endif // TAK_ENGINE_MATH_VECTOR4_H_INCLUDED
