#ifndef TAKENGINEJNI_INTEROP_JAVA_JNIPRIMITIVE_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JAVA_JNIPRIMITIVE_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            JNILocalRef Boolean_valueOf(JNIEnv &env, const bool value) NOTHROWS;
            JNILocalRef Integer_valueOf(JNIEnv &env, const int value) NOTHROWS;
        }
    }
}
#endif
