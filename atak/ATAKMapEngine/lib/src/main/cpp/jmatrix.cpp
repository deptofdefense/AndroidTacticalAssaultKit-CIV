#include "jmatrix.h"

#include <cmath>

#include <math/Matrix2.h>
#include <math/Point2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_math_Matrix_clone
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Matrix2 *matrix = JLONG_TO_INTPTR(Matrix2, ptr);
    if(!matrix) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    Matrix2Ptr retval(new Matrix2(*matrix), Memory_deleter_const<Matrix2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_Matrix_create__
  (JNIEnv *env, jclass clazz)
{
    Matrix2Ptr retval(new Matrix2(), Memory_deleter_const<Matrix2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_Matrix_create__DDDDDDDDDDDDDDDD
  (JNIEnv *env, jclass c, jdouble m00, jdouble m01, jdouble m02, jdouble m03,
                          jdouble m10, jdouble m11, jdouble m12, jdouble m13,
                          jdouble m20, jdouble m21, jdouble m22, jdouble m23,
                          jdouble m30, jdouble m31, jdouble m32, jdouble m33)
{
    Matrix2Ptr retval(new Matrix2(m00, m01, m02, m03,
                                  m10, m11, m12, m13,
                                  m20, m21, m22, m23,
                                  m30, m31, m32, m33),
                      Memory_deleter_const<Matrix2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_destruct
  (JNIEnv *env, jclass clazz, jobject ptr)
{
    Pointer_destruct<Matrix2>(env, ptr);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_transform
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble srcX, jdouble srcY, jdouble srcZ, jobject jdst)
{
    Point2<double> p(srcX, srcY, srcZ);
    
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);

    p = xform->transform(p);
    env->SetDoubleField(jdst, pointD_x, p.x);
    env->SetDoubleField(jdst, pointD_y, p.y);
    env->SetDoubleField(jdst, pointD_z, p.z);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_Matrix_createInverse
  (JNIEnv *env, jclass clazz, jlong srcPtr)
{
    Matrix2 *src = JLONG_TO_INTPTR(Matrix2, srcPtr);
    if(!src) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    Matrix2Ptr dst(new Matrix2(), Memory_deleter_const<Matrix2>);

    TAKErr code = src->createInverse(dst.get());
    return (code == TE_Ok) ? NewPointer(env, std::move(dst)) : NULL;
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_concatenate
  (JNIEnv *env, jclass clazz, jlong selfPtr, jlong otherPtr)
{
    Matrix2 *self = JLONG_TO_INTPTR(Matrix2, selfPtr);
    Matrix2 *other = JLONG_TO_INTPTR(Matrix2, otherPtr);

    self->concatenate(*other);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_preConcatenate
  (JNIEnv *env, jclass clazz, jlong selfPtr, jlong otherPtr)
{
    Matrix2 *self = JLONG_TO_INTPTR(Matrix2, selfPtr);
    Matrix2 *other = JLONG_TO_INTPTR(Matrix2, otherPtr);

    self->preConcatenate(*other);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_set__JJ
  (JNIEnv *env, jclass clazz, jlong selfPtr, jlong otherPtr)
{
    Matrix2 *self = JLONG_TO_INTPTR(Matrix2, selfPtr);
    Matrix2 *other = JLONG_TO_INTPTR(Matrix2, otherPtr);

    *self = *other;
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_set__JIID
  (JNIEnv *env, jclass clazz, jlong ptr, jint row, jint col, jdouble v)
{
    Matrix2 *self = JLONG_TO_INTPTR(Matrix2, ptr);
    TAKErr code = self->set(row, col, v);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_getRowMajor
  (JNIEnv *env, jclass clazz, jlong ptr, jdoubleArray jarr)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);

    jdouble *arr = env->GetDoubleArrayElements(jarr, NULL);
    xform->get(reinterpret_cast<double *>(arr), Matrix2::MatrixOrder::ROW_MAJOR);
    env->ReleaseDoubleArrayElements(jarr, arr, 0);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_getColumnMajor
  (JNIEnv *env, jclass clazz, jlong ptr, jdoubleArray jarr)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);

    jdouble *arr = env->GetDoubleArrayElements(jarr, NULL);
    xform->get(reinterpret_cast<double *>(arr), Matrix2::MatrixOrder::COLUMN_MAJOR);
    env->ReleaseDoubleArrayElements(jarr, arr, 0);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_Matrix_get
  (JNIEnv *env, jclass clazz, jlong ptr, jint row, jint col)
{
    Matrix2 *self = JLONG_TO_INTPTR(Matrix2, ptr);
    double v;
    TAKErr code = self->get(&v, row, col);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NAN;
    return v;
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_translate__JDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble tx, jdouble ty)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->translate(tx, ty);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_translate__JDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble tx, jdouble ty, jdouble tz)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->translate(tx, ty, tz);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_rotate__JD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->rotate(theta);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_rotate__JDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble anchorX, jdouble anchorY)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->rotate(theta, anchorX, anchorY);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_rotate__JDDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble axisX, jdouble axisY, jdouble axisZ)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->rotate(theta, axisX, axisY, axisZ);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_rotate__JDDDDDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble anchorX, jdouble anchorY, jdouble anchorZ, jdouble axisX, jdouble axisY, jdouble axisZ)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->translate(anchorX, anchorY, anchorZ);
    xform->rotate(theta, axisX, axisY, axisZ);
    xform->translate(-anchorX, -anchorY, -anchorZ);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_scale__JD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble s)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->scale(s);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_scale__JDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble sx, jdouble sy)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->scale(sx, sy);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_scale__JDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble sx, jdouble sy, jdouble sz)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->scale(sx, sy, sz);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToIdentity
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToIdentity();
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToTranslate__JDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble tx, jdouble ty)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToTranslate(tx, ty);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToTranslate__JDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble tx, jdouble ty, jdouble tz)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToTranslate(tx, ty, tz);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToRotate__JD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToRotate(theta);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToRotate__JDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble theta, jdouble anchorX, jdouble anchorY)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToRotate(theta, anchorX, anchorY);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToScale__JD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble s)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToScale(s);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToScale__JDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble sx, jdouble sy)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToScale(sx, sy);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Matrix_setToScale__JDDD
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble sx, jdouble sy, jdouble sz)
{
    Matrix2 *xform = JLONG_TO_INTPTR(Matrix2, ptr);
    xform->setToScale(sx, sy, sz);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_Matrix_mapQuadsNative
  (JNIEnv *env,
   jclass clazz,
   jdouble srcX1, jdouble srcY1,
   jdouble srcX2, jdouble srcY2,
   jdouble srcX3, jdouble srcY3,
   jdouble srcX4, jdouble srcY4,
   jdouble dstX1, jdouble dstY1,
   jdouble dstX2, jdouble dstY2,
   jdouble dstX3, jdouble dstY3,
   jdouble dstX4, jdouble dstY4)
{
    Matrix2Ptr retval(new Matrix2(), Memory_deleter_const<Matrix2>);
    TAKErr code = Matrix2_mapQuads(retval.get(),
                                   srcX1, srcY1,
                                   srcX2, srcY2,
                                   srcX3, srcY3,
                                   srcX4, srcY4,
                                   dstX1, dstY1,
                                   dstX2, dstY2,
                                   dstX3, dstY3,
                                   dstX4, dstY4);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return NewPointer(env, std::move(retval));
}
