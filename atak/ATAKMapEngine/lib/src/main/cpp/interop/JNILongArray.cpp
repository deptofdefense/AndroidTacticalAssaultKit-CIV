#include "JNILongArray.h"

#include <cstdlib>

using namespace TAKEngineJNI::Interop;

namespace
{
    inline jlong *pin(JNIEnv *env, jlongArray jarr, jboolean *isCopy)
    {
        return env->GetLongArrayElements(jarr, isCopy);
    }

    inline void unpin(JNIEnv *env, jlongArray jarr, jlong *carr, int releaseMode)
    {
        env->ReleaseLongArrayElements(jarr, carr, releaseMode);
    }
}

JNILongArray::JNILongArray(JNIEnv &env_, jlongArray jarr_, int releaseMode_) NOTHROWS :
    JNIPrimitiveArray(env_, jarr_, releaseMode_, pin, unpin)
{}

jlongArray TAKEngineJNI::Interop::JNILongArray_newLongArray(JNIEnv *env, const jlong *data, const std::size_t len) NOTHROWS
{
    jlongArray retval = env->NewLongArray(len);
    JNILongArray jarr(*env, retval, 0);
    jlong *carr = jarr;
    memcpy(carr, data, len*sizeof(jlong));
    return retval;
}
