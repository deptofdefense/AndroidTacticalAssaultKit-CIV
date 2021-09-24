#include "ManagedDatabase2.h"

#include <jni.h>
#include "common.h"

#include <db/Database2.h>
#include <util/Error.h>
#include <port/String.h>
#include "util/Memory.h"

#include "ManagedStatement.h"
#include "ManagedQuery.h"

#include "interop/JNIStringUTF.h"
#include "interop/java/JNILocalRef.h"

using namespace TAK::Engine;
using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;
using namespace TAKEngineJNI::Interop::Java;

namespace
{
    struct {
        jclass id;
        jmethodID executeMethodId;
        jmethodID queryMethodId;
        jmethodID compileStatementMethodId;
        jmethodID compileQueryMethodId;
        jmethodID isReadOnlyMethodId;
        jmethodID closeMethodId;
        jmethodID getVersionMethodId;
        jmethodID setVersionMethodId;
        jmethodID beginTransactionMethodId;
        jmethodID setTransactionSuccessfulMethodId;
        jmethodID endTransactionMethodId;
        jmethodID inTransactionMethodId;
    } DBIface_class;

    bool DBIface_Managed_class_init(JNIEnv &env) NOTHROWS;
}

    ManagedDatabase2::ManagedDatabase2(JNIEnv &env, jobject instance)
    {
        static bool clinit = DBIface_Managed_class_init(env);
        m_instance = env.NewGlobalRef(instance);
    }

    ManagedDatabase2::~ManagedDatabase2()
    {
        LocalJNIEnv env;
        if(m_instance) {
            env->CallVoidMethod(m_instance, DBIface_class.closeMethodId);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }

            env->DeleteGlobalRef(m_instance);
        }
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::execute(const char *sql, const char **args, const std::size_t len) NOTHROWS
    {
        LocalJNIEnv env;
        JNILocalRef jArgs(*env, NULL);
        if(len) {
            jclass jstringClass = ATAKMapEngineJNI_findClass(env, "java/lang/String");
            jArgs = JNILocalRef(*env, env->NewObjectArray(len, jstringClass, NULL));
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return TAKErr::TE_Err;
            }

            for (std::size_t i(0); i < len; ++i) {
                JNILocalRef jarg(*env, env->NewStringUTF(args[i]));
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    return TAKErr::TE_Err;
                }

                env->SetObjectArrayElement(static_cast<jobjectArray>(jArgs.get()), i, jarg);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    return TAKErr::TE_Err;
                }
            }
        }

        JNILocalRef jsql(*env, env->NewStringUTF(sql));
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        env->CallVoidMethod(m_instance, DBIface_class.executeMethodId, jsql.get(), jArgs.get());
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::query(QueryPtr &query, const char *sql) NOTHROWS
    {
        LocalJNIEnv env;

        JNILocalRef jsql(*env, env->NewStringUTF(sql));
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        JNILocalRef queryIface(
                *env,
                env->CallObjectMethod(
                        m_instance,
                        DBIface_class.compileQueryMethodId,
                        jsql.get()));

        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        query = QueryPtr(new ManagedQuery(*env, queryIface.get()), Memory_deleter_const<Query, ManagedQuery>);

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::compileStatement(StatementPtr &stmt, const char *sql) NOTHROWS
    {
        LocalJNIEnv env;

        JNILocalRef jsql(*env, env->NewStringUTF(sql));
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        JNILocalRef statementIface(
                *env,
                env->CallObjectMethod(
                        m_instance,
                        DBIface_class.compileStatementMethodId,
                        jsql.get()));

        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        stmt = StatementPtr(new ManagedStatement(*env, statementIface.get()), Memory_deleter_const<Statement2, ManagedStatement>);

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::compileQuery(QueryPtr &query, const char *sql) NOTHROWS
    {
        LocalJNIEnv env;

        JNILocalRef jsql(*env, env->NewStringUTF(sql));
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        JNILocalRef queryIface(
                *env,
                env->CallObjectMethod(
                        m_instance,
                        DBIface_class.compileQueryMethodId,
                        jsql.get()));
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        query = QueryPtr(new ManagedQuery(*env, queryIface.get()), Memory_deleter_const<Query, ManagedQuery>);

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::isReadOnly(bool *value) NOTHROWS
    {
        LocalJNIEnv env;

        jboolean readOnly = env->CallBooleanMethod(m_instance, DBIface_class.isReadOnlyMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        *value = (bool)readOnly;

        return TE_Ok;
    }
    TAK::Engine::Util::TAKErr ManagedDatabase2::getVersion(int *value) NOTHROWS
    {
        LocalJNIEnv env;

        jint version = env->CallIntMethod(m_instance, DBIface_class.getVersionMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        *value = (int)version;

        return TE_Ok;
    }
    TAK::Engine::Util::TAKErr ManagedDatabase2::setVersion(const int version) NOTHROWS
    {
        LocalJNIEnv env;

        env->CallVoidMethod(m_instance, DBIface_class.setVersionMethodId, (jint)version);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::beginTransaction() NOTHROWS
    {
        LocalJNIEnv env;

        env->CallVoidMethod(m_instance, DBIface_class.beginTransactionMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        return TE_Ok;
    }
    TAK::Engine::Util::TAKErr ManagedDatabase2::setTransactionSuccessful() NOTHROWS
    {
        LocalJNIEnv env;

        env->CallVoidMethod(m_instance, DBIface_class.setTransactionSuccessfulMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        return TE_Ok;
    }
    TAK::Engine::Util::TAKErr ManagedDatabase2::endTransaction() NOTHROWS
    {
        LocalJNIEnv env;

        env->CallVoidMethod(m_instance, DBIface_class.endTransactionMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        return TE_Ok;
    }
    TAK::Engine::Util::TAKErr ManagedDatabase2::inTransaction(bool *value) NOTHROWS
    {
        LocalJNIEnv env;

        jboolean transaction = env->CallBooleanMethod(m_instance, DBIface_class.inTransactionMethodId);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return TAKErr::TE_Err;
        }

        *value = (bool)transaction;

        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr ManagedDatabase2::getErrorMessage(Port::String &value) NOTHROWS
    {
        value = NULL;

        return TAK::Engine::Util::TE_Ok;
    }

namespace
{
bool DBIface_Managed_class_init(JNIEnv &env) NOTHROWS
    {
        DBIface_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/database/DatabaseIface");

        DBIface_class.executeMethodId = env.GetMethodID(DBIface_class.id, "execute", "(Ljava/lang/String;[Ljava/lang/String;)V");
        DBIface_class.queryMethodId = env.GetMethodID(DBIface_class.id, "query", "(Ljava/lang/String;[Ljava/lang/String;)Lcom/atakmap/database/CursorIface;");
        DBIface_class.compileStatementMethodId = env.GetMethodID(DBIface_class.id, "compileStatement", "(Ljava/lang/String;)Lcom/atakmap/database/StatementIface;");
        DBIface_class.compileQueryMethodId = env.GetMethodID(DBIface_class.id, "compileQuery", "(Ljava/lang/String;)Lcom/atakmap/database/QueryIface;");
        DBIface_class.isReadOnlyMethodId = env.GetMethodID(DBIface_class.id, "isReadOnly", "()Z");
        DBIface_class.closeMethodId = env.GetMethodID(DBIface_class.id, "close", "()V");
        DBIface_class.getVersionMethodId = env.GetMethodID(DBIface_class.id, "getVersion", "()I");
        DBIface_class.setVersionMethodId = env.GetMethodID(DBIface_class.id, "setVersion", "(I)V");
        DBIface_class.beginTransactionMethodId = env.GetMethodID(DBIface_class.id, "beginTransaction", "()V");
        DBIface_class.setTransactionSuccessfulMethodId = env.GetMethodID(DBIface_class.id, "setTransactionSuccessful", "()V");
        DBIface_class.endTransactionMethodId = env.GetMethodID(DBIface_class.id, "endTransaction", "()V");
        DBIface_class.inTransactionMethodId = env.GetMethodID(DBIface_class.id, "inTransaction", "()Z");

        return true;
    }
}
