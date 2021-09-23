#ifndef CESIUM3DTILES_MATRIX_H_INCLUDED
#define CESIUM3DTILES_MATRIX_H_INCLUDED

#include <cmath>

template<class T, class A, class B>
void Matrix_concatenate(T *value, const A *a, const B *b);
template<class T, class A>
void Matrix_rotate(T *value, const A *mx, const double angle, const double x, const double y, const double z);
template<class T, class A>
void Matrix_translate(T *value, const A *mx, const double x, const double y, const double z);
template<class T, class A>
void Matrix_scale(T *value, const A *mx, const double x, const double y, const double z);
template<class T>
void Matrix_identity(T *value);


template<class T, class A, class B>
inline void Matrix_concatenate(T *value, const A *a, const B *b)
{
#define m00 a[0]
#define m01 a[4]
#define m02 a[8]
#define m03 a[12]
#define m10 a[1]
#define m11 a[5]
#define m12 a[9]
#define m13 a[13]
#define m20 a[2]
#define m21 a[6]
#define m22 a[10]
#define m23 a[14]
#define m30 a[3]
#define m31 a[7]
#define m32 a[11]
#define m33 a[15]
#define tm00 b[0]
#define tm01 b[4]
#define tm02 b[8]
#define tm03 b[12]
#define tm10 b[1]
#define tm11 b[5]
#define tm12 b[9]
#define tm13 b[13]
#define tm20 b[2]
#define tm21 b[6]
#define tm22 b[10]
#define tm23 b[14]
#define tm30 b[3]
#define tm31 b[7]
#define tm32 b[11]
#define tm33 b[15]

    const double rm00 = m00*tm00 + m01*tm10 + m02*tm20 + m03*tm30;
    const double rm01 = m00*tm01 + m01*tm11 + m02*tm21 + m03*tm31;
    const double rm02 = m00*tm02 + m01*tm12 + m02*tm22 + m03*tm32;
    const double rm03 = m00*tm03 + m01*tm13 + m02*tm23 + m03*tm33;
    const double rm10 = m10*tm00 + m11*tm10 + m12*tm20 + m13*tm30;
    const double rm11 = m10*tm01 + m11*tm11 + m12*tm21 + m13*tm31;
    const double rm12 = m10*tm02 + m11*tm12 + m12*tm22 + m13*tm32;
    const double rm13 = m10*tm03 + m11*tm13 + m12*tm23 + m13*tm33;
    const double rm20 = m20*tm00 + m21*tm10 + m22*tm20 + m23*tm30;
    const double rm21 = m20*tm01 + m21*tm11 + m22*tm21 + m23*tm31;
    const double rm22 = m20*tm02 + m21*tm12 + m22*tm22 + m23*tm32;
    const double rm23 = m20*tm03 + m21*tm13 + m22*tm23 + m23*tm33;
    const double rm30 = m30*tm00 + m31*tm10 + m32*tm20 + m33*tm30;
    const double rm31 = m30*tm01 + m31*tm11 + m32*tm21 + m33*tm31;
    const double rm32 = m30*tm02 + m31*tm12 + m32*tm22 + m33*tm32;
    const double rm33 = m30*tm03 + m31*tm13 + m32*tm23 + m33*tm33;

    value[0] = rm00; value[4] = rm01; value[8] = rm02; value[12] = rm03;
    value[1] = rm10; value[5] = rm11; value[9] = rm12; value[13] = rm13;
    value[2] = rm20; value[6] = rm21; value[10] = rm22; value[14] = rm23;
    value[3] = rm30; value[7] = rm31; value[11] = rm32; value[15] = rm33;
}

template<class T, class A>
inline void Matrix_rotate(T *value, const A *mx, const double angle, const double x, const double y, const double z)
{
    // normalize the axis
    const double axis_mag = sqrt(x*x + y*y + z*z);
    const double xn = x / axis_mag;
    const double yn = y / axis_mag;
    const double zn = z / axis_mag;

    double angle_rad = angle * (M_PI/180.0);

    const double c = cos(angle_rad);
    const double s = sin(angle_rad);
    const double t = 1.0 - c;

    double rot[16];
    Matrix_identity(rot);
    rot[0] = c+x*x*t;   rot[1] = y*x*t+z*s; rot[2] = z*x*t-y*s;
    rot[4] = x*y*t-z*s; rot[5] = c+y*y*t;   rot[6] = z*y*t+x*s;
    rot[8] = x*z*t+y*s; rot[9] = y*z*t-x*s; rot[10] = z*z*t+c;

    Matrix_concatenate(value, mx, rot);
}
template<class T, class A>
inline void Matrix_translate(T *value, const A *mx, const double x, const double y, const double z)
{
    double tx[16];
    Matrix_identity(tx);
    tx[12] = x;
    tx[13] = y;
    tx[14] = z;
    Matrix_concatenate(value, mx, tx);
}
template<class T, class A>
inline void Matrix_scale(T *value, const A *mx, const double x, const double y, const double z)
{
    double sx[16];
    Matrix_identity(sx);
    sx[0] = x;
    sx[5] = y;
    sx[10] = z;
    Matrix_concatenate(value, mx, sx);
}
template<class T>
inline void Matrix_identity(T *value)
{
    value[0] = 1.0; value[4] = 0.0; value[8] = 0.0; value[12] = 0.0;
    value[1] = 0.0; value[5] = 1.0; value[9] = 0.0; value[13] = 0.0;
    value[2] = 0.0; value[6] = 0.0; value[10] = 1.0; value[14] = 0.0;
    value[3] = 0.0; value[7] = 0.0; value[11] = 0.0; value[15] = 1.0;
}

#endif
