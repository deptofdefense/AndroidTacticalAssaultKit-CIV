#ifndef TAKENGINEJNI_INTEROP_MODEL_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_MODEL_INTEROP_H_INCLUDED

#include <jni.h>

#include <model/Mesh.h>
#include <port/Platform.h>
#include <util/Error.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Model {
            TAK::Engine::Util::TAKErr Interop_access(std::shared_ptr<TAK::Engine::Model::Mesh> &value, JNIEnv *env, jobject jmesh);

            template<class T>
            bool Interop_isWrapped(JNIEnv *env, jobject obj) NOTHROWS;

            // template specializations
            template<>
            bool Interop_isWrapped<TAK::Engine::Model::Mesh>(JNIEnv *env, jobject obj) NOTHROWS;
        }
    }
}

#endif
