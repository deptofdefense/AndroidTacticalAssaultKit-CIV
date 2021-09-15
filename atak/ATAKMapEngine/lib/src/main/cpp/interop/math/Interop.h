#ifndef TAKENGINEJNI_INTEROP_MATH_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_MATH_INTEROP_H_INCLUDED

#include <jni.h>

#include <math/Matrix.h>
#include <math/Rectangle2.h>
#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Math {
            // Matrix interop
            TAK::Engine::Util::TAKErr Interop_copy(TAK::Engine::Math::Matrix2 *value, JNIEnv *env, jobject mmatrix) NOTHROWS;

            TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Math::Matrix2 &cmatrix) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Math::Matrix2 &cmatrix) NOTHROWS;

            // Rectangle interop
            TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Math::Rectangle2<double> *value, JNIEnv &env, jobject mpoint) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Math::Rectangle2<double> &crect) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Math::Rectangle2<double> &crect) NOTHROWS;
        }
    }
}
#endif
