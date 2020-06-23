#ifndef ATAKMAP_RENDERER_GLMATRIX_H_INCLUDED
#define ATAKMAP_RENDERER_GLMATRIX_H_INCLUDED

#include <stdexcept>

#include "port/Platform.h"

namespace atakmap
{
    namespace renderer
    {

        struct ENGINE_API GLMatrix
        {
        private:
            GLMatrix();
            ~GLMatrix();
        public:
            static void multiply(float *dst, const float *src, const float v);
            static void multiply(float *dst, const float *left, const float *right);
            static void translate(float *dst, const float *src, const float tx, const float ty, const float tz);
            static void scale(float *dst, const float *src, const float sx, const float sy, const float sz);
            static void rotate(float *dst, const float *src, const float degrees, const float ax, const float ay, const float az);
            static void invert(float *dst, const float *src) throw (std::invalid_argument);
            static void identity(float *mx);
            static void orthoM(float *dst, const float left, const float right, const float bottom, const float top, const float near, const float far);
        };
    }
}

#endif
