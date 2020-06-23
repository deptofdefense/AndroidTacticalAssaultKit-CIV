#ifndef TAKENGINEJNI_INTEROP_MATH_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_MATH_INTEROP_H_INCLUDED

#include <jni.h>

#include <math/Matrix.h>
#include <port/Platform.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Math {
            TAK::Engine::Util::TAKErr Interop_copy(TAK::Engine::Math::Matrix2 *value, JNIEnv *env, jobject mmatrix) NOTHROWS;
        }
    }
}
#endif
