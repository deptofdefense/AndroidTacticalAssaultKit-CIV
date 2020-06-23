#include "jconfigoptions.h"

#include <util/ConfigOptions.h>

#include "common.h"
#include "interop/JNIStringUTF.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_util_ConfigOptions_setOption
  (JNIEnv *env, jclass clazz, jstring jkey, jstring jvalue)
{
    TAK::Engine::Port::String key;
    if(ATAKMapEngineJNI_checkOrThrow(env, JNIStringUTF_get(key, *env, jkey)))
        return;
    TAK::Engine::Port::String value;
    if(ATAKMapEngineJNI_checkOrThrow(env, JNIStringUTF_get(value, *env, jvalue)))
        return;

    TAKErr code = ConfigOptions_setOption(key, value);
    ATAKMapEngineJNI_checkOrThrow(env, code);
}
JNIEXPORT jstring JNICALL Java_com_atakmap_util_ConfigOptions_getOption
  (JNIEnv *env, jclass clazz, jstring jkey, jstring jdef)
{
    TAKErr code(TE_Ok);
    JNIStringUTF key(*env, jkey);
    TAK::Engine::Port::String value;
    code = ConfigOptions_getOption(value, key);
    if(code == TE_Ok)
        return value ? env->NewStringUTF(value) : NULL;
    else if(code == TE_InvalidArg)
        return jdef;

    ATAKMapEngineJNI_checkOrThrow(env, code);
    return NULL;
}
