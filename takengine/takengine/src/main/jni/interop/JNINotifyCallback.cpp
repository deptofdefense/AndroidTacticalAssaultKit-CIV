#include "JNINotifyCallback.h"

#include "../common.h"

using namespace TAK::Engine::Util;

namespace
{
    struct
    {
        jclass id;
        jmethodID onEvent;
    } NotifyCallback_class;

    bool init() NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::JNINotifyCallback_eventOccurred(jobject callback) NOTHROWS
{
    static bool initialized = init();

    LocalJNIEnv env;
    // if there is a pending exception, return
    if(env->ExceptionCheck())
        return TE_Ok;
    try {
        const bool result = env->CallBooleanMethod(callback, NotifyCallback_class.onEvent);
        return result ? TE_Ok : TE_Done;
    } catch(...) {
        return TE_Err;
    }
}

namespace
{
    bool init() NOTHROWS
    {
        LocalJNIEnv env;
        NotifyCallback_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/interop/NotifyCallback");
        NotifyCallback_class.onEvent = env->GetMethodID(NotifyCallback_class.id, "onEvent", "()Z");
        return true;
    }
}