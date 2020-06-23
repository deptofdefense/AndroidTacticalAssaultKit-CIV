#include "interop/db/Interop.h"

#include "common.h"

using namespace TAK::Engine::Util;

namespace
{
    struct
    {
        jclass id;
        jmethodID moveToNext;
        jmethodID isClosed;
        jmethodID close;
    } RowIterator_class;

    bool checkInit(JNIEnv *env) NOTHROWS;
    bool Interop_class_init(JNIEnv *env) NOTHROWS;
}

TAKErr TAKEngineJNI::Interop::DB::RowIterator_moveToNext(JNIEnv *env, jobject jrowIterator) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(env->ExceptionCheck())
        return TE_Err;
    const bool result = env->CallBooleanMethod(jrowIterator, RowIterator_class.moveToNext);
    if(env->ExceptionCheck())
        return TE_Err;
    return result ? TE_Ok : TE_Done;

}
TAKErr TAKEngineJNI::Interop::DB::RowIterator_close(JNIEnv *env, jobject jrowIterator) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(env->ExceptionCheck())
        return TE_Err;
    env->CallVoidMethod(jrowIterator, RowIterator_class.close);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}
TAKErr TAKEngineJNI::Interop::DB::RowIterator_isClosed(bool *value, JNIEnv *env, jobject jrowIterator) NOTHROWS
{
    if(!checkInit(env))
        return TE_IllegalState;
    if(env->ExceptionCheck())
        return TE_Err;
    *value = env->CallBooleanMethod(jrowIterator, RowIterator_class.moveToNext);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}

namespace
{
    bool checkInit(JNIEnv *env) NOTHROWS
    {
        static bool clinit = Interop_class_init(env);
        return clinit;
    }
    bool Interop_class_init(JNIEnv *env) NOTHROWS
    {
        RowIterator_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/database/RowIterator");
        RowIterator_class.moveToNext = env->GetMethodID(RowIterator_class.id, "moveToNext", "()Z");
        RowIterator_class.close = env->GetMethodID(RowIterator_class.id, "close", "()V");
        RowIterator_class.isClosed = env->GetMethodID(RowIterator_class.id, "isClosed", "()Z");

        return true;
    }
}