#include "ManagedStatement.h"

#include "common.h"

#include <jni.h>

#include "interop/JNIStringUTF.h"
#include "interop/JNIByteArray.h"
#include "interop/db/Interop.h"
#include "interop/java/JNILocalRef.h"
#include "jpointer.h"
#include <db/Database2.h>
#include <db/Statement2.h>

#include <util/Error.h>

using namespace TAKEngineJNI::Interop::DB;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

namespace
{
    struct {
        jclass id;
        //StatementIface functions
        jmethodID executeMethodId;
        jmethodID closeMethodId;
    } JStatement_class;

    bool JStatement_class_init(JNIEnv &env) NOTHROWS;
}

ManagedStatement::ManagedStatement(JNIEnv& env, jobject instance) NOTHROWS
{
    static bool clinit = JStatement_class_init(env);
    m_instance = env.NewGlobalRef(instance);

    if (env.ExceptionCheck())
    {
        env.ExceptionClear();
    }
}

ManagedStatement::~ManagedStatement() NOTHROWS
{
    LocalJNIEnv env;

    if(m_instance) {
        env->CallVoidMethod(m_instance, JStatement_class.closeMethodId);

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }

        env->DeleteGlobalRef(m_instance);

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        m_instance = NULL;
    }
}

TAK::Engine::Util::TAKErr ManagedStatement::execute() NOTHROWS
{
    LocalJNIEnv env;
    env->CallVoidMethod(m_instance, JStatement_class.executeMethodId);

    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
        return TAKErr::TE_Err;
    }

    return TAKErr::TE_Ok;
}

TAK::Engine::Util::TAKErr ManagedStatement::bindBlob(
        const std::size_t idx,
        const uint8_t *blob,
        const std::size_t size) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, blob, size);
}

TAK::Engine::Util::TAKErr ManagedStatement::bindInt(const std::size_t idx, const int32_t value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedStatement::bindLong(const std::size_t idx, const int64_t value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedStatement::bindDouble(const std::size_t idx, const double value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedStatement::bindString(const std::size_t idx, const char *value) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bind(*env, m_instance, idx, value);
}

TAK::Engine::Util::TAKErr ManagedStatement::bindNull(const std::size_t idx) NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_bindNull(*env, m_instance, idx);
}

TAK::Engine::Util::TAKErr ManagedStatement::clearBindings() NOTHROWS
{
    LocalJNIEnv env;
    return Bindable_clearBindings(*env, m_instance);
}

namespace
{
    bool JStatement_class_init(JNIEnv &env) NOTHROWS
    {
        JStatement_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/database/StatementIface");

        JStatement_class.executeMethodId = env.GetMethodID(JStatement_class.id, "execute",  "()V");
        JStatement_class.closeMethodId = env.GetMethodID(JStatement_class.id, "close",  "()V");
        
        return true;
    }
}
