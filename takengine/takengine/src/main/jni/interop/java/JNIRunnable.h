#ifndef TAKENGINEJNI_INTEROP_JAVA_JNIRUNNABLE_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JAVA_JNIRUNNABLE_H_INCLUDED

#include <memory>

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, void(*run)(void *), std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS;
        }
    }
}

#endif
