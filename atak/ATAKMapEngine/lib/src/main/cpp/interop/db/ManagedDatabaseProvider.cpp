#include "common.h"

#include <jni.h>
#include <cstdio>
#include <cstdint>

#include "interop/JNIStringUTF.h"
#include "interop/java/JNILocalRef.h"
#include "ManagedDatabaseProvider.h"

#include "ManagedDatabase2.h"

#include "util/Memory.h"

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    struct {
        jclass id;
        jmethodID createMethodId;
        jmethodID isDatabaseMethodId;

        jclass uriId;
        jmethodID parseMethodId;

        jclass dbId;
        jmethodID initMethodId;

    } JDatabaseProvider_class;

    bool JDatabaseProvider_class_init(JNIEnv &env) NOTHROWS;
}

ManagedDatabaseProvider::ManagedDatabaseProvider(JNIEnv& env, jobject instance) : providerType(NULL)
{
    static bool clinit = JDatabaseProvider_class_init(env);
    m_instance = env.NewGlobalRef(instance);
}

ManagedDatabaseProvider::~ManagedDatabaseProvider()
{
    LocalJNIEnv env;

    delete[] providerType;

    env->DeleteGlobalRef(m_instance);
}

TAK::Engine::Util::TAKErr
ManagedDatabaseProvider::create(DatabasePtr& result, const DatabaseInformation& information) NOTHROWS
{
    LocalJNIEnv env;

    const char* uriStr;
    information.getUri(&uriStr);
    JNILocalRef jstr(*env, env->NewStringUTF(uriStr));
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TAKErr::TE_Err;
    }

    JNILocalRef uri(
            *env,
            env->CallStaticObjectMethod(
            JDatabaseProvider_class.uriId,
            JDatabaseProvider_class.parseMethodId,
            jstr.get()));
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TAKErr::TE_Err;
    }

    JNILocalRef javaDatabaseInformation(
            *env,
            env->NewObject(
            JDatabaseProvider_class.dbId,
            JDatabaseProvider_class.initMethodId,
            uri.get(),
            nullptr));
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TAKErr::TE_Err;
    }

    JNILocalRef jretval(
            *env,
            env->CallObjectMethod(
            m_instance,
            JDatabaseProvider_class.createMethodId,
            javaDatabaseInformation.get()));
    if (!jretval || env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TAKErr::TE_Err;
    }

    result = DatabasePtr(new ManagedDatabase2(*env, jretval.get()), Memory_deleter_const<Database2, ManagedDatabase2>);

    return TAKErr::TE_Ok;
}

TAK::Engine::Util::TAKErr
ManagedDatabaseProvider::getType(const char** value) NOTHROWS
{
    LocalJNIEnv env;
    if(!providerType) {
        int size = snprintf(NULL, 0, "jni-0x%p", this);
        providerType = new char[size+1];
        snprintf(providerType, size+1, "jni-0x%p", this);
    }
    *value= providerType;
    return TAKErr::TE_Ok;
}

namespace
{
    bool JDatabaseProvider_class_init(JNIEnv &env) NOTHROWS
    {
        JDatabaseProvider_class.id = ATAKMapEngineJNI_findClass(
                &env,
                "com/atakmap/coremap/io/IOProvider");

        JDatabaseProvider_class.createMethodId = env.GetMethodID(
                JDatabaseProvider_class.id,
                "createDatabase",
                "(Lcom/atakmap/coremap/io/DatabaseInformation;)Lcom/atakmap/database/DatabaseIface;");

        JDatabaseProvider_class.isDatabaseMethodId = env.GetMethodID(
                JDatabaseProvider_class.id,
                "isDatabase",
                "(Ljava/io/File;)Z");

        //Uri
        JDatabaseProvider_class.uriId = ATAKMapEngineJNI_findClass(
                &env,
                "android/net/Uri");

        JDatabaseProvider_class.parseMethodId = env.GetStaticMethodID(
                JDatabaseProvider_class.uriId,
                "parse",
                "(Ljava/lang/String;)Landroid/net/Uri;");


        //DatabaseInformation
        JDatabaseProvider_class.dbId = ATAKMapEngineJNI_findClass(
                &env,
                "com/atakmap/coremap/io/DatabaseInformation");

        JDatabaseProvider_class.initMethodId = env.GetMethodID(
                JDatabaseProvider_class.dbId,
                "<init>",
                "(Landroid/net/Uri;Lcom/atakmap/coremap/io/ProviderChangeRequestedListener;)V");

        return true;
    }
}
