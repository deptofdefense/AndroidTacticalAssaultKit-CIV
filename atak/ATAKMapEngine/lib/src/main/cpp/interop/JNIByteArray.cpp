#include "JNIByteArray.h"

#include <cstring>

using namespace TAKEngineJNI::Interop;

namespace
{
    inline jbyte *pin(JNIEnv *env, jbyteArray jarr, jboolean *isCopy)
    {
        return env->GetByteArrayElements(jarr, isCopy);
    }

    inline void unpin(JNIEnv *env, jbyteArray jarr, jbyte *carr, jint releaseMode)
    {
        env->ReleaseByteArrayElements(jarr, carr, releaseMode);
    }
}

JNIByteArray::JNIByteArray(JNIEnv &env_, jbyteArray jarr_, int releaseMode_) NOTHROWS :
    JNIPrimitiveArray(env_, jarr_, releaseMode_, pin, unpin)
{}

jbyteArray TAKEngineJNI::Interop::JNIByteArray_newByteArray(JNIEnv *env, const jbyte *data, const std::size_t len) NOTHROWS
{
    jbyteArray retval = env->NewByteArray(len);
    JNIByteArray jarr(*env, retval, 0);
    jbyte *carr = jarr;
    memcpy(carr, data, len);
    return retval;
}
