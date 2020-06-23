#include "JNIFloatArray.h"

#include <cstdlib>

using namespace TAKEngineJNI::Interop;

namespace
{
    inline jfloat *pin(JNIEnv *env, jfloatArray jarr, jboolean *isCopy)
    {
        return env->GetFloatArrayElements(jarr, isCopy);
    }

    inline void unpin(JNIEnv *env, jfloatArray jarr, jfloat *carr, int releaseMode)
    {
        env->ReleaseFloatArrayElements(jarr, carr, releaseMode);
    }
}

JNIFloatArray::JNIFloatArray(JNIEnv &env_, jfloatArray jarr_, int releaseMode_) NOTHROWS :
    JNIPrimitiveArray(env_, jarr_, releaseMode_, pin, unpin)
{}

jfloatArray TAKEngineJNI::Interop::JNIFloatArray_newFloatArray(JNIEnv *env, const jfloat *data, const std::size_t len) NOTHROWS
{
    jfloatArray retval = env->NewFloatArray(len);
    JNIFloatArray jarr(*env, retval, 0);
    jfloat *carr = jarr;
    memcpy(carr, data, len*sizeof(jfloat));
    return retval;
}
