#include <jni.h>

#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Java {
            TAK::Engine::Util::TAKErr JNIEnum_value(JNILocalRef &value, JNIEnv &env, const char *enumClass, const char *enumName) NOTHROWS;
        }
    }
}