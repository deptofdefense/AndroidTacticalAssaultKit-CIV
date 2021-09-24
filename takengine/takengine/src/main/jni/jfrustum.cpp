#include "jfrustum.h"

#include <math/Frustum2.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jobject JNICALL Java_com_atakmap_math_Frustum_create
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Matrix2 *mx = JLONG_TO_INTPTR(Matrix2, ptr);
    if(!mx) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer(env, new Frustum2(*mx), false);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_math_Frustum_intersectsSphere
  (JNIEnv *env, jclass clazz, jlong frustumPtr, jlong spherePtr)
{
    Frustum2 *frustum = JLONG_TO_INTPTR(Frustum2, frustumPtr);
    if(!frustum) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    Sphere2 *sphere = JLONG_TO_INTPTR(Sphere2, spherePtr);
    if(!sphere) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    return frustum->intersects(*sphere);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_math_Frustum_intersectsAABB
  (JNIEnv *env, jclass clazz, jlong frustumPtr, jlong aabbPtr)
{
    Frustum2 *frustum = JLONG_TO_INTPTR(Frustum2, frustumPtr);
    if(!frustum) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    AABB *aabb = JLONG_TO_INTPTR(AABB, aabbPtr);
    if(!aabb) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    return frustum->intersects(*aabb);
}
JNIEXPORT void JNICALL Java_com_atakmap_math_Frustum_destruct
  (JNIEnv *env, jclass clazz, jobject mpointer)
{
    Pointer_destruct<Frustum2>(env, mpointer);
}
