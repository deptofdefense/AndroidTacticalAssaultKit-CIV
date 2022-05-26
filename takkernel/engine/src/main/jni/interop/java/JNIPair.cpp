#include "interop/java/JNIPair.h"

#include "common.h"

using namespace TAK::Engine::Util;

namespace
{
    struct
    {
        jclass id;
        jmethodID create;
    } Pair_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool Pair_class_init(JNIEnv &env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Java::JNIPair_create(Java::JNILocalRef &value, JNIEnv &env, jobject first, jobject second) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    value = Java::JNILocalRef(env, env.CallStaticObjectMethod(Pair_class.id, Pair_class.create, first, second));
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = Pair_class_init(env);
        return clinit;
    }
    bool Pair_class_init(JNIEnv &env) NOTHROWS
    {
        Pair_class.id = ATAKMapEngineJNI_findClass(&env, "android/util/Pair");
        Pair_class.create = env.GetStaticMethodID(Pair_class.id, "create", "(Ljava/lang/Object;Ljava/lang/Object;)Landroid/util/Pair;");
        return true;
    }
}
