#include "JNIDoubleArray.h"

#include <cstring>

using namespace TAKEngineJNI::Interop;

namespace
{
    inline jdouble *pin(JNIEnv *env, jdoubleArray jarr, jboolean *isCopy)
    {
        return env->GetDoubleArrayElements(jarr, isCopy);
    }

    inline void unpin(JNIEnv *env, jdoubleArray jarr, jdouble *carr, jint releaseMode)
    {
        env->ReleaseDoubleArrayElements(jarr, carr, releaseMode);
    }
}

JNIDoubleArray::JNIDoubleArray(JNIEnv &env_, jdoubleArray jarr_, int releaseMode_) NOTHROWS :
    JNIPrimitiveArray(env_, jarr_, releaseMode_, pin, unpin)
{}

jdoubleArray TAKEngineJNI::Interop::JNIDoubleArray_newDoubleArray(JNIEnv *env, const jdouble *data, const std::size_t len) NOTHROWS
{
    jdoubleArray retval = env->NewDoubleArray(len);
    JNIDoubleArray jarr(*env, retval, 0);
    jdouble *carr = jarr;
    memcpy(carr, data, len*sizeof(jdouble));
    return retval;
}
