#ifndef ATAKMAP_DB_DATABASE2_H_INCLUDED
#define ATAKMAP_DB_DATABASE2_H_INCLUDED

#include <memory>


#include "port/Platform.h"
#include "port/String.h"
#include "port/Vector.h"
#include "util/Error.h"
#include "util/NonCopyable.h"

#define TE_CHECK_CODE_LOG_DB_ERRMSG(c, db) \
    if((c) != TAK::Engine::Util::TE_Ok) {\
        TAK::Engine::Port::String errmsg; \
        if (db->getErrorMessage(errmsg) == TAK::Engine::Util::TE_Ok) \
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "db error: %s", errmsg.get()); \
    }

namespace TAK {
    namespace Engine {
        namespace DB {

            class ENGINE_API Query;
            class ENGINE_API Statement2;

            typedef std::unique_ptr<Query, void(*)(const Query *)> QueryPtr;
            typedef std::unique_ptr<Statement2, void(*)(const Statement2 *)> StatementPtr;

            class ENGINE_API Database2
            {
            public:
                class Transaction;                  // Scope-based transaction management.
            protected:
                virtual ~Database2() = 0;
            public:
                virtual TAK::Engine::Util::TAKErr execute(const char *sql, const char **args, const std::size_t len) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr query(QueryPtr &query, const char *sql) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr compileStatement(StatementPtr &stmt, const char *sql) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr compileQuery(QueryPtr &query, const char *sql) NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr isReadOnly(bool *value) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr getVersion(int *value) NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr setVersion(const int version) NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr beginTransaction() NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr setTransactionSuccessful() NOTHROWS = 0;
                virtual TAK::Engine::Util::TAKErr endTransaction() NOTHROWS = 0;

                virtual TAK::Engine::Util::TAKErr inTransaction(bool *value) NOTHROWS = 0;
                
                virtual TAK::Engine::Util::TAKErr getErrorMessage(Port::String &value) NOTHROWS;
            };

            typedef std::unique_ptr<Database2, void(*)(const Database2 *)> DatabasePtr;

            ///=============================================================================
            ///
            ///  class atakmap::db::Database::Transaction
            ///
            ///     A concrete class that implements a scope-based database transaction.
            ///     Transactions are not nestable, so they are not copyable.
            ///
            ///=============================================================================


            class Database2::Transaction : TAK::Engine::Util::NonCopyable
            {
            public:
                Transaction(Database2& db_) NOTHROWS :
                    db(db_),
                    valid(db_.beginTransaction() == TAK::Engine::Util::TE_Ok)
                {}

                ~Transaction() NOTHROWS
                {
                    if (valid)
                    db.endTransaction();
                }

                bool isValid() NOTHROWS{ return valid; }
            private:
                Database2 &db;
                bool valid;
            };

			ENGINE_API TAK::Engine::Util::TAKErr Databases_getColumnNames(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, Database2 &db, const char *tableName) NOTHROWS;
			ENGINE_API TAK::Engine::Util::TAKErr Databases_getDatabaseFilePath(TAK::Engine::Port::String &value, Database2 &db) NOTHROWS;
			ENGINE_API TAK::Engine::Util::TAKErr Databases_getNextAutoincrementID(int64_t *value, Database2 &db, const char *table) NOTHROWS;
			ENGINE_API TAK::Engine::Util::TAKErr Databases_getTableNames(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, Database2 &db) NOTHROWS;
			ENGINE_API TAK::Engine::Util::TAKErr Databases_lastChangeCount(unsigned long *value, Database2 &db) NOTHROWS;
			ENGINE_API TAK::Engine::Util::TAKErr Databases_lastInsertRowID(int64_t *value, Database2 &db) NOTHROWS;
			ENGINE_API TAK::Engine::Util::TAKErr Databases_openDatabase(DatabasePtr &db, const char* databaseFilePath, const bool readOnly = false) NOTHROWS;
			/**
			 * @param passphrase    The passphrase as a C-string, `nullptr` if no passphrase
			 */
			ENGINE_API TAK::Engine::Util::TAKErr Databases_openDatabase(DatabasePtr &db, const char* databaseFilePath, const char *passphrase, const bool readOnly = false) NOTHROWS;
			/**
			 * @param key       The binary passphrase key; `nullptr` if no passphrase
			 * @param keylen    The length of the passphrase key, in bytes
			 */
			ENGINE_API TAK::Engine::Util::TAKErr Databases_openDatabase(DatabasePtr &db, const char* databaseFilePath, const uint8_t *key, const std::size_t keylen, const bool readOnly = false) NOTHROWS;

			ENGINE_API void Databases_enableDebug(bool v) NOTHROWS;

        }
    }
}

#endif // ATAKMAP_DB_DATABASE2_H_INCLUDED

