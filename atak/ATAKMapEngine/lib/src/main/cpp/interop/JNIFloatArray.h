#ifndef TAKENGINEJNI_INTEROP_JNIFLOATARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNIFLOATARRAY_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>

#include "JNIPrimitiveArray.h"

namespace TAKEngineJNI {
    namespace Interop {
        class JNIFloatArray : public JNIPrimitiveArray<jfloatArray, jfloat>
        {
        public :
            JNIFloatArray(JNIEnv &env, jfloatArray jarr, int releaseMode) NOTHROWS;
        };

        jfloatArray JNIFloatArray_newFloatArray(JNIEnv *env, const jfloat *data, const std::size_t len) NOTHROWS;
    }
}
#endif
