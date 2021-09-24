#ifndef TAKENGINEJNI_INTEROP_JNISTRING_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNISTRING_H_INCLUDED

#include <jni.h>

#include <port/Platform.h>
#include <port/String.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        class JNIStringUTF
        {
        public :
            JNIStringUTF(JNIEnv &env, jstring string) NOTHROWS;
            ~JNIStringUTF() NOTHROWS;
        public :
            const char *get() const NOTHROWS;
        public :
            operator const char*() const NOTHROWS;
        private :
            jstring string;
            const char *cstring;
        };

        TAK::Engine::Util::TAKErr JNIStringUTF_get(TAK::Engine::Port::String &value, JNIEnv &env, jstring string) NOTHROWS;
    }
}

#endif
