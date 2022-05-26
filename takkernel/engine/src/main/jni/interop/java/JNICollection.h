#ifndef TAKENGINEJNI_INTEROP_JAVA_JNICOLLECTION_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JAVA_JNICOLLECTION_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            enum JNICollectionClass
            {
                ArrayList,
                LinkedList,
                HashSet,
            };

            JNILocalRef JNICollection_create(JNIEnv &env, const JNICollectionClass type);
            TAK::Engine::Util::TAKErr JNICollection_add(JNIEnv &env, jobject collection, jobject element) NOTHROWS;
            TAK::Engine::Util::TAKErr JNICollection_remove(JNIEnv &env, jobject collection, jobject element) NOTHROWS;
            TAK::Engine::Util::TAKErr JNICollection_clear(JNIEnv &env, jobject collection) NOTHROWS;
        }
    }
}

#endif
