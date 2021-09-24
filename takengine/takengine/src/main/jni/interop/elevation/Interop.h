#ifndef TAKENGINEJNI_INTEROP_ELEVATION_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_ELEVATION_INTEROP_H_INCLUDED

#include <jni.h>

#include <elevation/ElevationSource.h>
#include <port/Platform.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Elevation {
            // general template declaration for Java-wraps-native
            template<class T>
            bool Interop_isWrapper(JNIEnv *env, jobject msource);

            TAK::Engine::Util::TAKErr Interop_adapt(std::shared_ptr<TAK::Engine::Elevation::ElevationSource> &value, JNIEnv *env, jobject msource, const bool forceWrap) NOTHROWS;
            jobject Interop_adapt(JNIEnv *env, const std::shared_ptr<TAK::Engine::Elevation::ElevationSource> &csource, const bool forceWrap) NOTHROWS;

            jobject Interop_getObject(JNIEnv *env, const TAK::Engine::Elevation::ElevationSource &csource) NOTHROWS;

            // template specialization for Java-wraps-native
            template<>
            bool Interop_isWrapper<TAK::Engine::Elevation::ElevationSource>(JNIEnv *env, jobject msource);

            bool Interop_isWrapper(const TAK::Engine::Elevation::ElevationSource &csource);
        }
    }
}

#endif
