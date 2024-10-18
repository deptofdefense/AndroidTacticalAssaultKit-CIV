#include "interop/core/ManagedRenderSurface.h"

#include <thread/Lock.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/java/JNILocalRef.h"

using namespace TAKEngineJNI::Interop::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Thread;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct {
        jclass id;
        jmethodID getDpi;
        jmethodID getWidth;
        jmethodID getHeight;
        jmethodID addOnSizeChangedListener;
        jmethodID removeOnSizeChangedListener;
    } RenderSurface_class;

    struct {
        jclass id;
        jmethodID init;
    } NativeRenderSurfaceSizeChangedListener_class;

    bool RenderSurface_init(JNIEnv &env) NOTHROWS;
}

ManagedRenderSurface::ManagedRenderSurface(JNIEnv &env_, jobject impl_) NOTHROWS :
    impl(env_.NewWeakGlobalRef(impl_))
{
    static bool clinit = RenderSurface_init(env_);
}
ManagedRenderSurface::~ManagedRenderSurface() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        // clear all native listeners
        for(auto entry = listeners.begin(); entry != listeners.end(); entry++) {
            env->CallVoidMethod(impl, RenderSurface_class.removeOnSizeChangedListener, entry->second);
            env->DeleteWeakGlobalRef(entry->second);
        }
        listeners.clear();
        env->DeleteWeakGlobalRef(impl);
        impl = NULL;
    }
}

double ManagedRenderSurface::getDpi() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallDoubleMethod(impl, RenderSurface_class.getDpi);
}
std::size_t ManagedRenderSurface::getWidth() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(impl, RenderSurface_class.getWidth);
}
std::size_t ManagedRenderSurface::getHeight() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(impl, RenderSurface_class.getHeight);
}
void ManagedRenderSurface::addOnSizeChangedListener(RenderSurface::OnSizeChangedListener *clistener) NOTHROWS
{
    LocalJNIEnv env;
    Lock lock(mutex);
    if(listeners.find(clistener) != listeners.end())
        return;
    Java::JNILocalRef mlistener(*env, env->NewObject(NativeRenderSurfaceSizeChangedListener_class.id, NativeRenderSurfaceSizeChangedListener_class.init, INTPTR_TO_JLONG(static_cast<RenderSurface *>(this)), NewPointer(env, clistener, true)));
    env->CallVoidMethod(impl, RenderSurface_class.addOnSizeChangedListener, mlistener.get());
    listeners[clistener] = env->NewWeakGlobalRef(mlistener);
}
void ManagedRenderSurface::removeOnSizeChangedListener(const RenderSurface::OnSizeChangedListener &clistener) NOTHROWS
{
    LocalJNIEnv env;
    Lock lock(mutex);
    auto entry = listeners.find(&clistener);
    if(entry == listeners.end())
        return;
    env->CallVoidMethod(impl, RenderSurface_class.removeOnSizeChangedListener, entry->second);
    env->DeleteWeakGlobalRef(entry->second);
    listeners.erase(entry);
}

namespace
{
    bool RenderSurface_init(JNIEnv &env) NOTHROWS {
        RenderSurface_class.id = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/RenderSurface");
        RenderSurface_class.getDpi = env.GetMethodID(RenderSurface_class.id, "getDpi", "()D");
        RenderSurface_class.getWidth = env.GetMethodID(RenderSurface_class.id, "getWidth", "()I");
        RenderSurface_class.getHeight = env.GetMethodID(RenderSurface_class.id, "getHeight", "()I");
        RenderSurface_class.addOnSizeChangedListener = env.GetMethodID(RenderSurface_class.id, "addOnSizeChangedListener", "(Lgov/tak/api/engine/map/RenderSurface$OnSizeChangedListener;)V");
        RenderSurface_class.removeOnSizeChangedListener = env.GetMethodID(RenderSurface_class.id, "removeOnSizeChangedListener", "(Lgov/tak/api/engine/map/RenderSurface$OnSizeChangedListener;)V");

        NativeRenderSurfaceSizeChangedListener_class.id = ATAKMapEngineJNI_findClass(&env, "gov/tak/platform/engine/map/NativeRenderSurfaceSizeChangedListener");
        NativeRenderSurfaceSizeChangedListener_class.init = env.GetMethodID(NativeRenderSurfaceSizeChangedListener_class.id, "<init>", "(JLcom/atakmap/interop/Pointer;)V");

        return true;
    }    
}
