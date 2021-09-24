#ifndef ATAKMAP_MATH_FRUSTUM_H_INCLUDED
#define ATAKMAP_MATH_FRUSTUM_H_INCLUDED

#include "math/Matrix.h"
#include "math/Sphere.h"
#include "port/Platform.h"

namespace atakmap
{
    namespace math
    {
        class ENGINE_API Frustum
        {
            
        public:
            Frustum(Matrix *proj, Matrix *model);
            Frustum(Matrix *clip);
            void update(Matrix *matrix_clip);
            void update(Matrix *proj, Matrix *model);
            bool intersects(Sphere *s);
            double depthIfInside(Sphere *s);
            Matrix getClip();

        private:
            class Plane;
            Plane* frustum[6];
            Matrix clip;
            Matrix invClip;
            bool sphereDirty;
        };

    }
}

#endif

