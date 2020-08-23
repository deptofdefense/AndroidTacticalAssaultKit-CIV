#include "interop/java/JNIRunnable.h"

#include <util/Memory.h>

#include "common.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop::Java;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID ctor;
    } NativeRunnable_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool NativeRunnable_class_init(JNIEnv &env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Java::Interop_marshal(JNILocalRef &value, JNIEnv &env, void(*run)(void *), std::unique_ptr<void, void(*)(const void *)> &&opaque) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!opaque.get())
        return TE_InvalidArg;
    value = JNILocalRef(env, env.NewObject(NativeRunnable_class.id, NativeRunnable_class.ctor, NewPointer(&env, std::move(opaque)), INTPTR_TO_JLONG(run), NULL));
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = NativeRunnable_class_init(env);
        return clinit;
    }
    bool NativeRunnable_class_init(JNIEnv &env) NOTHROWS
    {
        NativeRunnable_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/interop/NativeRunnable");
        NativeRunnable_class.ctor = env.GetMethodID(NativeRunnable_class.id, "<init>", "(Lcom/atakmap/interop/Pointer;JLjava/lang/Object;)V");
        return true;
    }
}
