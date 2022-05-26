#include "jnativegeometrymodel.h"

#include <math/AABB.h>
#include <math/Ellipsoid2.h>
#include <math/GeometryModel2.h>
#include <math/Plane2.h>
#include <math/Point2.h>
#include <math/Ray2.h>
#include <math/Sphere2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/math/ManagedGeometryModel.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Math;

JNIEXPORT void JNICALL Java_com_atakmap_math_NativeGeometryModel_destruct
  (JNIEnv *env, jclass clazz, jobject pointer)
{
    Pointer_destruct_iface<GeometryModel2>(env, pointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_clone
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GeometryModel2 *gm = JLONG_TO_INTPTR(GeometryModel2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    GeometryModel2Ptr retval(NULL, NULL);
    gm->clone(retval);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_math_NativeGeometryModel_isWrapped
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GeometryModel2 *cgm = JLONG_TO_INTPTR(GeometryModel2, ptr);
    if(!cgm)
        return false;
    ManagedGeometryModel *cimpl = dynamic_cast<ManagedGeometryModel *>(cgm);
    return !!cimpl;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_unwrap
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GeometryModel2 *cgm = JLONG_TO_INTPTR(GeometryModel2, ptr);
    if(!cgm)
        return NULL;
    ManagedGeometryModel *cimpl = dynamic_cast<ManagedGeometryModel *>(cgm);
    if(!cimpl)
        return NULL;
    return env->NewLocalRef(cimpl->impl);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_wrap
  (JNIEnv *env, jclass clazz, jobject mgm)
{
    GeometryModel2Ptr retval(NULL, NULL);
    if(mgm)
        retval = GeometryModel2Ptr(new ManagedGeometryModel(*env, mgm), Memory_deleter_const<GeometryModel2, ManagedGeometryModel>);
    return NewPointer(env, std::move(retval));
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_math_NativeGeometryModel_intersect
  (JNIEnv *env,
   jclass clazz,
   jlong pointer,
   jdouble rox, jdouble roy, jdouble roz,
   jdouble rdx, jdouble rdy, jdouble rdz,
   jobject jisect)
{
    GeometryModel2 *model = JLONG_TO_INTPTR(GeometryModel2, pointer);
    Point2<double> isect;
    if(!model->intersect(&isect,
                         Ray2<double>(Point2<double>(rox, roy, roz),
                                      Vector4<double>(rdx, rdy, rdz)))) {
        
        return false;
    }

    env->SetDoubleField(jisect, pointD_x, isect.x);
    env->SetDoubleField(jisect, pointD_y, isect.y);
    env->SetDoubleField(jisect, pointD_z, isect.z);

    return true;
}

JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeomClass
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    GeometryModel2 *gm = JLONG_TO_INTPTR(GeometryModel2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return gm->getGeomClass();
}

JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1create
  (JNIEnv *env, jclass clazz, jdouble cx, jdouble cy, jdouble cz, jdouble rx, jdouble ry, jdouble rz)
{
    GeometryModel2Ptr retval(new Ellipsoid2(Point2<double>(cx, cy, cz), rx, ry, rz), Memory_deleter_const<GeometryModel2, Ellipsoid2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1getCenterX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Ellipsoid2 *gm = JLONG_TO_INTPTR(Ellipsoid2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->center.x;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1getCenterY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Ellipsoid2 *gm = JLONG_TO_INTPTR(Ellipsoid2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->center.y;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1getCenterZ
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Ellipsoid2 *gm = JLONG_TO_INTPTR(Ellipsoid2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->center.z;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1getRadiusX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Ellipsoid2 *gm = JLONG_TO_INTPTR(Ellipsoid2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->radiusX;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1getRadiusY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Ellipsoid2 *gm = JLONG_TO_INTPTR(Ellipsoid2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->radiusY;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Ellipsoid_1getRadiusZ
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Ellipsoid2 *gm = JLONG_TO_INTPTR(Ellipsoid2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->radiusZ;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_Sphere_1create
  (JNIEnv *env, jclass clazz, jdouble cx, jdouble cy, jdouble cz, jdouble r)
{
    GeometryModel2Ptr retval(new Sphere2(Point2<double>(cx, cy, cz), r), Memory_deleter_const<GeometryModel2, Sphere2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Sphere_1getCenterX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Sphere2 *gm = JLONG_TO_INTPTR(Sphere2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->center.x;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Sphere_1getCenterY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Sphere2 *gm = JLONG_TO_INTPTR(Sphere2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->center.y;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Sphere_1getCenterZ
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Sphere2 *gm = JLONG_TO_INTPTR(Sphere2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->center.z;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_Sphere_1getRadius
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Sphere2 *gm = JLONG_TO_INTPTR(Sphere2, ptr);
    if(!gm) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return gm->radius;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1create
  (JNIEnv *env, jclass clazz, jdouble minX, jdouble minY, jdouble minZ, jdouble maxX, jdouble maxY, jdouble maxZ)
{
    GeometryModel2Ptr retval(new AABB(Point2<double>(minX, minY, minZ), Point2<double>(maxX, maxY, maxZ)), Memory_deleter_const<GeometryModel2, AABB>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1getMinX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    AABB *aabb = JLONG_TO_INTPTR(AABB, ptr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return aabb->minX;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1getMinY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    AABB *aabb = JLONG_TO_INTPTR(AABB, ptr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return aabb->minY;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1getMinZ
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    AABB *aabb = JLONG_TO_INTPTR(AABB, ptr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return aabb->minZ;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1getMaxX
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    AABB *aabb = JLONG_TO_INTPTR(AABB, ptr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return aabb->maxX;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1getMaxY
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    AABB *aabb = JLONG_TO_INTPTR(AABB, ptr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return aabb->maxY;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_math_NativeGeometryModel_AABB_1getMaxZ
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    AABB *aabb = JLONG_TO_INTPTR(AABB, ptr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return aabb->maxZ;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_math_NativeGeometryModel_Plane_1create
  (JNIEnv *env, jclass clazz, jdouble nx, jdouble ny, jdouble nz, jdouble px, jdouble py, jdouble pz)
{
    GeometryModel2Ptr retval(new Plane2(Vector4<double>(nx, ny, nz), Point2<double>(px, py, pz)), Memory_deleter_const<GeometryModel2, Plane2>);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1PLANE
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::PLANE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1ELLIPSOID
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::ELLIPSOID;
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1SPHERE
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::SPHERE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1TRIANGLE
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::TRIANGLE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1MESH
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::MESH;
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1AABB
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::AABB;
}
JNIEXPORT jint JNICALL Java_com_atakmap_math_NativeGeometryModel_getGeometryModel2_1GeometryClass_1UNDEFINED
  (JNIEnv *env, jclass clazz)
{
    return GeometryModel2::UNDEFINED;
}
