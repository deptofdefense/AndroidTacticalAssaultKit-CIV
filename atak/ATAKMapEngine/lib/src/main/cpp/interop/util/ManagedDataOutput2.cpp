#include "interop/util/ManagedDataOutput2.h"

#include "common.h"

#include "interop/JNIByteArray.h"
#include "interop/java/JNILocalRef.h"

using namespace TAKEngineJNI::Interop::Util;

using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct
    {
        jclass id;
        jmethodID write;
        jmethodID close;
    } OutputStream_class;

    bool ManagedDataOutput2_init(JNIEnv &env) NOTHROWS;
}

ManagedDataOutput2::ManagedDataOutput2() NOTHROWS :
    impl(NULL)
{}
ManagedDataOutput2::~ManagedDataOutput2() NOTHROWS
{
    close();
}
TAKErr ManagedDataOutput2::open(JNIEnv &env, jobject mimpl) NOTHROWS
{
    static bool clinit = ManagedDataOutput2_init(env);
    if(!clinit)
        return TE_IllegalState;
    if(!mimpl)
        return TE_InvalidArg;
    close();
    impl = env.NewGlobalRef(mimpl);
    return TE_Ok;
}
TAKErr ManagedDataOutput2::close() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->CallVoidMethod(impl, OutputStream_class.close);
        if(env->ExceptionCheck())
            env->ExceptionClear();
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
    return TE_Ok;
}
TAKErr ManagedDataOutput2::write(const uint8_t *buf, const std::size_t len) NOTHROWS
{
    LocalJNIEnv env;
    Java::JNILocalRef marr(*env, JNIByteArray_newByteArray(env, reinterpret_cast<const jbyte *>(buf), len));
    env->CallVoidMethod(impl, OutputStream_class.write, marr.get(), 0, len);
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_IO;
    }
    return TE_Ok;
}
TAKErr ManagedDataOutput2::writeByte(const uint8_t value) NOTHROWS
{
    return write(&value, 1u);
}

namespace
{
    bool ManagedDataOutput2_init(JNIEnv &env) NOTHROWS
    {
        OutputStream_class.id = ATAKMapEngineJNI_findClass(&env, "java/io/OutputStream");
        OutputStream_class.write = env.GetMethodID(OutputStream_class.id, "write", "([BII)V");
        OutputStream_class.close = env.GetMethodID(OutputStream_class.id, "close", "()V");
        return true;
    }
}
