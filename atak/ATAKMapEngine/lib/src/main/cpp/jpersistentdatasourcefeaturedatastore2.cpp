#include "jpersistentdatasourcefeaturedatastore2.h"

#include <feature/PersistentDataSourceFeatureDataStore2.h>
#include <util/Logging2.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    typedef std::unique_ptr<PersistentDataSourceFeatureDataStore2, void(*)(const PersistentDataSourceFeatureDataStore2 *)> DataStoreImplPtr;
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_create
  (JNIEnv *env, jclass clazz, jstring jpath)
{
    JNIStringUTF path(*env, jpath);
    DataStoreImplPtr retval(new PersistentDataSourceFeatureDataStore2(), Memory_deleter_const<PersistentDataSourceFeatureDataStore2>);
    TAKErr code = retval->open(path);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_destruct
  (JNIEnv *env, jclass clazz, jobject jpointer)
{
    Pointer_destruct<PersistentDataSourceFeatureDataStore2>(env, jpointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_asBase
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl)
        return NULL;
    return NewPointer(env, impl, true);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_contains
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    JNIStringUTF path(*env, jpath);
    bool value;
    code = impl->contains(&value, path);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    return value;
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_getFile
  (JNIEnv *env, jclass clazz, jlong ptr, jlong fsid)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    TAK::Engine::Port::String value;
    code = impl->getFile(value, fsid);
    if(code != TE_Ok)
        return NULL;
    if(!value)
        return NULL;
    return env->NewStringUTF(value);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_add__JLjava_lang_String_2
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(!jpath) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    JNIStringUTF path(*env, jpath);
    code = impl->add(path, NULL);
    if(code == TE_Ok)
        return true;
    else if(code == TE_InvalidArg)
        return false;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_add__JLjava_lang_String_2Ljava_lang_String_2
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath, jstring jhint)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(!jpath) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    TAK::Engine::Port::String path;
    code = JNIStringUTF_get(path, *env, jpath);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return false;
    TAK::Engine::Port::String hint;
    if(jhint) {
        code = JNIStringUTF_get(hint, *env, jhint);
        if(ATAKMapEngineJNI_checkOrThrow(env, code))
            return false;
    }
    code = impl->add(path, hint);
    if(code == TE_Ok)
        return true;
    else if(code == TE_InvalidArg)
        return false;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}
JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_remove
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    if(!jpath) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    JNIStringUTF path(*env, jpath);
    code = impl->remove(path.get());
    if(code == TE_InvalidArg)
        return; // did not contain
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_update
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    if(!jpath) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return false;
    }
    JNIStringUTF path(*env, jpath);
    bool value;
    code = impl->update(path);
    if(code == TE_Ok)
        return true;
    else if(code == TE_InvalidArg)
        return false;
    ATAKMapEngineJNI_checkOrThrow(env, code);
    return false;
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_queryFiles
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    DataSourceFeatureDataStore2::FileCursorPtr result(NULL, NULL);
    code = impl->queryFiles(result);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(result));
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_refresh
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return ;
    }

    code = impl->refresh();
    ATAKMapEngineJNI_checkOrThrow(env, code);
}

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_PersistentDataSourceFeatureDataStore2_queryFeatureSets
  (JNIEnv *env, jclass clazz, jlong ptr, jstring jpath)
{
    TAKErr code(TE_Ok);
    PersistentDataSourceFeatureDataStore2 *impl = JLONG_TO_INTPTR(PersistentDataSourceFeatureDataStore2, ptr);
    if(!impl) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    JNIStringUTF path(*env, jpath);
    FeatureSetCursorPtr result(NULL, NULL);
    code = impl->queryFeatureSets(result, path);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(result));
}
