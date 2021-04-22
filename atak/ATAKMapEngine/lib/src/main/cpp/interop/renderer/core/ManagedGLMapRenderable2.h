#ifndef TAKENGINEJNI_INTEROP_RENDERER_CORE_MANAGEDGLMAPRENDERABLE2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_CORE_MANAGEDGLMAPRENDERABLE2_H_INCLUDED

#include <jni.h>

#include <renderer/core/GLMapRenderable2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Renderer {
            namespace Core {
                class ManagedGLMapRenderable2 : public TAK::Engine::Renderer::Core::GLMapRenderable2
                {
                public :
                    ManagedGLMapRenderable2(JNIEnv &env, jobject impl, const bool requiresSync = true) NOTHROWS;
                    ~ManagedGLMapRenderable2() NOTHROWS;
                public:
                    virtual void draw(const TAK::Engine::Renderer::Core::GLGlobeBase& view, const int renderPass) NOTHROWS;
                    virtual void release() NOTHROWS;
                    virtual int getRenderPass() NOTHROWS;
                    virtual void start() NOTHROWS;
                    virtual void stop() NOTHROWS;
                public :
                    jobject impl;
                private :
                    bool requiresSync;
                };
            }
        }
    }
}

#endif
