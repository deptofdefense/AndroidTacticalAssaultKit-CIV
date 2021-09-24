#ifndef ATAK_MANAGEDDATABASE2_H
#define ATAK_MANAGEDDATABASE2_H

#include <db/Database2.h>
#include <util/Error.h>
#include <port/String.h>

#include <jni.h>

using namespace TAK::Engine;
using namespace TAK::Engine::DB;

class ManagedDatabase2 : public Database2
{
public:
    ManagedDatabase2(JNIEnv &env, jobject instance);
public:
    ~ManagedDatabase2() override;
    TAK::Engine::Util::TAKErr execute(const char *sql, const char **args, const std::size_t len) NOTHROWS override;
    TAK::Engine::Util::TAKErr query(QueryPtr &query, const char *sql) NOTHROWS override;
    TAK::Engine::Util::TAKErr compileStatement(StatementPtr &stmt, const char *sql) NOTHROWS override;
    TAK::Engine::Util::TAKErr compileQuery(QueryPtr &query, const char *sql) NOTHROWS override;

    TAK::Engine::Util::TAKErr isReadOnly(bool *value) NOTHROWS override;
    TAK::Engine::Util::TAKErr getVersion(int *value) NOTHROWS override;
    TAK::Engine::Util::TAKErr setVersion(const int version) NOTHROWS override;

    TAK::Engine::Util::TAKErr beginTransaction() NOTHROWS override;
    TAK::Engine::Util::TAKErr setTransactionSuccessful() NOTHROWS override;
    TAK::Engine::Util::TAKErr endTransaction() NOTHROWS override;

    TAK::Engine::Util::TAKErr inTransaction(bool *value) NOTHROWS override;

    TAK::Engine::Util::TAKErr getErrorMessage(Port::String &value) NOTHROWS override;

private:
    jobject m_instance;
};

#endif //ATAK_MANAGEDDATABASE2_H
