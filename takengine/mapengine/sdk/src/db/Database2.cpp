#include "db/Database2.h"

#include <cstring>
#include <iostream>
#include <sstream>
#include <string>
#ifndef MSVC
#include <strings.h>
#endif
#include <vector>


#include "sqlite3.h"                    // Must appear before spatialite.h.
#include "spatialite.h"

#include "port/StringBuilder.h"

#include "db/Query.h"
#include "db/Statement2.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Logging.h"
#include "util/NonCopyable.h"

using namespace TAK::Engine::DB;

using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Thread;

using namespace atakmap::db;
using namespace atakmap::util;

#define CHATTY_CONNECTION

#define CHECKRETURN_CODE(c) \
    if((c) != TE_Ok) return c;

#define AUTOINCREMENT_QUERY \
    "SELECT seq FROM sqlite_sequence WHERE name = ?"
#define TABLE_NAME_QUERY \
    "SELECT name FROM sqlite_master WHERE type=\'table\'"

#define MEM_FN( fn )    "TAK::Engine::DB::SpatiaLiteDB::" fn ": "

TAK::Engine::Util::TAKErr Database2::getErrorMessage(TAK::Engine::Port::String &value) NOTHROWS {
    return TAK::Engine::Util::TE_Unsupported;
}

namespace
{
    class SpatiaLiteDB : public Database2,
                         TAK::Engine::Util::NonCopyable
    {
    public :
        explicit
        SpatiaLiteDB(const char* filePath = nullptr)  // Defaults to temporary DB.
            throw (DB_Error);

        SpatiaLiteDB(const char* filePath,
        const uint8_t *key,
        const std::size_t keylen,
        bool readOnly)
            throw (DB_Error);
    public:
        ~SpatiaLiteDB() override;
    public:
        TAK::Engine::Util::TAKErr execute(const char *sql, const char **args, const std::size_t len) NOTHROWS override;
        TAK::Engine::Util::TAKErr query(QueryPtr &cursor, const char *sql) NOTHROWS override;
        TAK::Engine::Util::TAKErr compileStatement(StatementPtr &stmt, const char *sql) NOTHROWS override;
        TAK::Engine::Util::TAKErr compileQuery(QueryPtr &query, const char *sql) NOTHROWS override;

        TAK::Engine::Util::TAKErr isReadOnly(bool *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr getVersion(int *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr setVersion(const int version) NOTHROWS override;

        TAK::Engine::Util::TAKErr beginTransaction() NOTHROWS override;
        TAK::Engine::Util::TAKErr setTransactionSuccessful() NOTHROWS override;
        TAK::Engine::Util::TAKErr endTransaction() NOTHROWS override;

        TAK::Engine::Util::TAKErr inTransaction(bool *value) NOTHROWS override;

        TAK::Engine::Util::TAKErr getErrorMessage(TAK::Engine::Port::String &value) NOTHROWS override;
    private :
        void closeConnection() NOTHROWS;
    private :
        Mutex mutex;
        struct sqlite3* connection;
        void* cache;
        bool inTrans;
        bool successfulTrans;
        bool readOnly;
    };

    class StatementImpl : public Statement2
    {
    public:
        StatementImpl(sqlite3_stmt* stmt) NOTHROWS; // Must not be NULL.
    public :
        ~StatementImpl() NOTHROWS override;
    public :
        TAK::Engine::Util::TAKErr execute() NOTHROWS override;
    public :
        TAK::Engine::Util::TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindNull(const std::size_t idx) NOTHROWS override;
        TAK::Engine::Util::TAKErr clearBindings() NOTHROWS override;
    private:
        sqlite3_stmt* impl;
    };

    class QueryImpl : public Query
    {
    public:
        QueryImpl(sqlite3_stmt* stmt) NOTHROWS; // Must not be NULL.
    public :
        ~QueryImpl() NOTHROWS override;
#if 0
    public :
        virtual TAK::Engine::Util::TAKErr reset() NOTHROWS;
#endif
    public :
        TAK::Engine::Util::TAKErr moveToNext() NOTHROWS override;
    public:
        TAK::Engine::Util::TAKErr getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS override;
        TAK::Engine::Util::TAKErr getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getColumnCount(std::size_t *value) NOTHROWS override;

        TAK::Engine::Util::TAKErr getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getString(const char **value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getDouble(double *value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getType(FieldType *value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr isNull(bool *value, const std::size_t columnIndex) NOTHROWS override;
    public:
        TAK::Engine::Util::TAKErr bindBlob(const std::size_t idx, const uint8_t *blob, const std::size_t size) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindInt(const std::size_t idx, const int32_t value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindLong(const std::size_t idx, const int64_t value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindDouble(const std::size_t idx, const double value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindString(const std::size_t idx, const char *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr bindNull(const std::size_t idx) NOTHROWS override;
        TAK::Engine::Util::TAKErr clearBindings() NOTHROWS override;
    private:
        TAK::Engine::Util::TAKErr fillColumnNames() const NOTHROWS;
        TAK::Engine::Util::TAKErr validate() const NOTHROWS;
        TAK::Engine::Util::TAKErr validateIndex(std::size_t colIndex) const NOTHROWS;
        TAK::Engine::Util::TAKErr validate(std::size_t colIndex) const NOTHROWS;
    private :
        sqlite3_stmt* impl;
        std::size_t colCount;
        mutable std::vector<String> columnNames;
        bool valid;
    };

    template<class Iface, class Impl>
    void deleteImpl(const Iface *ptr);

    void
    throwDB_Error(int response,
                  const char* header,      // MEM_FN (mumble) ends with ' '
                  const char* doing)
        throw (DB_Error);

    void logDB_Error(int response, const char* header, const char* doing) NOTHROWS;
    TAK::Engine::Util::TAKErr prepareStatement(sqlite3_stmt **stmt, const char* sql, sqlite3* connection) NOTHROWS;
    TAK::Engine::Port::String catStrings(const char *a, const char *b) NOTHROWS;

#ifdef MSVC
    inline int strcasecmp(const char* lhs, const char* rhs)
    {
        return TAK::Engine::Port::String_strcasecmp(lhs, rhs);
    }
#endif

    bool dbdbg = false;
}

Database2::~Database2()
{}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_getColumnNames(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, Database2 &db, const char *tableName) NOTHROWS
{
    TAK::Engine::Util::TAKErr code;

	StringBuilder queryStr;
	code = StringBuilder_combine(queryStr, "PRAGMA table_info(", tableName, ")");
	CHECKRETURN_CODE(code);

    QueryPtr colResult(nullptr, nullptr);
    code = db.compileQuery(colResult, queryStr.c_str());
    CHECKRETURN_CODE(code);

    std::size_t nameCol;
    code = colResult->getColumnIndex(&nameCol, "name");
    CHECKRETURN_CODE(code);

    do {
        code = colResult->moveToNext();
        if (code != TE_Ok)
            break;

        const char *colName;
        code = colResult->getString(&colName, nameCol);
        if (code != TE_Ok)
            break;

        value.add(String(colName));
    } while (true);
    return (code==TE_Done) ? TE_Ok : code;
}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_getDatabaseFilePath(TAK::Engine::Port::String &value, Database2 &db) NOTHROWS
{
    TAK::Engine::Util::TAKErr code;
    QueryPtr dbResult(nullptr, nullptr);
    code = db.query(dbResult, "PRAGMA database_list");
    CHECKRETURN_CODE(code);

    do {
        code = dbResult->moveToNext();
        if (code != TE_Ok)
            break;

        const char *result;
        code = dbResult->getString(&result, 1);
        if (code != TE_Ok)
            break;
        if(!String_strcasecmp(result, "main"))
        {
            result = nullptr;
            code = dbResult->getString(&result, 2);
            if (code != TE_Ok)
                break;
            if (result) {
                 value = result;
                 break;
             }
        }
    } while (true);

    return (code==TE_Done) ? TE_Ok : code;
}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_getNextAutoincrementID(int64_t *value, Database2 &db, const char *table) NOTHROWS
{
    TAK::Engine::Util::TAKErr code;

    QueryPtr dbResult(nullptr, nullptr);
    code = db.compileQuery(dbResult, AUTOINCREMENT_QUERY);
    CHECKRETURN_CODE(code);

    code = dbResult->bindString(1, table);

    code = dbResult->moveToNext();
    if (code == TE_Ok) {
        code = dbResult->getLong(value, 0);
    }
    else if (code == TE_Done) {
        *value = 1LL;
    }

    return (code == TE_Done) ? TE_Ok : code;
}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_getTableNames(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &value, Database2 &db) NOTHROWS
{
    TAK::Engine::Util::TAKErr code;
    QueryPtr tableResult(nullptr, nullptr);
    code = db.query(tableResult, TABLE_NAME_QUERY);
    CHECKRETURN_CODE(code);

    const char *tblName;
    do
    {
        code = tableResult->moveToNext();
        TE_CHECKBREAK_CODE(code);
        code = tableResult->getString(&tblName, 0);
        TE_CHECKBREAK_CODE(code);
        code = value.add(TAK::Engine::Port::String(tblName));
        TE_CHECKBREAK_CODE(code);
    } while (true);

    return (code==TE_Done) ? TE_Ok : code;
}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_lastChangeCount(unsigned long *value, Database2 &db) NOTHROWS
{
    TAK::Engine::Util::TAKErr code;
    QueryPtr qResult(nullptr, nullptr);
    code = db.query(qResult, "SELECT changes()");
    CHECKRETURN_CODE(code);

    code = qResult->moveToNext();
    if (code == TE_Ok) {
        int32_t v;
        code = qResult->getInt(&v, 0);
        *value = (unsigned long)v;
    } else if (code == TE_Done) {
        *value = 0LL;
    }

    return (code == TE_Done) ? TE_Ok : code;
}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_lastInsertRowID(int64_t *value, Database2 &db) NOTHROWS
{
    TAK::Engine::Util::TAKErr code;
    QueryPtr qResult(nullptr, nullptr);
    code = db.query(qResult, "SELECT last_insert_rowid()");
    CHECKRETURN_CODE(code);

    code = qResult->moveToNext();
    if (code == TE_Ok) {
        code = qResult->getLong(value, 0);
    } else if(code == TE_Done) {
        *value = 0LL;
    }

    return (code == TE_Done) ? TE_Ok : code;
}

TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_openDatabase(DatabasePtr &db, const char* databaseFilePath, const bool readOnly) NOTHROWS
{
    return Databases_openDatabase(db, databaseFilePath, nullptr, 0u, readOnly);
}
TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_openDatabase(DatabasePtr &db, const char* databaseFilePath, const char *passphrase, const bool readOnly) NOTHROWS
{
    if(passphrase)
        return Databases_openDatabase(db, databaseFilePath, reinterpret_cast<const uint8_t *>(passphrase), passphrase ? strlen(passphrase) : 0u, readOnly);
    else
        return Databases_openDatabase(db, databaseFilePath, nullptr, 0u, readOnly);
    try {
        db = DatabasePtr(new SpatiaLiteDB(databaseFilePath, reinterpret_cast<const uint8_t *>(passphrase), passphrase ? strlen(passphrase) : 0u, readOnly), deleteImpl<Database2, SpatiaLiteDB>);
        return TE_Ok;
    }
    catch (DB_Error &e) {
        Logger::log(Logger::Error, "Failed to open database %s, message: %s", databaseFilePath, e.what());
        return TE_Err;
    }
}
TAK::Engine::Util::TAKErr TAK::Engine::DB::Databases_openDatabase(DatabasePtr &db, const char* databaseFilePath, const uint8_t *key, const std::size_t keylen, const bool readOnly) NOTHROWS
{
    try {
        db = DatabasePtr(new SpatiaLiteDB(databaseFilePath, key, keylen, readOnly), deleteImpl<Database2, SpatiaLiteDB>);
        return TE_Ok;
    }
    catch (DB_Error &e) {
        Logger::log(Logger::Error, "Failed to open database %s, message: %s", databaseFilePath, e.what());
        return TE_Err;
    }
}

void TAK::Engine::DB::Databases_enableDebug(bool v) NOTHROWS
{
    dbdbg = v;
}

namespace
{

    SpatiaLiteDB::SpatiaLiteDB(const char* filePath, const uint8_t *key, const std::size_t keylen, const bool ro) throw (DB_Error) :
        mutex(TEMT_Recursive),
        connection(nullptr),
        cache(nullptr),
        inTrans(false),
        successfulTrans(false),
        readOnly(ro)
    {
        try {
            //
            // If the supplied filePath is NULL, create a temporary database.
            //

            int response(sqlite3_open_v2(filePath ? filePath : "",
                &connection,
                (readOnly ? SQLITE_OPEN_READONLY :
                SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE),
                nullptr));

            if (response != SQLITE_OK)
            {
                closeConnection();
                std::string msg;
                if(filePath) {
                    std::ostringstream strm;
                    strm << "opening database at " << filePath;
                    msg = strm.str();
                } else {
                    msg = "opening a temporary database";
                }
                throwDB_Error(response,
                    MEM_FN("SpatiaLiteDB"),
                    msg.c_str());
            }

            if(key) {
#ifdef SQLITE_HAS_CODEC
                sqlite3_key(connection, key, keylen);
                response = sqlite3_exec(connection, "SELECT count(*) FROM sqlite_master;", NULL, NULL, NULL);
                if(response != SQLITE_OK) {
                    throwDB_Error(response,
                        MEM_FN("SpatiaLiteDB"),
                        "setting passphrase on database");
                }
#else
                Logger_log(TELL_Warning, "SQLite codec not available, ignoring passphrase");
#endif
            }

            cache = spatialite_alloc_connection();
            spatialite_init_ex(connection, cache, false);
        }
        catch (...)
        {
            throw DB_Error(MEM_FN("SpatiaLiteDB") "Caught unknown exception");
        }
    }

    SpatiaLiteDB::~SpatiaLiteDB()
    {
        closeConnection();
    }

    void SpatiaLiteDB::closeConnection() NOTHROWS
    {
        TAKErr code(TE_Ok);

        Lock lock(mutex);
        code = lock.status;
        if (code == TE_Ok) {
            if (connection)
            {
                int response(sqlite3_close_v2(connection));

                connection = nullptr;
                if (response != SQLITE_OK)
                {
                    Logger::log(Logger::Error, MEM_FN("closeConnection")
                        "Error (%s) closing connection", sqlite3_errstr(response));
                }
                spatialite_cleanup_ex(cache);
                cache = nullptr;
            }
        } else {
            Logger::log(Logger::Error, MEM_FN("closeConnection") "Error on lock");

            //
            // Make a last-ditch effort.
            //

            sqlite3_close_v2(connection);
            connection = nullptr;
            spatialite_cleanup_ex(cache);
            cache = nullptr;
        }
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::execute(const char *sql, const char **args, const std::size_t len) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        StatementPtr stmt(nullptr, nullptr);
        code = compileStatement(stmt, sql);
        CHECKRETURN_CODE(code);

        for (std::size_t i(0); i < len; ++i)
        {
            code = stmt->bindString(i + 1, args[i]);
            if (code != TE_Ok)
                break;
        }
        CHECKRETURN_CODE(code);
        return stmt->execute();
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::query(QueryPtr &result, const char *sql) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        QueryPtr retval(nullptr, nullptr);
        code = compileQuery(retval, sql);
        CHECKRETURN_CODE(code);

        result = QueryPtr(retval.release(), deleteImpl<Query, QueryImpl>);
        return code;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::compileStatement(StatementPtr &result, const char *sql) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (dbdbg) Logger::log(Logger::Info, "SpatiaLiteDB::compileStatement(%s)", sql);

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        sqlite3_stmt *stmt(nullptr);

        code = prepareStatement(&stmt, sql, connection);
        CHECKRETURN_CODE(code);
        result = StatementPtr(new StatementImpl(stmt), deleteImpl<Statement2, StatementImpl>);

        return code;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::compileQuery(QueryPtr &result, const char *sql) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (dbdbg) Logger::log(Logger::Info, "SpatiaLiteDB::compileQuery(%s)", sql);

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        sqlite3_stmt *stmt(nullptr);

        code = prepareStatement(&stmt, sql, connection);
        CHECKRETURN_CODE(code);
        result = QueryPtr(new QueryImpl(stmt), deleteImpl<Query, QueryImpl>);
        return code;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::isReadOnly(bool *value) NOTHROWS
    {
        // XXX -
        *value = false;
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::getVersion(int *value) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        QueryPtr cursor(nullptr, nullptr);
        code = query(cursor, "PRAGMA user_version");
        CHECKRETURN_CODE(code);

        code = cursor->moveToNext();
        if (code != TE_Ok)
        {
            Logger::log(Logger::Error, MEM_FN("getVersion") "No version set");
            return code;
        }

        return cursor->getInt(value, 0);
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::setVersion(const int version) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        StatementPtr stmt(nullptr, nullptr);

		StringBuilder stmtStr;
		code = StringBuilder_combine(stmtStr, "PRAGMA user_version = ", version);
		CHECKRETURN_CODE(code);

        code = compileStatement(stmt, stmtStr.c_str());
        CHECKRETURN_CODE(code);

        return stmt->execute();
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::beginTransaction() NOTHROWS
    {
        TAKErr code(TE_Ok);

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        if (inTrans)
        {
            Logger::log(Logger::Error, MEM_FN("beginTranscaction") "Already in a transaction");
            return TE_Err;
        }
        code = execute("BEGIN EXCLUSIVE", nullptr, 0);
        if (code == TE_Ok)
            inTrans = true;
        return code;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::setTransactionSuccessful() NOTHROWS
    {
        TAKErr code(TE_Ok);

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);


        if (!inTrans)
        {
            Logger::log(Logger::Error, MEM_FN("setTransactionSuccessful")
                "Not in a transaction");
            return TE_Err;
        }

        successfulTrans = true;
        return code;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::endTransaction() NOTHROWS
    {
        TAKErr code(TE_Ok);

        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        if (!inTrans)
        {
            Logger::log(Logger::Error, MEM_FN("endTransaction") "Not in a transaction");
            return TE_Err;
        }

        // XXX - do not believe this is necessary and is causing crashes
        //       with other concurrent access on 'sqlite3_stmt' instance
        //       (specifically, invocation of sqlite3_column_int64).
#if 0
        //
        // Must reset any busy statements before committing or rolling back.
        //

        for (sqlite3_stmt* next(sqlite3_next_stmt(connection, NULL));
            next;
            next = sqlite3_next_stmt(connection, next))
        {
            if (sqlite3_stmt_busy(next))
            {
                int response(sqlite3_reset(next));

                if (response != SQLITE_OK)
                {
                    std::ostringstream strm;
                    strm << MEM_FN("endTransaction")
                            << "Error ("
                            << sqlite3_errstr(response)
                            << ") "
                            << "resetting busy statement: "
                            << sqlite3_sql(next);

                    Logger::log(Logger::Error, strm.str().c_str());
                    return TE_Err;
                }
            }
        }
#endif
        execute(successfulTrans ? "COMMIT" : "ROLLBACK", nullptr, 0);
        inTrans = successfulTrans = false;
        return code;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::inTransaction(bool *value) NOTHROWS
    {
        *value = inTrans;
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr SpatiaLiteDB::getErrorMessage(TAK::Engine::Port::String &value) NOTHROWS {
        const char *errmsg = sqlite3_errmsg(this->connection);
        if (errmsg) {
            value = errmsg;
        }
        return TAK::Engine::Util::TE_Ok;
    }

    /*************************************************************************/
    // Query Implementation

    QueryImpl::QueryImpl(sqlite3_stmt* stmt) NOTHROWS :
        impl(stmt),
        colCount(sqlite3_column_count(impl)),
        valid(false)
    { }

    QueryImpl::~QueryImpl() NOTHROWS
    {
        int response(sqlite3_finalize(impl));

        if (response != SQLITE_OK)
        {
            Logger::log(Logger::Warning,  MEM_FN("QueryImpl::~QueryImpl")
                "Error (%s) finalizing statement: %s", sqlite3_errstr(response),
                sqlite3_sql(impl));
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::moveToNext() NOTHROWS
    {
        int response(sqlite3_step(impl));

        TAK::Engine::Util::TAKErr code;
        switch (response)
        {
        case SQLITE_ROW:  valid = true;   code = TE_Ok;   break;
        case SQLITE_DONE: valid = false;  code = TE_Done; break;

        default:
            Logger::log(Logger::Error, MEM_FN("QueryImpl::moveToNext")
                        "Error (%s) %s", sqlite3_errstr(response), "stepping cursor");
            code = TE_Err;
        }

        return code;
    }

    TAK::Engine::Util::TAKErr QueryImpl::getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS
    {
        if (!columnName) {
            Logger::log(Logger::Error, MEM_FN("getColumnIndex")
                "Received NULL column name");
            return TE_InvalidArg;
        }

        if (columnNames.empty()) {
            TAK::Engine::Util::TAKErr code = fillColumnNames();
            CHECKRETURN_CODE(code);
        }

        std::size_t colIndex(0);

        while (colIndex < columnNames.size()
            && ::strcasecmp(columnNames[colIndex], columnName))
        {
            colIndex++;
        }
        if (colIndex == columnNames.size())
        {
            Logger::log(Logger::Error,
                (MEM_FN("getColumnIndex")
                "Unknown column name: %s",
                columnName));
            return TE_InvalidArg;
        }
        *value = colIndex;
        return TE_Ok;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getColumnName(const char **value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        code = validateIndex(columnIndex);
        CHECKRETURN_CODE(code);

        *value = columnNames.empty()
            ? sqlite3_column_name(impl, static_cast<int>(columnIndex))
            : static_cast<const char*> (columnNames[columnIndex]);
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getColumnCount(std::size_t *value) NOTHROWS
    {
        *value = colCount;
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr QueryImpl::getBlob(const uint8_t **value, std::size_t *len, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        code = validate(columnIndex);
        CHECKRETURN_CODE(code);

        *value =
            (static_cast<const unsigned char*> (sqlite3_column_blob(impl, static_cast<int>(columnIndex))));
        *len = sqlite3_column_bytes(impl, static_cast<int>(columnIndex));
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getString(const char **value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        code = validate(columnIndex);
        CHECKRETURN_CODE(code);
        *value = reinterpret_cast<const char *>(sqlite3_column_text(impl, static_cast<int>(columnIndex)));
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getInt(int32_t *value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code = validate(columnIndex);
        CHECKRETURN_CODE(code);
        *value = sqlite3_column_int(impl, static_cast<int>(columnIndex));
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getLong(int64_t *value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code = validate(columnIndex);
        CHECKRETURN_CODE(code);
        *value = sqlite3_column_int64(impl, static_cast<int>(columnIndex));
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getDouble(double *value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code = validate(columnIndex);
        CHECKRETURN_CODE(code);
        *value = sqlite3_column_double(impl, static_cast<int>(columnIndex));
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::getType(FieldType *value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        code = validateIndex(columnIndex);
        CHECKRETURN_CODE(code);

        FieldType type(TEFT_Null);

        switch (sqlite3_column_type(impl, static_cast<int>(columnIndex)))
        {
        case SQLITE_INTEGER:      type = TEFT_Integer;   break;
        case SQLITE_FLOAT:        type = TEFT_Float;     break;
        case SQLITE_BLOB:         type = TEFT_Blob;      break;
        case SQLITE3_TEXT:        type = TEFT_String;    break;
        case SQLITE_NULL:         type = TEFT_Null;      break;

        default:

            Logger::log(Logger::Error, MEM_FN("QueryImpl::getType") "Unknown column type");
            return TE_Err;
        }

        *value = type;
        return code;
    }
    TAK::Engine::Util::TAKErr QueryImpl::isNull(bool *value, const std::size_t columnIndex) NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        code = validateIndex(columnIndex);
        CHECKRETURN_CODE(code);
        *value = (sqlite3_column_type(impl, static_cast<int>(columnIndex)) == SQLITE_NULL);
        return code;
    }

    TAK::Engine::Util::TAKErr QueryImpl::fillColumnNames() const NOTHROWS
    {
        columnNames.reserve(colCount);
        for (int i(0); i < static_cast<int>(colCount); ++i)
        {
            columnNames.push_back(sqlite3_column_name(impl, i));
        }
        return TE_Ok;
    }

    TAK::Engine::Util::TAKErr QueryImpl::validate() const NOTHROWS
    {
        if (!valid)
        {
            Logger::log(Logger::Error, (MEM_FN("QueryImpl::validate")
                "Invalid cursor state"));
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::validateIndex(std::size_t colIndex) const NOTHROWS
    {
        if (colIndex >= colCount)
        {
            Logger::log(Logger::Error, (MEM_FN("QueryImpl::validateIndex")
                "Column index out of range"));
            return TE_BadIndex;
        } else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::validate(std::size_t colIndex) const NOTHROWS
    {
        TAK::Engine::Util::TAKErr code;
        code = validate();
        CHECKRETURN_CODE(code);
        code = validateIndex(colIndex);
        return code;
    }

    /*************************************************************************/
    // Statement Implementation

    StatementImpl::StatementImpl(sqlite3_stmt *stmt) NOTHROWS:
        impl(stmt)
    {}

    StatementImpl::~StatementImpl() NOTHROWS
    {
        int response(sqlite3_finalize(impl));

        if (response != SQLITE_OK)
        {
            Logger::log(Logger::Error, MEM_FN("StatementImpl::~StatementImpl")
                "Error (%s) finalizing statement: %s", sqlite3_errstr(response),
                sqlite3_sql(impl));
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::bindString(const std::size_t index, const char* value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindString(%d, %s)", index, value);
        int response(sqlite3_bind_text(impl, static_cast<int>(index), value,
            value ? static_cast<int>(std::strlen(value)) : 0,
            SQLITE_TRANSIENT));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding text parameter ", index, " to: ", (value ? value : "NULL"));

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::bindDouble(const std::size_t index, const double value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindDouble(%d, %lf)", index, value);

        int response(sqlite3_bind_double(impl, static_cast<int>(index), value));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding double parameter ", index, " to: ", value);

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::bindInt(const std::size_t index, const int32_t value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindInt(%d, %d)", index, value);
        int response(sqlite3_bind_int(impl, static_cast<int>(index), value));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding integer parameter ", index, " to: ", value);

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::bindLong(const std::size_t index, const int64_t value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindDouble(%d, %.0lf)", index, (double)value);
        int response(sqlite3_bind_int64(impl, static_cast<int>(index), value));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
            StringBuilder_combine(logStr, "binding long parameter ", index, " to: ", value);

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::bindBlob(const std::size_t index, const uint8_t *value, const std::size_t len) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindBlob(%d, %p, %d)", index, value, len);
        int response(sqlite3_bind_blob(impl, static_cast<int>(index), value,
            static_cast<int>(len),
            SQLITE_TRANSIENT));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding blob parameter ", index);
            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::bindNull(const std::size_t index) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindNull(%d)", index);
        int response(sqlite3_bind_null(impl, static_cast<int>(index)));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding NULL to parameter ", index);
            logDB_Error(response, MEM_FN("StatementImpl::bindNULL"), logStr.c_str());
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }


    TAK::Engine::Util::TAKErr StatementImpl::clearBindings() NOTHROWS
    {
        int response(sqlite3_clear_bindings(impl));

        if (response != SQLITE_OK)
        {
            logDB_Error(response, MEM_FN("StatementImpl::clearBindings"),
                "clearing parameter bindings");
            return TE_Err;
        }
        else {

#if 1
            // for consistency with the Java API, we'll reset prior to every execute
            int resetResponse(sqlite3_reset(impl));

            if (resetResponse != SQLITE_OK)
            {
                logDB_Error(resetResponse, MEM_FN("StatementImpl::clearBindings"),
                    "resetting statement");
                return TE_Err;
            }
#endif
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr StatementImpl::execute() NOTHROWS
    {
#if 1
        // for consistency with the Java API, we'll reset prior to every execute
        int resetResponse(sqlite3_reset(impl));

        if (resetResponse != SQLITE_OK)
        {
            logDB_Error(resetResponse, MEM_FN("StatementImpl::execute"), "resetting statement");
            return TE_Err;
        }
#endif

        int response(sqlite3_step(impl));

        if (response == SQLITE_ROW)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, MEM_FN("StatementImpl::execute"), "Execution of statement has result for: ", sqlite3_sql(impl));
            Logger::log(Logger::Warning, logStr.c_str());
            return TE_Ok;
        } else if (response != SQLITE_DONE) {
            logDB_Error(response, MEM_FN("StatementImpl::execute"),
                catStrings("executing statement: ",
                sqlite3_sql(impl)));
            return TE_Err;
        } else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::bindString(const std::size_t index, const char* value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "QueryImpl::bindString(%d, %s)", index, value);
        int response(sqlite3_bind_text(impl, static_cast<int>(index), value,
        static_cast<int>(value ? std::strlen(value) : 0),
        SQLITE_TRANSIENT));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding text parameter ", index, " to: ", (value ? value : "NULL"));

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::bindDouble(const std::size_t index, const double value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "QueryImpl::bindDouble(%d, %lf)", index, value);
        int response(sqlite3_bind_double(impl, static_cast<int>(index), value));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding double parameter ", index, " to: ", value);

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::bindInt(const std::size_t index, const int32_t value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "QueryImpl::bindInt(%d, %d)", index, value);
        int response(sqlite3_bind_int(impl, static_cast<int>(index), value));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding integer parameter ", index, " to: ", value);

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::bindLong(const std::size_t index, const int64_t value) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "QueryImpl::bindLong(%d, %.0lf)", index, (double)value);
        int response(sqlite3_bind_int64(impl, static_cast<int>(index), value));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding long parameter ", index, " to: ", value);

            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::bindBlob(const std::size_t index, const uint8_t *value, const std::size_t len) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "QueryImpl::bindBlob(%d, %p, %d)", index, value, len);
        int response(sqlite3_bind_blob(impl, static_cast<int>(index), value,
        static_cast<int>(len),
        SQLITE_TRANSIENT));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding blob parameter ", index);
            logDB_Error(response, MEM_FN("StatementImpl::bind"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::bindNull(const std::size_t index) NOTHROWS
    {
        if (dbdbg) Logger::log(Logger::Info, "StatementImpl::bindNull(%d)", index);
        int response(sqlite3_bind_null(impl, static_cast<int>(index)));

        if (response != SQLITE_OK)
        {
			StringBuilder logStr;
			StringBuilder_combine(logStr, "binding NULL to parameter ", index);
            logDB_Error(response, MEM_FN("StatementImpl::bindNULL"), logStr.c_str());
            return TE_Err;
        }
        else {
            return TE_Ok;
        }
    }

    TAK::Engine::Util::TAKErr QueryImpl::clearBindings() NOTHROWS
    {
        int response(sqlite3_clear_bindings(impl));

        if (response != SQLITE_OK)
        {
            logDB_Error(response, MEM_FN("StatementImpl::clearBindings"),
                "clearing parameter bindings");
            return TE_Err;
        }
        else {

#if 1
            // for consistency with the Java API, we'll reset prior to every execute
            int resetResponse(sqlite3_reset(impl));

            if (resetResponse != SQLITE_OK)
            {
                logDB_Error(resetResponse, MEM_FN("StatementImpl::clearBindings"),
                    "resetting statement");
                return TE_Err;
            }
#endif
            return TE_Ok;
        }
    }

    /*************************************************************************/

    template<class Iface, class Impl>
    void deleteImpl(const Iface *ptr)
    {
        if (!ptr) return;
        const Impl *impl = static_cast<const Impl *>(ptr);
        delete impl;
    }

    void throwDB_Error(int response, const char* header, const char* doing) throw (DB_Error)
    {
		StringBuilder logStr;
		StringBuilder_combine(logStr, header, "Error (", sqlite3_errstr(response), ") ", doing);

        switch (response)
        {
        case SQLITE_BUSY:

            throw DB_Busy(logStr.c_str());
            break;

        case SQLITE_INTERRUPT:

            throw DB_Interrupted(logStr.c_str());
            break;

        default:

            throw DB_Error(logStr.c_str());
        }
    }

    void logDB_Error(int response, const char* header, const char* doing) NOTHROWS
    {
		StringBuilder logStr;
		StringBuilder_combine(logStr, header, "Error (", sqlite3_errstr(response), ") ", doing);

        Logger::log(Logger::Error, logStr.c_str());
    }

    TAK::Engine::Util::TAKErr prepareStatement(sqlite3_stmt **stmt, const char* sql, sqlite3* connection) NOTHROWS
    {
        if (!sql)
        {
            Logger::log(Logger::Error, MEM_FN("prepareStatement")
                "Received NULL SQL string");
            return TE_InvalidArg;
        }

        sqlite3_stmt* statement(nullptr);
        int response(sqlite3_prepare_v2(connection,
            sql, static_cast<int>(std::strlen(sql) + 1),
            &statement, nullptr));

        if (response != SQLITE_OK || !statement)
        {
            sqlite3_finalize(statement);   // Statement should be NULL anyways.
            logDB_Error(response, MEM_FN("prepareStatement"),
                catStrings("preparing statement: ",
                sql));
            return TE_Err;
        }

        *stmt = statement;
        return TE_Ok;
    }
    TAK::Engine::Port::String catStrings(const char *a, const char *b) NOTHROWS
    {
        std::ostringstream strm;
        strm << a << b;
        return strm.str().c_str();
    }
}
