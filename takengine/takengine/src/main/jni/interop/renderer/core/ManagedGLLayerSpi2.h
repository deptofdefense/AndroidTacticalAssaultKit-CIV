#ifndef TAKENGINEJNI_INTEROP_RENDERER_CORE_MANAGEDGLLAYERSPI2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_CORE_MANAGEDGLLAYERSPI2_H_INCLUDED

#include <jni.h>

#include <renderer/core/GLLayerSpi2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Renderer {
            namespace Core {
                class ManagedGLLayerSpi2 : public TAK::Engine::Renderer::Core::GLLayerSpi2
                {
                public :
                    ManagedGLLayerSpi2(JNIEnv &env, jobject impl) NOTHROWS;
                    ~ManagedGLLayerSpi2() NOTHROWS;
                public:
                    virtual TAK::Engine::Util::TAKErr create(TAK::Engine::Renderer::Core::GLLayer2Ptr &value, TAK::Engine::Renderer::Core::GLGlobeBase& renderer, TAK::Engine::Core::Layer2 &subject) NOTHROWS;
                public :
                    jobject impl;
                };
            }
        }
    }
}

#endif
