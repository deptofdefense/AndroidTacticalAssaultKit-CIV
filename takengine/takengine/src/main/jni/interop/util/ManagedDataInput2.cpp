#include "interop/util/ManagedDataInput2.h"

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
        jmethodID read;
        jmethodID close;
    } InputStream_class;

    bool ManagedDataInput2_init(JNIEnv &env) NOTHROWS;
}

ManagedDataInput2::ManagedDataInput2() NOTHROWS :
    impl(NULL)
{}
ManagedDataInput2::~ManagedDataInput2() NOTHROWS
{
    close();
}
TAKErr ManagedDataInput2::open(JNIEnv &env, jobject mobject) NOTHROWS
{
    static bool clinit = ManagedDataInput2_init(env);
    if(!clinit)
        return TE_IllegalState;
    if(!mobject)
        return TE_InvalidArg;
    close();
    impl = env.NewGlobalRef(mobject);
    return TE_Ok;
}
TAKErr ManagedDataInput2::close() NOTHROWS
{
    if(impl) {
        LocalJNIEnv env;
        env->CallVoidMethod(impl, InputStream_class.close);
        if(env->ExceptionCheck())
            env->ExceptionClear();
        env->DeleteGlobalRef(impl);
        impl = NULL;
    }
    return TE_Ok;
}
TAKErr ManagedDataInput2::read(uint8_t *buf, std::size_t *numRead, const std::size_t len) NOTHROWS
{
    LocalJNIEnv env;
    Java::JNILocalRef marr(*env, env->NewByteArray(len));
    jint n = env->CallIntMethod(impl, InputStream_class.read, marr.get(), 0, len);
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_IO;
    }
    if(n < 1)
        return TE_EOF;
    JNIByteArray jarr(*env, (jbyteArray)marr.get(), JNI_ABORT);
    memcpy(buf, jarr.get<const uint8_t>(), n);
    *numRead = n;
    return TE_Ok;
}
TAKErr ManagedDataInput2::readByte(uint8_t *value) NOTHROWS
{
    LocalJNIEnv env;
    Java::JNILocalRef marr(*env, env->NewByteArray(1u));
    jint numRead = env->CallIntMethod(impl, InputStream_class.read, marr.get(), 0, 1);
    if(numRead < 1)
        return TE_EOF;
    JNIByteArray jarr(*env, (jbyteArray)marr.get(), JNI_ABORT);
    *value = jarr[0];
    return TE_Ok;
}
int64_t ManagedDataInput2::length() const NOTHROWS
{
    return -1LL;
}

namespace
{
    bool ManagedDataInput2_init(JNIEnv &env) NOTHROWS
    {
        InputStream_class.id = ATAKMapEngineJNI_findClass(&env, "java/io/InputStream");
        InputStream_class.read = env.GetMethodID(InputStream_class.id, "read", "([BII)I");
        InputStream_class.close = env.GetMethodID(InputStream_class.id, "close", "()V");
        return true;
    }
}
