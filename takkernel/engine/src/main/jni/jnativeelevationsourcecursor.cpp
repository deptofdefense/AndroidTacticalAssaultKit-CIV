#include "jnativeelevationsourcecursor.h"

#include <cmath>

#include <elevation/ElevationChunkCursor.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<ElevationChunkCursor>(env, jpointer);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_moveToNext
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    code = result->moveToNext();
    if(code == TE_Ok)
        return true;
    else if(code == TE_Done)
        return false;

    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_get
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    ElevationChunkPtr retval(NULL, NULL);
    code = result->get(retval);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getResolution
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    double retval;
    code = result->getResolution(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return retval;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_isAuthoritative
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    bool retval;
    code = result->isAuthoritative(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return retval;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getCE
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    double retval;
    code = result->getCE(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return retval;
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getLE
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    double retval;
    code = result->getLE(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return retval;
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getUri
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    const char *retval;
    code = result->getUri(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return env->NewStringUTF(retval);
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getType
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    const char *retval;
    code = result->getType(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return env->NewStringUTF(retval);
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getBounds
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    const Polygon2 *retval;
    code = result->getBounds(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return INTPTR_TO_JLONG(retval);
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationSourceCursor_getFlags
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunkCursor *result = JLONG_TO_INTPTR(ElevationChunkCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    unsigned int retval;
    code = result->getFlags(&retval) ;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return retval;
}
