#include "jnativeelevationchunk.h"

#include <cmath>

#include <elevation/ElevationChunk.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<ElevationChunk>(env, jpointer);
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getResolution
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return chunk->getResolution();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_isAuthoritative
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    return chunk->isAuthoritative();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getCE
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return chunk->getCE();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getLE
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }
    return chunk->getLE();
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getUri
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    return env->NewStringUTF(chunk->getUri());
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getType
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    return env->NewStringUTF(chunk->getType());
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getBounds
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    return INTPTR_TO_JLONG(chunk->getBounds());
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_getFlags
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return chunk->getFlags();
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_createData
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    ElevationChunk *chunk = JLONG_TO_INTPTR(ElevationChunk, ptr);
    if(!chunk) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    ElevationChunkDataPtr retval(NULL, NULL);
    code = chunk->createData(retval);
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_Data_1destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<ElevationChunk::Data>(env, jpointer);
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_Data_1getLocalFrame
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ElevationChunk::Data *data = JLONG_TO_INTPTR(ElevationChunk::Data, ptr);
    if(!data) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    return INTPTR_TO_JLONG(&data->localFrame);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_Data_1isInterpolated
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ElevationChunk::Data *data = JLONG_TO_INTPTR(ElevationChunk::Data, ptr);
    if(!data) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    return data->interpolated;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_Data_1getSrid
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ElevationChunk::Data *data = JLONG_TO_INTPTR(ElevationChunk::Data, ptr);
    if(!data) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return -1;
    }

    return data->srid;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_elevation_NativeElevationChunk_Data_1getValue
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ElevationChunk::Data *data = JLONG_TO_INTPTR(ElevationChunk::Data, ptr);
    if(!data) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    return NewPointer<Mesh>(env, data->value);
}
