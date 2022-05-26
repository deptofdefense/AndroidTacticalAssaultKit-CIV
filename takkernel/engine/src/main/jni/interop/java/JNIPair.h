#ifndef TAKENGINEJNI_INTEROP_JAVA_JNIPAIR_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JAVA_JNIPAIR_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            TAK::Engine::Util::TAKErr JNIPair_create(Java::JNILocalRef &value, JNIEnv &env, jobject first, jobject second) NOTHROWS;
        }
    }
}

#endif
