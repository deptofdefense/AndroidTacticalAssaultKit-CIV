#include "interop/java/JNIPrimitive.h"

#include "common.h"

using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct
    {
        jclass id;
        jmethodID valueOf;
    } Boolean_class;

    struct
    {
        jclass id;
        jmethodID valueOf;
    } Integer_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool JNIPrimitive_class_init(JNIEnv &env) NOTHROWS;
}

JNILocalRef TAKEngineJNI::Interop::Java::Boolean_valueOf(JNIEnv &env, const bool value) NOTHROWS
{
    if(!checkInit(env))
        return JNILocalRef(env, NULL);
    return JNILocalRef(env, env.CallStaticObjectMethod(Boolean_class.id, Boolean_class.valueOf, value));
}
JNILocalRef TAKEngineJNI::Interop::Java::Integer_valueOf(JNIEnv &env, const int value) NOTHROWS
{
    if(!checkInit(env))
        return JNILocalRef(env, NULL);
    return JNILocalRef(env, env.CallStaticObjectMethod(Integer_class.id, Integer_class.valueOf, value));
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = JNIPrimitive_class_init(env);
        return clinit;
    }
    bool JNIPrimitive_class_init(JNIEnv &env) NOTHROWS
    {
        Boolean_class.id = ATAKMapEngineJNI_findClass(&env, "java/lang/Boolean");
        Boolean_class.valueOf = env.GetStaticMethodID(Boolean_class.id, "valueOf", "(Z)Ljava/lang/Boolean;");

        Integer_class.id = ATAKMapEngineJNI_findClass(&env, "java/lang/Integer");
        Integer_class.valueOf = env.GetStaticMethodID(Integer_class.id, "valueOf", "(I)Ljava/lang/Integer;");

        return true;
    }
}
