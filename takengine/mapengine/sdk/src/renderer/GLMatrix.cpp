#include "renderer/GLMatrix.h"

static const float IDENTITY[] = { 1, 0, 0, 0,
                                  0, 1, 0, 0,
                                  0, 0, 1, 0,
                                  0, 0, 0, 1 };

#include <string.h>
#include <math.h>
#include <stdexcept>

//using namespace atakmap::math;

namespace
{
    float vdot(const float *a, const float *b)
    {
        float accum = 0;
        for (int i = 0; i < 4; ++i)
            accum += a[i] * b[i];
        return accum;
    }
}

namespace atakmap
{
    namespace renderer
    {


        GLMatrix::GLMatrix()
        {}
        GLMatrix::~GLMatrix()
        {}

        void GLMatrix::multiply(float *dst, const float *src, const float v)
        {
#if 1
            for (int x = 0; x < 16; x++)
            {
                dst[x] = src[x] * v;
            }
#endif
        }

        void GLMatrix::multiply(float *dst, const float *left, const float *right)
        {
#if 1
            float xCmp[4] = { left[0], left[4], left[8], left[12] };
            float yCmp[4] = { left[1], left[5], left[9], left[13] };
            float zCmp[4] = { left[2], left[6], left[10], left[14] };
            float wCmp[4] = { left[3], left[7], left[11], left[15] };
            const float *x = right + 0;
            const float *y = right + 4;
            const float *z = right + 8;
            const float *w = right + 12;

            float res[16] = {
                vdot(x, xCmp), vdot(x, yCmp), vdot(x, zCmp), vdot(x, wCmp),
                vdot(y, xCmp), vdot(y, yCmp), vdot(y, zCmp), vdot(y, wCmp),
                vdot(z, xCmp), vdot(z, yCmp), vdot(z, zCmp), vdot(z, wCmp),
                vdot(w, xCmp), vdot(w, yCmp), vdot(w, zCmp), vdot(w, wCmp),
            };
            memcpy(dst, res, 16*sizeof(float));
#endif
        }

        void GLMatrix::translate(float *dst, const float *src, const float tx, const float ty, const float tz)
        {
#if 1
            float temp[16];
            memset(temp, 0, 16*sizeof(float));
            temp[0] = 1;
            temp[5] = 1;
            temp[10] = 1;
            temp[12] = tx;
            temp[13] = ty;
            temp[14] = tz;
            temp[15] = 1;
#if 1
            multiply(dst, src, temp);
#else
            memcpy(dst, temp, 16*sizeof(float));
#endif
#endif
        }

        void GLMatrix::scale(float *dst, const float *src, const float sx, const float sy, const float sz)
        {
#if 1
            float temp[16];
            memset(temp, 0, 16*sizeof(float));
            temp[0] = sx;
            temp[5] = sy;
            temp[10] = sz;
            temp[15] = 1;

#if 1
            multiply(dst, src, temp);
#else
            memcpy(dst, temp, 16*sizeof(float));
#endif
#endif
        }

        void GLMatrix::rotate(float *dst, const float *src, const float degrees, const float axisX, const float axisY, const float axisZ)
        {
#if 1
            const auto theta = static_cast<float>(degrees * M_PI / 180.0f);
            const float sinTheta = static_cast<float>(sin(theta));
            const float cosTheta = static_cast<float>(cos(theta));

            const float mag = static_cast<float>(sqrt((axisX*axisX) + (axisY*axisY) + (axisZ*axisZ)));

            const float nX = axisX / mag;
            const float nY = axisY / mag;
            const float nZ = axisZ / mag;

            const float oneMinusCos = 1-cosTheta;

            float f[16] = 
            {
                (float)(nX*nX*(oneMinusCos)+cosTheta),       (float)(nX*nY*(oneMinusCos)+axisZ*sinTheta), (float)(nX*nZ*(oneMinusCos)-axisY*sinTheta), 0,
                (float)(nY*nX*(oneMinusCos)-axisZ*sinTheta), (float)(nY*nY*(oneMinusCos)+cosTheta),       (float)(nY*nZ*(oneMinusCos)+axisX*sinTheta), 0,
                (float)(nZ*nX*(oneMinusCos)+axisY*sinTheta), (float)(nZ*nY*(oneMinusCos)-axisX*sinTheta), (float)(nZ*nZ*(oneMinusCos)+cosTheta),       0,
                0, 0, 0, 1
            };

#if 1
            multiply(dst, src, f);
#else
            memcpy(dst, f, 16*sizeof(float));
#endif
#endif
        }


