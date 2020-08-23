#ifndef TAKENGINEJNI_INTEROP_JNIFLOATARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNIFLOATARRAY_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "JNIPrimitiveArray.h"

namespace TAKEngineJNI {
    namespace Interop {
        class JNIFloatArray : public JNIPrimitiveArray<jfloatArray, jfloat>
        {
        public :
            JNIFloatArray(JNIEnv &env, jfloatArray jarr, int releaseMode) NOTHROWS;
        };

        jfloatArray JNIFloatArray_newFloatArray(JNIEnv *env, const jfloat *data, const std::size_t len) NOTHROWS;
        TAK::Engine::Util::TAKErr JNIFloatArray_copy(jfloat *dst, JNIEnv &env, jfloatArray src, const std::size_t off, const std::size_t len) NOTHROWS;
        TAK::Engine::Util::TAKErr JNIFloatArray_copy(jfloatArray dst, const std::size_t off, JNIEnv &env, const jfloat *src, const std::size_t len) NOTHROWS;
    }
}
#endif
