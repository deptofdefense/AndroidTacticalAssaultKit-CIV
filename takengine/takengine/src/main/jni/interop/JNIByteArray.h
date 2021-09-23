#ifndef TAKENGINEJNI_INTEROP_JNIBYTEARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNIBYTEARRAY_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>

#include "JNIPrimitiveArray.h"

namespace TAKEngineJNI {
    namespace Interop {
        class JNIByteArray : public JNIPrimitiveArray<jbyteArray, jbyte>
        {
        public :
            JNIByteArray(JNIEnv &env, jbyteArray jarr, int releaseMode) NOTHROWS;
        };

        jbyteArray JNIByteArray_newByteArray(JNIEnv *env, const jbyte *data, const std::size_t len) NOTHROWS;
    }
}
#endif
