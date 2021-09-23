#ifndef TAKENGINEJNI_INTEROP_RENDERER_MANAGEDRENDERCONTEXT_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_MANAGEDRENDERCONTEXT_H_INCLUDED

#include <jni.h>

#include <core/RenderContext.h>
#include <port/Platform.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
        class ManagedRenderContext : public TAK::Engine::Core::RenderContext {
            public :
                ManagedRenderContext(JNIEnv &env, jobject impl) NOTHROWS;
            private :
                ManagedRenderContext(JNIEnv &env, jobject impl, jobject parent) NOTHROWS;
            public :
                ~ManagedRenderContext() NOTHROWS;
            public :
                virtual bool isRenderThread() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr queueEvent(void(*runnable)(void *) NOTHROWS, std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS;
                virtual void requestRefresh() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr setFrameRate(const float rate) NOTHROWS;
                virtual float getFrameRate() const NOTHROWS;
                virtual void setContinuousRenderEnabled(const bool enabled) NOTHROWS;
                virtual bool isContinuousRenderEnabled() NOTHROWS;
                virtual bool supportsChildContext() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr createChildContext(TAK::Engine::Core::RenderContextPtr &value) NOTHROWS;
                virtual bool isAttached() const NOTHROWS;
                virtual bool attach() NOTHROWS;
                virtual bool detach() NOTHROWS;
                virtual bool isMainContext() const NOTHROWS;
                virtual TAK::Engine::Core::RenderSurface *getRenderSurface() const NOTHROWS;
            public :
                jobject impl;
                jobject parent;
            };
        }
    }
}

#endif
