#ifndef ATAKMAP_MATH_FRUSTUM2_H_INCLUDED
#define ATAKMAP_MATH_FRUSTUM2_H_INCLUDED

#include "math/Matrix2.h"
#include "math/Sphere2.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {

            class ENGINE_API Frustum2
            {
            private :
                struct Plane
                {
                    Plane();
                    Plane(const Vector4<double> &normal, double dist);

                    Vector4<double> normal;
                    double dist;
                };
            public:
                Frustum2(Matrix2 &proj, Matrix2 &model) NOTHROWS;
                Frustum2(Matrix2 &clip) NOTHROWS;
                void update(Matrix2 &matrix_clip) NOTHROWS;
                void update(Matrix2 &proj, Matrix2 &model) NOTHROWS;
                bool intersects(Sphere2 &s) NOTHROWS;
                double depthIfInside(Sphere2 &s) NOTHROWS;
                Matrix2 getClip() NOTHROWS;
            private :
                static void normalize(Plane *dst, const Plane &src);
                static double distance(const Plane &dst, const Vector4<double> &v);
            private:
                Plane frustum[6];
                Matrix2 clip;
                Matrix2 invClip;
                bool sphereDirty;
            };

        }
    }
}

#endif

