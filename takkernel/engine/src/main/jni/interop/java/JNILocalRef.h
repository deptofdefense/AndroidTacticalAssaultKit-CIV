#ifndef TAKENGINEJNI_INTEROP_JAVA_JNILOCALREF_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JAVA_JNILOCALREF_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            class JNILocalRef
            {
            public :
                JNILocalRef(JNIEnv &env, jobject obj) NOTHROWS;
                JNILocalRef(JNILocalRef &&other) NOTHROWS;
            private :
                JNILocalRef(const JNILocalRef &) NOTHROWS;
            public :
                ~JNILocalRef() NOTHROWS;
            public :
                jobject get() NOTHROWS;
                jobject release() NOTHROWS;
            public :
                operator bool () const NOTHROWS;
                operator jobject () const NOTHROWS;
                operator jstring () const NOTHROWS;
                operator jclass () const NOTHROWS;
                JNILocalRef &operator=(JNILocalRef &&other) NOTHROWS;
            private :
                static void* operator new (std::size_t);
                static void* operator new[] (std::size_t);
                static void operator delete (void*);
                static void operator delete[] (void*);
            private :
                JNIEnv &env;
                jobject obj;
            };
        }
    }
}
#endif // TAKENGINEJNI_INTEROP_JAVA_JNILOCALREF_H_INCLUDED
