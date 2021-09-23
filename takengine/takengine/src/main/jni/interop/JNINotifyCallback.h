#ifndef TAKENGINEJNI_INTEROP_JNINOTIFYCALLBACK_H_INCLUDED
#define TAKENGINEJNI_INTEROP_JNINOTIFYCALLBACK_H_INCLUDED

#include <port/Platform.h>
#include <util/Error.h>

#include <jni.h>

namespace TAKEngineJNI {
    namespace Interop {
        TAK::Engine::Util::TAKErr JNINotifyCallback_eventOccurred(jobject callback) NOTHROWS;
    }
}
#endif

