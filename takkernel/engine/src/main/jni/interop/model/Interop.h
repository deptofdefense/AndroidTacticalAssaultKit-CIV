#ifndef TAKENGINEJNI_INTEROP_MODEL_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_MODEL_INTEROP_H_INCLUDED

#include <jni.h>

#include <model/Mesh.h>
#include <model/SceneInfo.h>
#include <port/Platform.h>
#include <util/Error.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Model {
            TAK::Engine::Util::TAKErr Interop_access(std::shared_ptr<TAK::Engine::Model::Mesh> &value, JNIEnv *env, jobject jmesh);

            template<class T>
            bool Interop_isWrapped(JNIEnv *env, jobject obj) NOTHROWS;

            // template specializations
            template<>
            bool Interop_isWrapped<TAK::Engine::Model::Mesh>(JNIEnv *env, jobject obj) NOTHROWS;

            // ModelInfo/SceneInfo interop
            TAK::Engine::Util::TAKErr Interop_marshal(jobject &modelInfo, JNIEnv *env, const TAK::Engine::Model::SceneInfo &sceneinfo) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Model::SceneInfo &sceneinfo, JNIEnv *env, jobject modelinfo) NOTHROWS;

            // ResourceAliasCollection interop
            TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &resourceMap, JNIEnv *env, const TAK::Engine::Model::ResourceAliasCollectionPtr &resourceAliases) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Model::ResourceAliasCollectionPtr &resourceAliases, JNIEnv *env, jobject resourceMap) NOTHROWS;
        }
    }
}

#endif
