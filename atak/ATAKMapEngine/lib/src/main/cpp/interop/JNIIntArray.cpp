#include "JNIIntArray.h"

#include <cstring>

using namespace TAKEngineJNI::Interop;

namespace
{
    inline jint *pin(JNIEnv *env, jintArray jarr, jboolean *isCopy)
    {
        return env->GetIntArrayElements(jarr, isCopy);
    }

    inline void unpin(JNIEnv *env, jintArray jarr, jint *carr, jint releaseMode)
    {
        env->ReleaseIntArrayElements(jarr, carr, releaseMode);
    }
}

JNIIntArray::JNIIntArray(JNIEnv &env_, jintArray jarr_, int releaseMode_) NOTHROWS :
    JNIPrimitiveArray(env_, jarr_, releaseMode_, pin, unpin)
{}

jintArray TAKEngineJNI::Interop::JNIIntArray_newIntArray(JNIEnv *env, const jint *data, const std::size_t len) NOTHROWS
{
    jintArray retval = env->NewIntArray(len);
    JNIIntArray jarr(*env, retval, 0);
    jint *carr = jarr;
    memcpy(carr, data, len*sizeof(jint));
    return retval;
}
