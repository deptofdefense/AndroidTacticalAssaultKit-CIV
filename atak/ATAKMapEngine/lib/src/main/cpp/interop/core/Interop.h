#ifndef TAKENGINEJNI_INTEROP_CORE_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_CORE_INTEROP_H_INCLUDED

#include <memory>

#include <jni.h>
#include <core/GeoPoint2.h>
#include <core/Projection2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
            TAK::Engine::Util::TAKErr Interop_copy(jobject value, JNIEnv *env, const TAK::Engine::Core::GeoPoint2 &p) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_copy(TAK::Engine::Core::GeoPoint2 *value, JNIEnv *env, jobject p) NOTHROWS;
            jobject Interop_create(JNIEnv *env, const TAK::Engine::Core::GeoPoint2 &p) NOTHROWS;

            jobject Interop_wrap(JNIEnv *env, TAK::Engine::Core::Projection2Ptr &&cproj) NOTHROWS;
            jobject Interop_wrap(JNIEnv *env, const std::shared_ptr<TAK::Engine::Core::Projection2> &cproj) NOTHROWS;
        }
    }
}
#endif
