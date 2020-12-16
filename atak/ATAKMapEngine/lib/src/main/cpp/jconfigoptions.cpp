#include "jconfigoptions.h"

#include <util/ConfigOptions.h>

#include "common.h"
#include "interop/JNIStringUTF.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_util_ConfigOptions_setOption
  (JNIEnv *env, jclass clazz, jstring jkey, jstring jvalue)
{
    TAKErr code = TE_Ok;
    TAK::Engine::Port::String key;
    if(ATAKMapEngineJNI_checkOrThrow(env, JNIStringUTF_get(key, *env, jkey)))
        return;
    if(jvalue != NULL){
        TAK::Engine::Port::String value;
        if(ATAKMapEngineJNI_checkOrThrow(env, JNIStringUTF_get(value, *env, jvalue)))
            return;

        code = ConfigOptions_setOption(key, value);
    }
    else{
        TAK::Engine::Port::String value;
        auto checkCode = ConfigOptions_getOption(value, key);
	if(checkCode == TE_Ok && value != NULL){
            code = ConfigOptions_setOption(key, NULL);
	}
    }
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
