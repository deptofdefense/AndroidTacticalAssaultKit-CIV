#include "JNIStringUTF.h"

#include "../common.h"

using namespace TAKEngineJNI::Interop;

using namespace TAK::Engine::Util;

JNIStringUTF::JNIStringUTF(JNIEnv &env, jstring string_) NOTHROWS :
    string(string_),
    cstring(string_ ? env.GetStringUTFChars(string_, NULL) : NULL)
{}

JNIStringUTF::~JNIStringUTF() NOTHROWS
{
    if(cstring) {
        LocalJNIEnv env;
        if(env.valid())
            env->ReleaseStringUTFChars(string, cstring);
        cstring = NULL;
    }
}
const char *JNIStringUTF::get() const NOTHROWS
{
    return cstring;
}
JNIStringUTF::operator const char*() const NOTHROWS
{
    return cstring;
}


TAKErr TAKEngineJNI::Interop::JNIStringUTF_get(TAK::Engine::Port::String &value, JNIEnv &env, jstring string) NOTHROWS
{
    if(string) {
        JNIStringUTF str(env, string);
        value = str;
    } else {
        value = NULL;
    }
    return TE_Ok;
}