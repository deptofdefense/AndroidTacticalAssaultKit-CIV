#include "JNIFloatArray.h"

#include <cstring>

using namespace TAKEngineJNI::Interop;

using namespace TAK::Engine::Util;

namespace
{
    inline jfloat *pin(JNIEnv *env, jfloatArray jarr, jboolean *isCopy)
    {
        return env->GetFloatArrayElements(jarr, isCopy);
    }

    inline void unpin(JNIEnv *env, jfloatArray jarr, jfloat *carr, jint releaseMode)
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
TAKErr TAKEngineJNI::Interop::JNIFloatArray_copy(jfloat *dst, JNIEnv &env, jfloatArray src, const std::size_t off, const std::size_t len) NOTHROWS
{
    if(!src)
        return TE_InvalidArg;
    if(!dst)
        return TE_InvalidArg;
    JNIFloatArray marr(env, src, JNI_ABORT);
    if(off+len > marr.length())
        return TE_InvalidArg;
    memcpy(dst, marr.get<jfloat>()+off, sizeof(jfloat) * len);
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::JNIFloatArray_copy(jfloatArray dst, const std::size_t off, JNIEnv &env, const jfloat *src, const std::size_t len) NOTHROWS
{
    if(!src)
        return TE_InvalidArg;
    if(!dst)
        return TE_InvalidArg;
    JNIFloatArray marr(env, dst, 0);
    if(len > marr.length())
        return TE_InvalidArg;
    memcpy(marr.get<jfloat>()+off, src, sizeof(jfloat) * len);
    return TE_Ok;
}
