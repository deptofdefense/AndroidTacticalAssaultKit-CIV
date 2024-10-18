#ifndef TAKENGINEJNI_INTEROP_RENDERER_MANAGEDRENDERSURFACE_H_INCLUDED
#define TAKENGINEJNI_INTEROP_RENDERER_MANAGEDRENDERSURFACE_H_INCLUDED

#include <map>

#include <jni.h>

#include <core/RenderSurface.h>
#include <port/Platform.h>
#include <thread/Mutex.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
            class ManagedRenderSurface : public TAK::Engine::Core::RenderSurface {
                public :
                    ManagedRenderSurface(JNIEnv &env, jobject impl) NOTHROWS;
                public :
                    ~ManagedRenderSurface() NOTHROWS;
                public :
                    double getDpi() const NOTHROWS override;
                    std::size_t getWidth() const NOTHROWS override;
                    std::size_t getHeight() const NOTHROWS override;
                    void addOnSizeChangedListener(TAK::Engine::Core::RenderSurface::OnSizeChangedListener *l) NOTHROWS override;
                    void removeOnSizeChangedListener(const TAK::Engine::Core::RenderSurface::OnSizeChangedListener &l) NOTHROWS override;
                public :
                    jobject impl;
                    TAK::Engine::Thread::Mutex mutex;
                    std::map<const TAK::Engine::Core::RenderSurface::OnSizeChangedListener *, jobject> listeners;
            };
        }
    }
}

#endif
