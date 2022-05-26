#include "jnativeprojection.h"

#include <cmath>

#include <core/GeoPoint2.h>
#include <core/Projection2.h>
#include <math/Point2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/core/Interop.h"
#include "interop/core/ManagedProjection.h"

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Core;

JNIEXPORT void JNICALL Java_com_atakmap_map_projection_NativeProjection_destruct
  (JNIEnv *env, jclass clazz, jobject pointer)
{
    Pointer_destruct_iface<Projection2>(env, pointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_NativeProjection_wrap
  (JNIEnv *env, jclass clazz, jobject mproj)
{
    Projection2Ptr cproj(NULL, NULL);
    if(mproj)
        cproj = Projection2Ptr(new ManagedProjection(*env, mproj), Memory_deleter_const<Projection2, ManagedProjection>);
    return NewPointer(env, std::move(cproj));
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_projection_NativeProjection_isWrapper
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *cproj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!cproj)
        return false;
    ManagedProjection *cimpl = dynamic_cast<ManagedProjection *>(cproj);
    return !!cimpl;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_projection_NativeProjection_unwrap
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *cproj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!cproj)
        return NULL;
    ManagedProjection *cimpl = dynamic_cast<ManagedProjection *>(cproj);
    if(!cimpl)
        return NULL;
    return env->NewLocalRef(cimpl->impl);
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_projection_NativeProjection_getSrid
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    return proj->getSpatialReferenceID();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_projection_NativeProjection_is3D
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    return proj->is3D();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_projection_NativeProjection_forward
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble latitude, jdouble longitude, jdouble hae, jobject mpoint)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    Point2<double> cpoint;
    code = proj->forward(&cpoint, GeoPoint2(latitude, longitude, hae, AltitudeReference::HAE, NAN, NAN));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    env->SetDoubleField(mpoint, pointD_x, cpoint.x);
    env->SetDoubleField(mpoint, pointD_y, cpoint.y);
    env->SetDoubleField(mpoint, pointD_z, cpoint.z);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_projection_NativeProjection_inverse
  (JNIEnv *env, jclass clazz, jlong ptr, jdouble x, jdouble y, jdouble z, jobject mgeopoint)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }

    TAKErr code(TE_Ok);
    GeoPoint2 cgeopoint;
    code = proj->inverse(&cgeopoint, Point2<double>(x, y, z));
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
    code = Interop_copy(mgeopoint, env, cgeopoint);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_NativeProjection_getMinLatitude
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    return proj->getMinLatitude();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_NativeProjection_getMinLongitude
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    return proj->getMinLongitude();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_NativeProjection_getMaxLatitude
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    return proj->getMaxLatitude();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_projection_NativeProjection_getMaxLongitude
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Projection2 *proj = JLONG_TO_INTPTR(Projection2, ptr);
    if(!proj) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    return proj->getMaxLongitude();
}
