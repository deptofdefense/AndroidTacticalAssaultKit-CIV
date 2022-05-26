#include "interop/core/ManagedVisibilityListener.h"

#include "common.h"
#include "interop/core/Interop.h"
#include "interop/java/JNILocalRef.h"

using namespace TAKEngineJNI::Interop::Core;

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID onLayerVisibleChanged;
    } Layer_OnLayerVisibleChangedListener_class;

    bool Layer_OnLayerVisibleChangedListener_init(JNIEnv &) NOTHROWS;
}

ManagedVisibilityListener::ManagedVisibilityListener(JNIEnv &env_, jobject impl_) NOTHROWS :
    impl(env_.NewGlobalRef(impl_))
{
    static bool clinit = Layer_OnLayerVisibleChangedListener_init(env_);
}
ManagedVisibilityListener::~ManagedVisibilityListener() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
void ManagedVisibilityListener::visibilityChanged(atakmap::core::Layer &csubject)
{
    LocalJNIEnv env;
    Java::JNILocalRef msubject(*env, NULL);
    if(Interop_marshal(msubject, *env, csubject) != TE_Ok)
        return;
    env->CallVoidMethod(impl, Layer_OnLayerVisibleChangedListener_class.onLayerVisibleChanged, msubject.get());
}

namespace
{
    bool Layer_OnLayerVisibleChangedListener_init(JNIEnv &env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env.GetMethodID(c.id, #m, sig)

        Layer_OnLayerVisibleChangedListener_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/layer/Layer$OnLayerVisibleChangedListener");
        SET_METHOD_DEFINITION(Layer_OnLayerVisibleChangedListener_class, onLayerVisibleChanged, "(Lcom/atakmap/map/layer/Layer;)V");
        
        return true;
    }
}
