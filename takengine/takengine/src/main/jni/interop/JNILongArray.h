#ifndef TAKENGINEJNI_INTEROP_JNILONGARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNILONGARRAY_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>

#include "JNIPrimitiveArray.h"

namespace TAKEngineJNI {
    namespace Interop {
        class JNILongArray : public JNIPrimitiveArray<jlongArray, jlong>
        {
        public :
            JNILongArray(JNIEnv &env, jlongArray jarr, int releaseMode) NOTHROWS;
        };

        jlongArray JNILongArray_newLongArray(JNIEnv *env, const jlong *data, const std::size_t len) NOTHROWS;
    }
}
#endif
