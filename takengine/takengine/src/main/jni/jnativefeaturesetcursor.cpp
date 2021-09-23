#include "jnativefeaturesetcursor.h"

#include <cmath>

#include <feature/FeatureSetCursor2.h>

#include "common.h"
#include "interop/Pointer.h"

#include <feature/FeatureDataStore2.h>
#include <feature/FeatureSetCursor2.h>
#include <util/Error.h>
#include <util/Memory.h>

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<FeatureSetCursor2>(env, jpointer);
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getId
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0LL;
    }
    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return 0LL;

    return row->getId();
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getType
   (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return env->NewStringUTF(row->getType());
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getProvider
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return env->NewStringUTF(row->getProvider());
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getName
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;

    return env->NewStringUTF(row->getName());
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getMinResolution
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NAN;

    return row->getMinResolution();
}
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getMaxResolution
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NAN;
    }

    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NAN;

    return row->getMaxResolution();
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_getVersion
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return FeatureDataStore2::FEATURE_VERSION_NONE;
    }

    const FeatureSet2 *row;
    code = result->get(&row);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return FeatureDataStore2::FEATURESET_VERSION_NONE;

    return row->getVersion();
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFeatureSetCursor_moveToNext
  (JNIEnv *env, jclass clazz, jlong pointer)
{
    TAKErr code(TE_Ok);
    FeatureSetCursor2 *result = JLONG_TO_INTPTR(FeatureSetCursor2, pointer);
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
