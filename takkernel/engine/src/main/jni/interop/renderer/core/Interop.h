#ifndef TAKENGINEJNI_INTEROP_RENDERER_CORE_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_CORE_INTEROP_H_INCLUDED

#include <memory>

#include <jni.h>

#include <renderer/core/GLGlobeBase.h>
#include <renderer/core/GLLayer2.h>
#include <renderer/core/GLLayerSpi2.h>

#include "interop/java/JNILocalRef.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Renderer {
            namespace Core {
                template<class T>
                TAK::Engine::Util::TAKErr Interop_isWrapper(bool *value, JNIEnv &, jobject mlayer) NOTHROWS;

                // GLMapView interop
                TAK::Engine::Util::TAKErr Interop_marshal(std::shared_ptr<TAK::Engine::Renderer::Core::GLGlobeBase> &value, JNIEnv &env, jobject mview) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Renderer::Core::GLGlobeBase &cview) NOTHROWS;

                // GLMapView2::RenderPass
                TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Renderer::Core::GLMapView2::RenderPass *value, const jint mpass) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(jint *value, const TAK::Engine::Renderer::Core::GLMapView2::RenderPass cpass) NOTHROWS;

                // GLMapRenderable2 interop
                TAK::Engine::Util::TAKErr Interop_marshal(std::shared_ptr<TAK::Engine::Renderer::Core::GLMapRenderable2> &value, JNIEnv &env, jobject mgllayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Renderer::Core::GLMapRenderable2Ptr &value, JNIEnv &env, jobject mlayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<TAK::Engine::Renderer::Core::GLMapRenderable2> &clayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, TAK::Engine::Renderer::Core::GLMapRenderable2Ptr &&clayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Renderer::Core::GLMapRenderable2 &clayer) NOTHROWS;

                template<>
                TAK::Engine::Util::TAKErr Interop_isWrapper<TAK::Engine::Renderer::Core::GLMapRenderable2>(bool *value, JNIEnv &, jobject mlayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_isWrapper(bool *value, JNIEnv &, const TAK::Engine::Renderer::Core::GLMapRenderable2 &clayer) NOTHROWS;

                // GLLayer2 interop
                TAK::Engine::Util::TAKErr Interop_marshal(std::shared_ptr<TAK::Engine::Renderer::Core::GLLayer2> &value, JNIEnv &env, jobject mgllayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Renderer::Core::GLLayer2Ptr &value, JNIEnv &env, jobject mlayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<TAK::Engine::Renderer::Core::GLLayer2> &clayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, TAK::Engine::Renderer::Core::GLLayer2Ptr &&clayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Renderer::Core::GLLayer2 &clayer) NOTHROWS;

                template<>
                TAK::Engine::Util::TAKErr Interop_isWrapper<TAK::Engine::Renderer::Core::GLLayer2>(bool *value, JNIEnv &, jobject mlayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_isWrapper(bool *value, JNIEnv &, const TAK::Engine::Renderer::Core::GLLayer2 &clayer) NOTHROWS;

                // GLLayerSpi2 interop
                TAK::Engine::Util::TAKErr Interop_marshal(std::shared_ptr<TAK::Engine::Renderer::Core::GLLayerSpi2> &value, JNIEnv &env, jobject mgllayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(TAK::Engine::Renderer::Core::GLLayerSpi2Ptr &value, JNIEnv &env, jobject mlayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const std::shared_ptr<TAK::Engine::Renderer::Core::GLLayerSpi2> &clayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, TAK::Engine::Renderer::Core::GLLayerSpi2Ptr &&clayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Renderer::Core::GLLayerSpi2 &clayer) NOTHROWS;

                template<>
                TAK::Engine::Util::TAKErr Interop_isWrapper<TAK::Engine::Renderer::Core::GLLayerSpi2>(bool *value, JNIEnv &, jobject mlayer) NOTHROWS;
                TAK::Engine::Util::TAKErr Interop_isWrapper(bool *value, JNIEnv &, const TAK::Engine::Renderer::Core::GLLayerSpi2 &clayer) NOTHROWS;
            }
        }
    }
}
#endif
