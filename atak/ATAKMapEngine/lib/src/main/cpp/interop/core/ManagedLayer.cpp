#include "interop/core/ManagedLayer.h"

#include <thread/Lock.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/core/Interop.h"
#include "interop/java/JNILocalRef.h"

using namespace TAKEngineJNI::Interop::Core;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jmethodID setVisible;
        jmethodID isVisible;
        jmethodID addOnLayerVisibleChangedListener;
        jmethodID removeOnLayerVisibleChangedListener;
        jmethodID getName;
    } Layer_class;

    bool Layer_class_init(JNIEnv &env) NOTHROWS;
}

ManagedLayer::ManagedLayer(JNIEnv &env_, jobject impl_) NOTHROWS :
    impl(env_.NewGlobalRef(impl_)),
    mutex(TEMT_Recursive)
{
    static bool clinit = Layer_class_init(env_);
    Java::JNILocalRef mname(env_, env_.CallObjectMethod(impl, Layer_class.getName));
    JNIStringUTF_get(name, env_, (jstring)mname);

}
ManagedLayer::~ManagedLayer () NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        LockPtr lock(NULL, NULL);
        Lock_create(lock, mutex);
        for(auto it = visibilityListeners.begin(); it != visibilityListeners.end(); it++) {
            Java::JNILocalRef mlistener(*env, env->NewLocalRef(it->second));
            if(mlistener)
                env->CallVoidMethod(impl, Layer_class.removeOnLayerVisibleChangedListener, mlistener.get());
            env->DeleteWeakGlobalRef(it->second);
        }
        visibilityListeners.clear();
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
void ManagedLayer::addVisibilityListener (VisibilityListener *clistener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mutex);
    if(visibilityListeners.find(clistener) != visibilityListeners.end())
        return;
    LocalJNIEnv env;
    Java::JNILocalRef mlistener(*env, NULL);
    std::shared_ptr<VisibilityListener> clistenerPtr(std::move(std::unique_ptr<VisibilityListener, void(*)(const VisibilityListener *)>(clistener, Memory_leaker_const<VisibilityListener>)));
    if(Core::Interop_marshal(mlistener, *env, clistenerPtr) != TE_Ok)
        return;
    visibilityListeners[clistener] = env->NewWeakGlobalRef(mlistener);
    env->CallVoidMethod(impl, Layer_class.addOnLayerVisibleChangedListener, mlistener.get());
}
const char* ManagedLayer::getName () const throw ()
{
    return name;
}
bool ManagedLayer::isVisible () const
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, Layer_class.isVisible);
}
void ManagedLayer::removeVisibilityListener (atakmap::core::Layer::VisibilityListener *clistener)
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, mutex);
    auto entry = visibilityListeners.find(clistener);
    if(entry == visibilityListeners.end())
        return;
    LocalJNIEnv env;
    Java::JNILocalRef mlistener(*env, env->NewLocalRef(entry->second));
    if(mlistener)
        env->CallVoidMethod(impl, Layer_class.removeOnLayerVisibleChangedListener, mlistener.get());
    env->DeleteWeakGlobalRef(entry->second);
    visibilityListeners.erase(entry);
}
void ManagedLayer::setVisible (bool visibility)
{
    LocalJNIEnv env;
    env->CallVoidMethod(impl, Layer_class.setVisible, visibility);
}

namespace
{
    bool Layer_class_init(JNIEnv &env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env.GetMethodID(c.id, #m, sig)

        Layer_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/Layer");
        SET_METHOD_DEFINITION(Layer_class, setVisible, "(Z)V");
        SET_METHOD_DEFINITION(Layer_class, isVisible, "()Z");
        SET_METHOD_DEFINITION(Layer_class, addOnLayerVisibleChangedListener, "(Lcom/atakmap/map/layer/Layer$OnLayerVisibleChangedListener;)V");
        SET_METHOD_DEFINITION(Layer_class, removeOnLayerVisibleChangedListener, "(Lcom/atakmap/map/layer/Layer$OnLayerVisibleChangedListener;)V");
        SET_METHOD_DEFINITION(Layer_class, getName, "()Ljava/lang/String;");

        return true;
    }
}
