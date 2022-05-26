#include "interop/core/ManagedRenderContext.h"

#include <util/Memory.h>

#include "common.h"
#include "interop/java/JNILocalRef.h"
#include "interop/java/JNIRunnable.h"

using namespace TAKEngineJNI::Interop::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID isRenderThread;
        jmethodID queueEvent;
        jmethodID requestRefresh;
        jmethodID setFrameRate;
        jmethodID getFrameRate;
        jmethodID setContinuousRenderEnabled;
        jmethodID isContinuousRenderEnabled;
        jmethodID supportsChildContext;
        jmethodID createChildContext;
        jmethodID destroyChildContext;
        jmethodID isAttached;
        jmethodID attach;
        jmethodID detach;
        jmethodID isMainContext;
        jmethodID getRenderSurface;
    } RenderContext_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool RenderContext_class_init(JNIEnv &env) NOTHROWS;
}

ManagedRenderContext::ManagedRenderContext(JNIEnv &env_, jobject impl_) NOTHROWS :
    impl(env_.NewGlobalRef(impl_)),
    parent(NULL)
{
    checkInit(env_);
}
ManagedRenderContext::ManagedRenderContext(JNIEnv &env_, jobject impl_, jobject parent_) NOTHROWS :
        impl(env_.NewGlobalRef(impl_)),
        parent(env_.NewGlobalRef(parent_))
{
    checkInit(env_);
}
ManagedRenderContext::~ManagedRenderContext() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        // if a child context, need to have parent invoke destroy
        if(parent) {
            env->CallVoidMethod(parent, RenderContext_class.destroyChildContext, impl);

            env->DeleteGlobalRef(parent);
            parent = NULL;
        }
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
}
bool ManagedRenderContext::isRenderThread() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.isRenderThread);
}
TAKErr ManagedRenderContext::queueEvent(void(*runnable)(void *) NOTHROWS, std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS
{
    TAKErr code(TE_Ok);
    LocalJNIEnv env;
    Java::JNILocalRef mrunnable(*env, NULL);
    code = Java::Interop_marshal(mrunnable, *env, runnable, std::move(opaque));
    TE_CHECKRETURN_CODE(code);

    env->CallVoidMethod(impl, RenderContext_class.queueEvent, mrunnable.release());
    return code;
}
void ManagedRenderContext::requestRefresh() NOTHROWS
{
    LocalJNIEnv env;
    env->CallVoidMethod(impl, RenderContext_class.requestRefresh);
}
TAKErr ManagedRenderContext::setFrameRate(const float rate) NOTHROWS
{
    LocalJNIEnv env;
    env->CallVoidMethod(impl, RenderContext_class.setFrameRate, rate);
    return TE_Ok;
}
float ManagedRenderContext::getFrameRate() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallFloatMethod(impl, RenderContext_class.getFrameRate);
}
void ManagedRenderContext::setContinuousRenderEnabled(const bool enabled) NOTHROWS
{
    LocalJNIEnv env;
    env->CallVoidMethod(impl, RenderContext_class.setContinuousRenderEnabled, enabled);
}
bool ManagedRenderContext::isContinuousRenderEnabled() NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.isContinuousRenderEnabled);
}
bool ManagedRenderContext::supportsChildContext() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.supportsChildContext);
}
TAKErr ManagedRenderContext::createChildContext(RenderContextPtr &value) NOTHROWS
{
    LocalJNIEnv env;
    if(!env->CallBooleanMethod(impl, RenderContext_class.supportsChildContext))
        return TE_IllegalState;
    Java::JNILocalRef mchild(*env, env->CallObjectMethod(impl, RenderContext_class.createChildContext));
    if(!mchild)
        return TE_Err;
    value = RenderContextPtr(new ManagedRenderContext(*env, mchild, impl), Memory_deleter_const<RenderContext, ManagedRenderContext>);
    return TE_Ok;
}
bool ManagedRenderContext::isAttached() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.isAttached);
}
bool ManagedRenderContext::attach() NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.attach);
}
bool ManagedRenderContext::detach() NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.detach);
}
bool ManagedRenderContext::isMainContext() const NOTHROWS
{
    LocalJNIEnv env;
    return env->CallBooleanMethod(impl, RenderContext_class.isMainContext);
}
RenderSurface *ManagedRenderContext::getRenderSurface() const NOTHROWS
{
    // XXX - TODO
    return NULL;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = RenderContext_class_init(env);
        return clinit;
    }
    bool RenderContext_class_init(JNIEnv &env) NOTHROWS
    {
#define SET_METHOD_DEFINITION(c, m, sig) \
    c.m = env.GetMethodID(c.id, #m, sig)

        RenderContext_class.id = ATAKMapEngineJNI_findClass(&env, "gov/tak/api/engine/map/RenderContext");
        SET_METHOD_DEFINITION(RenderContext_class, isRenderThread, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, queueEvent, "(Ljava/lang/Runnable;)V");
        SET_METHOD_DEFINITION(RenderContext_class, requestRefresh, "()V");
        SET_METHOD_DEFINITION(RenderContext_class, setFrameRate, "(F)V");
        SET_METHOD_DEFINITION(RenderContext_class, getFrameRate, "()F");
        SET_METHOD_DEFINITION(RenderContext_class, setContinuousRenderEnabled, "(Z)V");
        SET_METHOD_DEFINITION(RenderContext_class, isContinuousRenderEnabled, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, supportsChildContext, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, createChildContext, "()Lgov/tak/api/engine/map/RenderContext;");
        SET_METHOD_DEFINITION(RenderContext_class, destroyChildContext, "(Lgov/tak/api/engine/map/RenderContext;)V");
        SET_METHOD_DEFINITION(RenderContext_class, isAttached, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, attach, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, detach, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, isMainContext, "()Z");
        SET_METHOD_DEFINITION(RenderContext_class, getRenderSurface, "()Lgov/tak/api/engine/map/RenderSurface;");

        return true;
    }
}
