#include "jnativefilecursor.h"

#include <feature/DataSourceFeatureDataStore2.h>
#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_NativeFileCursor_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct_iface<DataSourceFeatureDataStore2::FileCursor>(env, jpointer);
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_NativeFileCursor_getFile
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    DataSourceFeatureDataStore2::FileCursor *result = JLONG_TO_INTPTR(DataSourceFeatureDataStore2::FileCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    TAKErr code(TE_Ok);
    TAK::Engine::Port::String cpath;
    code = result->getFile(cpath);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return cpath ? env->NewStringUTF(cpath) : NULL;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_NativeFileCursor_moveToNext
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    DataSourceFeatureDataStore2::FileCursor *result = JLONG_TO_INTPTR(DataSourceFeatureDataStore2::FileCursor, ptr);
    if(!result) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }

    TAKErr code(TE_Ok);
    code = result->moveToNext();
    if(code == TE_Done)
        return false;
    else if(code == TE_Ok)
        return true;

    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}