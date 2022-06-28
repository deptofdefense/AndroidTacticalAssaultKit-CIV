#include "JNIIterator.h"

#include "common.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop::Java;

namespace {
    struct
    {
        jclass id;
        jmethodID hasNext;
        jmethodID next;
        jmethodID remove;
    } Iterator_class;

    bool checkInit(JNIEnv &env) NOTHROWS;
    bool JNIIterator_class_init(JNIEnv &env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::Java::JNIIterator_hasNext(bool &value, JNIEnv &env, jobject iterator) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!iterator)
        return TE_InvalidArg;
    if(env.ExceptionCheck())
        return TE_Err;
    const jboolean jvalue = env.CallBooleanMethod(iterator, Iterator_class.hasNext);
    value = jvalue == JNI_TRUE;
    if(env.ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Java::JNIIterator_next(JNILocalRef &value, JNIEnv &env, jobject iterator) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!iterator)
        return TE_InvalidArg;
    if(env.ExceptionCheck())
        return TE_Err;
    value = JNILocalRef(env, env.CallObjectMethod(iterator, Iterator_class.next));
    if(env.ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::Java::JNIIterator_remove(JNIEnv &env, jobject iterator) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(!iterator)
        return TE_InvalidArg;
    if(env.ExceptionCheck())
        return TE_Err;
    env.CallVoidMethod(iterator, Iterator_class.remove);
    if(env.ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv &env) NOTHROWS
    {
        static bool clinit = JNIIterator_class_init(env);
        return clinit;
    }
    bool JNIIterator_class_init(JNIEnv &env) NOTHROWS
    {
        Iterator_class.id = ATAKMapEngineJNI_findClass(&env, "java/util/Iterator");
        Iterator_class.hasNext = env.GetMethodID(Iterator_class.id, "hasNext", "()Z");
        Iterator_class.next = env.GetMethodID(Iterator_class.id, "next", "()Ljava/lang/Object;");
        Iterator_class.remove = env.GetMethodID(Iterator_class.id, "remove", "()V");

        return true;
    }
}