        void GLMatrix::invert(float *dst, const float *m) throw (std::invalid_argument)
        {
#if 1
            float detA = m[0] * m[5] * m[10] * m[15] + m[0] * m[6] * m[11] * m[13] + m[0] * m[7] * m[9] * m[14]
                + m[1] * m[4] * m[11] * m[14] + m[1] * m[6] * m[8] * m[15] + m[1] * m[7] * m[10] * m[12]
                + m[2] * m[4] * m[9] * m[15] + m[2] * m[5] * m[11] * m[12] + m[2] * m[7] * m[8] * m[13]
                + m[3] * m[4] * m[10] * m[13] + m[3] * m[5] * m[8] * m[14] + m[3] * m[6] * m[9] * m[12]

                - m[0] * m[5] * m[11] * m[14] - m[0] * m[6] * m[9] * m[15] - m[0] * m[7] * m[10] * m[13]
                - m[1] * m[4] * m[10] * m[15] - m[1] * m[6] * m[11] * m[12] - m[1] * m[7] * m[8] * m[14]
                - m[2] * m[4] * m[11] * m[13] - m[2] * m[5] * m[8] * m[15] - m[2] * m[7] * m[9] * m[12]
                - m[3] * m[4] * m[9] * m[14] - m[3] * m[5] * m[10] * m[12] - m[3] * m[6] * m[8] * m[13];

            if (detA == 0)
                throw std::invalid_argument("Cannot invert provided matrix");

            float f[16] = {
                m[5] * m[10] * m[15] + m[6] * m[11] * m[13] + m[7] * m[9] * m[14] - m[5] * m[11] * m[14] - m[6] * m[9] * m[15] - m[7] * m[10] * m[13],
                    m[1] * m[11] * m[14] + m[2] * m[9] * m[15] + m[3] * m[10] * m[13] - m[1] * m[10] * m[15] - m[2] * m[11] * m[13] - m[3] * m[9] * m[14],
                    m[1] * m[6] * m[15] + m[2] * m[7] * m[13] + m[3] * m[5] * m[14] - m[1] * m[7] * m[14] - m[2] * m[5] * m[15] - m[3] * m[6] * m[13],
                    m[1] * m[7] * m[10] + m[2] * m[5] * m[11] + m[3] * m[6] * m[9] - m[1] * m[6] * m[11] - m[2] * m[7] * m[9] - m[3] * m[5] * m[10],

                    m[4] * m[11] * m[14] + m[6] * m[8] * m[15] + m[7] * m[10] * m[12] - m[4] * m[10] * m[15] - m[6] * m[11] * m[12] - m[7] * m[8] * m[14],
                    m[0] * m[10] * m[15] + m[2] * m[11] * m[12] + m[3] * m[8] * m[14] - m[0] * m[11] * m[14] - m[2] * m[8] * m[15] - m[3] * m[10] * m[12],
                    m[0] * m[7] * m[14] + m[2] * m[4] * m[15] + m[3] * m[6] * m[12] - m[0] * m[6] * m[15] - m[2] * m[7] * m[12] - m[3] * m[4] * m[14],
                    m[0] * m[6] * m[11] + m[2] * m[7] * m[8] + m[3] * m[4] * m[10] - m[0] * m[7] * m[10] - m[2] * m[4] * m[11] - m[3] * m[6] * m[8],

                    m[4] * m[9] * m[15] + m[5] * m[11] * m[12] + m[7] * m[8] * m[13] - m[4] * m[11] * m[13] - m[5] * m[8] * m[15] - m[7] * m[9] * m[12],
                    m[0] * m[11] * m[13] + m[1] * m[8] * m[15] + m[3] * m[9] * m[12] - m[0] * m[9] * m[15] - m[1] * m[11] * m[12] - m[3] * m[8] * m[13],
                    m[0] * m[5] * m[15] + m[1] * m[7] * m[12] + m[3] * m[4] * m[13] - m[0] * m[7] * m[13] - m[1] * m[4] * m[15] - m[3] * m[5] * m[12],
                    m[0] * m[7] * m[9] + m[1] * m[4] * m[11] + m[3] * m[5] * m[8] - m[0] * m[5] * m[11] - m[1] * m[7] * m[8] - m[3] * m[4] * m[9],

                    m[4] * m[10] * m[13] + m[5] * m[8] * m[14] + m[6] * m[9] * m[12] - m[4] * m[9] * m[14] - m[5] * m[10] * m[12] - m[6] * m[8] * m[13],
                    m[0] * m[9] * m[14] + m[1] * m[10] * m[12] + m[2] * m[8] * m[13] - m[0] * m[10] * m[13] - m[1] * m[8] * m[14] - m[2] * m[9] * m[12],
                    m[0] * m[6] * m[13] + m[1] * m[4] * m[14] + m[2] * m[5] * m[12] - m[0] * m[5] * m[14] - m[1] * m[6] * m[12] - m[2] * m[4] * m[13],
                    m[0] * m[5] * m[10] + m[1] * m[6] * m[8] + m[2] * m[4] * m[9] - m[0] * m[6] * m[9] - m[1] * m[4] * m[10] - m[2] * m[5] * m[8],
            };

            memcpy(dst, f, 16*sizeof(float));
            multiply(dst, dst, 1.0f / detA);
#endif
        }

        void GLMatrix::identity(float *dst)
        {
            memcpy(dst, IDENTITY, 16*sizeof(float));
        }

        void GLMatrix::orthoM(float *dst, const float left, const float right, const float bottom, const float top, const float near, const float far)
        {
            float rw = 1.0f / (right - left);
            float rh = 1.0f / (top - bottom);
            float rd = 1.0f / (far - near);
            float x = 2.0f * rw;
            float y = 2.0f * rh;
            float z = -2.0f * rd;
            float tx = -(right + left) * rw;
            float ty = -(top + bottom) * rh;
            float tz = -(far + near) * rd;
            float f[16] = {
                x, 0, 0, 0,
                    0, y, 0, 0,
                    0, 0, z, 0,
                    tx, ty, tz, 1.0
            };
            memcpy(dst, f, 16*sizeof(float));
        }
    }

}