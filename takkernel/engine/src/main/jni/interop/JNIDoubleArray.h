#ifndef TAKENGINEJNI_INTEROP_JNIDOUBLEARRAY_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNIDOUBLEARRAY_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>

#include "JNIPrimitiveArray.h"

namespace TAKEngineJNI {
    namespace Interop {
        class JNIDoubleArray : public JNIPrimitiveArray<jdoubleArray, jdouble>
        {
        public :
            JNIDoubleArray(JNIEnv &env, jdoubleArray jarr, int releaseMode) NOTHROWS;
        };

        jdoubleArray JNIDoubleArray_newDoubleArray(JNIEnv *env, const jdouble *data, const std::size_t len) NOTHROWS;
    }
}
#endif
