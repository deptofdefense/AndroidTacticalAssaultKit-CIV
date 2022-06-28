#ifndef TAKENGINEJNI_INTEROP_JAVA_JNIITERATOR_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JAVA_JNIITERATOR_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            TAK::Engine::Util::TAKErr JNIIterator_hasNext(bool &value, JNIEnv &env, jobject iterator) NOTHROWS;
            TAK::Engine::Util::TAKErr JNIIterator_next(JNILocalRef &value, JNIEnv &env, jobject iterator) NOTHROWS;
            TAK::Engine::Util::TAKErr JNIIterator_remove(JNIEnv &env, jobject iterator) NOTHROWS;
        }
    }
}

#endif
