#ifndef TAKENGINEJNI_INTEROP_JNIINTARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNIINTARRAY_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>

#include "JNIPrimitiveArray.h"

namespace TAKEngineJNI {
    namespace Interop {
        class JNIIntArray : public JNIPrimitiveArray<jintArray, jint>
        {
        public :
            JNIIntArray(JNIEnv &env, jintArray jarr, int releaseMode) NOTHROWS;
        };

        jintArray JNIIntArray_newIntArray(JNIEnv *env, const jint *data, const std::size_t len) NOTHROWS;
    }
}
#endif
