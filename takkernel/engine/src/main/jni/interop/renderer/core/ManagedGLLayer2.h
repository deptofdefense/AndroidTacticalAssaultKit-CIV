#ifndef TAKENGINEJNI_INTEROP_RENDERER_CORE_MANAGEDGLLAYER2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_CORE_MANAGEDGLLAYER2_H_INCLUDED

#include <jni.h>

#include <renderer/core/GLLayer2.h>

#include "interop/renderer/core/ManagedGLMapRenderable2.h"

namespace TAKEngineJNI {
    namespace Interop {
        namespace Renderer {
            namespace Core {
                class ManagedGLLayer2 : public TAK::Engine::Renderer::Core::GLLayer2
                {
                public :
                    ManagedGLLayer2(JNIEnv &env, jobject impl) NOTHROWS;
                    ~ManagedGLLayer2() NOTHROWS;
                public:
                    virtual void draw(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const int renderPass) NOTHROWS;
                    virtual void release() NOTHROWS;
                    virtual int getRenderPass() NOTHROWS;
                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;
                    virtual TAK::Engine::Core::Layer2 &getSubject() NOTHROWS;
                private :
                    ManagedGLMapRenderable2 renderable;
                    bool gllayer3;
                public :
                    jobject impl;
                private :
                    std::shared_ptr<TAK::Engine::Core::Layer2> csubject;
                };
            }
        }
    }
}

#endif
