#include "interop/db/Interop.h"

#include "common.h"
#include "interop/JNIByteArray.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID moveToNext;
        jmethodID isClosed;
        jmethodID close;
    } RowIterator_class;

    struct {
        jclass id;
        jmethodID bindByteArrayMethodId;
        jmethodID bindIntMethodId;
        jmethodID bindLongMethodId;
        jmethodID bindDoubleMethodId;
        jmethodID bindStringMethodId;
        jmethodID bindNullMethodId;
        jmethodID clearBindingsMethodId;
    } Bindable_class;

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

TAKErr
TAKEngineJNI::Interop::DB::Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS {
    if(!blob)
        return Bindable_bindNull(env, mbindable, idx);

    Java::JNILocalRef jblob(env,
                      JNIByteArray_newByteArray(&env, reinterpret_cast<const jbyte *>(blob),
                                                size));
    if (!jblob) {
        return TE_Err;
    }

    env.CallVoidMethod(mbindable, Bindable_class.bindByteArrayMethodId, idx, jblob.get());
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::DB::Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const int32_t value) NOTHROWS {
    env.CallVoidMethod(mbindable, Bindable_class.bindIntMethodId, idx, value);
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::DB::Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const int64_t value) NOTHROWS {
    env.CallVoidMethod(mbindable, Bindable_class.bindLongMethodId, idx, value);
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::DB::Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const double value) NOTHROWS {
    env.CallVoidMethod(mbindable, Bindable_class.bindDoubleMethodId, idx, value);
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::DB::Bindable_bind(JNIEnv &env, jobject mbindable, const std::size_t idx, const char *value) NOTHROWS {
    Java::JNILocalRef jstr(env, env.NewStringUTF(value));
    env.CallVoidMethod(mbindable, Bindable_class.bindStringMethodId, idx, (jstring)jstr);
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::DB::Bindable_bindNull(JNIEnv &env, jobject mbindable, const std::size_t idx) NOTHROWS {
    env.CallVoidMethod(mbindable, Bindable_class.bindNullMethodId, idx);
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
    return TE_Ok;
}

TAKErr TAKEngineJNI::Interop::DB::Bindable_clearBindings(JNIEnv &env, jobject mbindable) NOTHROWS {
    env.CallVoidMethod(mbindable, Bindable_class.clearBindingsMethodId);
    if(env.ExceptionCheck())
    {
        env.ExceptionClear();
        return TE_Err;
    }
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
        
        Bindable_class.id = ATAKMapEngineJNI_findClass(env, "com/atakmap/database/Bindable");
        Bindable_class.bindByteArrayMethodId = env->GetMethodID(Bindable_class.id, "bind", "(I[B)V");
        Bindable_class.bindIntMethodId = env->GetMethodID(Bindable_class.id, "bind", "(II)V");
        Bindable_class.bindLongMethodId = env->GetMethodID(Bindable_class.id, "bind", "(IJ)V");
        Bindable_class.bindDoubleMethodId = env->GetMethodID(Bindable_class.id, "bind", "(ID)V");
        Bindable_class.bindStringMethodId = env->GetMethodID(Bindable_class.id, "bind", "(ILjava/lang/String;)V");
        Bindable_class.bindNullMethodId = env->GetMethodID(Bindable_class.id, "bindNull", "(I)V");
        Bindable_class.clearBindingsMethodId = env->GetMethodID(Bindable_class.id, "clearBindings", "()V");

        return true;
    }
}