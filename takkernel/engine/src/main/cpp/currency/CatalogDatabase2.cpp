#include "currency/CatalogDatabase2.h"

#include <algorithm>
#include <sstream>

#include "currency/Currency2.h"
#include "currency/CurrencyRegistry2.h"
#include "db/Query.h"
#include "db/Statement2.h"
#include "port/STLVectorAdapter.h"
#include "thread/Lock.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Logging.h"
#include "db/DatabaseFactory.h"

using namespace TAK::Engine::Currency;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

#define CATALOG_VERSION 2

#define TBL_CATALOG           "catalog"
#define TBL_CATALOG_METADATA  "catalog_metadata"

#define COL_CATALOG_ID           "id"
#define COL_CATALOG_PATH         "path"
#define COL_CATALOG_SYNC         "sync"
#define COL_CATALOG_APP_VERSION  "appversion"
#define COL_CATALOG_APP_DATA     "appdata"
#define COL_CATALOG_APP_NAME     "appname"

#define COL_CATALOG_METADATA_KEY     "key"
#define COL_CATALOG_METADATA_VALUE   "value"

#define DEFAULT_PATH_CATALOG_CURSOR_QUERY \
    "SELECT " COL_CATALOG_PATH ", " \
    COL_CATALOG_SYNC ", " \
    COL_CATALOG_APP_VERSION ", " \
    COL_CATALOG_APP_DATA ", " \
    COL_CATALOG_APP_NAME " FROM " \
    TBL_CATALOG " WHERE " COL_CATALOG_PATH " = ?"

#define DEFAULT_CURRENCY_CATALOG_CURSOR_QUERY \
    "SELECT " COL_CATALOG_PATH ", " \
    COL_CATALOG_SYNC ", " \
    COL_CATALOG_APP_VERSION ", " \
    COL_CATALOG_APP_DATA ", " \
    COL_CATALOG_APP_NAME " FROM " \
    TBL_CATALOG " WHERE " COL_CATALOG_APP_NAME " = ?"

const char * const CatalogDatabase2::TABLE_CATALOG = TBL_CATALOG;
const char * const CatalogDatabase2::TABLE_CATALOG_METADATA = TBL_CATALOG_METADATA;

const char * const CatalogDatabase2::COLUMN_CATALOG_ID = COL_CATALOG_ID;
const char * const CatalogDatabase2::COLUMN_CATALOG_PATH = COL_CATALOG_PATH;
const char * const CatalogDatabase2::COLUMN_CATALOG_SYNC = COL_CATALOG_SYNC;
const char * const CatalogDatabase2::COLUMN_CATALOG_APP_VERSION = COL_CATALOG_APP_VERSION;
const char * const CatalogDatabase2::COLUMN_CATALOG_APP_DATA = COL_CATALOG_APP_DATA;
const char * const CatalogDatabase2::COLUMN_CATALOG_APP_NAME = COL_CATALOG_APP_NAME;

const char * const CatalogDatabase2::COLUMN_CATALOG_METADATA_KEY = COL_CATALOG_METADATA_KEY;
const char * const CatalogDatabase2::COLUMN_CATALOG_METADATA_VALUE = COL_CATALOG_METADATA_VALUE;

namespace
{
    TAKErr isValidApp(bool *value, CatalogDatabase2::CatalogCursor &result, CatalogCurrency2 *currency);
    
    // Transforms the COLUM_CATALOG_PATH value into the runtime path version (used for TE_SHOULD_ADAPT_STORAGE_PATH == 1)
    class StoragePathAdapterCatalogCursor : public CatalogDatabase2::CatalogCursor
    {
    public:
        StoragePathAdapterCatalogCursor(TAK::Engine::DB::QueryPtr &&cursor) NOTHROWS;
        TAK::Engine::Util::TAKErr moveToNext() NOTHROWS override;
        ~StoragePathAdapterCatalogCursor() NOTHROWS override;
        TAK::Engine::Util::TAKErr getString(const char **value, const std::size_t columnIndex) NOTHROWS override;
        TAK::Engine::Util::TAKErr getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS override;
    private:
        TAK::Engine::Port::String currentRuntimePath;
        std::size_t currentPathColumnIndex;
    };

}

/**************************************************************************/

static const char * const TABLE_CATALOG = "catalog";
static const char * const TABLE_CATALOG_METADATA = "catalog_metadata";

static const char * const COLUMN_CATALOG_ID = "id";
static const char * const COLUMN_CATALOG_PATH = "path";
static const char * const COLUMN_CATALOG_SYNC = "sync";
static const char * const COLUMN_CATALOG_APP_VERSION = "appversion";
static const char * const COLUMN_CATALOG_APP_DATA = "appdata";
static const char * const COLUMN_CATALOG_APP_NAME = "appname";

static const char * const COLUMN_CATALOG_METADATA_KEY = "key";
static const char * const COLUMN_CATALOG_METADATA_VALUE = "value";

CatalogDatabase2::CatalogDatabase2(CatalogCurrencyRegistry2 &currencyRegistry_) NOTHROWS :
    database(nullptr, nullptr),
    currencyRegistry(currencyRegistry_),
    updateCatalogEntrySyncStmt(nullptr, nullptr),
    mutex(TEMT_Recursive)
{}

CatalogDatabase2::~CatalogDatabase2() NOTHROWS
{}

TAKErr CatalogDatabase2::open(const char *databasePath) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    DatabasePtr db(nullptr, nullptr);
    DatabaseInformation info(databasePath, nullptr, DATABASE_OPTIONS_READONLY);
    code = DatabaseFactory_create(db, info);
    TE_CHECKRETURN_CODE(code);

    return this->openImpl(std::move(db));
}

TAKErr CatalogDatabase2::openImpl(DatabasePtr &&db) NOTHROWS
{
    TAKErr code;
    this->database = std::move(db);

    std::vector<Port::String> tableNames;
    Port::STLVectorAdapter<Port::String> tableNamesAdapter(tableNames);
    code = Databases_getTableNames(tableNamesAdapter, *this->database);
    TE_CHECKRETURN_CODE(code);

    bool create = ((std::find(tableNames.begin(), tableNames.end(), Port::String(TABLE_CATALOG))==tableNames.end())
        || (std::find(tableNames.begin(), tableNames.end(), Port::String(TABLE_CATALOG_METADATA))==tableNames.end()));

    bool versionValid;
    code = this->checkDatabaseVersion(&versionValid);
    TE_CHECKRETURN_CODE(code);
    if (!versionValid) {
        code = this->dropTables();
        TE_CHECKRETURN_CODE(code);
        create = true;
    }

    if (create) {
        bool readOnly = false;
        code = this->database->isReadOnly(&readOnly);
        TE_CHECKRETURN_CODE(code);
        if (readOnly) {
            Logger::log(Logger::Error, "Database is read-only");
            return TE_InvalidArg;
        }

        code = this->buildTables();
        TE_CHECKRETURN_CODE(code);
        code = this->setDatabaseVersion();
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr CatalogDatabase2::checkDatabaseVersion(bool *value) NOTHROWS
{
    int version;
    TAKErr code = this->database->getVersion(&version);
    TE_CHECKRETURN_CODE(code);
    *value = (version == CATALOG_VERSION);
    return code;
}

TAKErr CatalogDatabase2::setDatabaseVersion() NOTHROWS
{
    return this->database->setVersion(CATALOG_VERSION);
}

TAKErr CatalogDatabase2::dropTables() NOTHROWS
{
    TAKErr code;
    code = this->database->execute("DROP TABLE IF EXISTS " TBL_CATALOG, nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database->execute("DROP TABLE IF EXISTS " TBL_CATALOG_METADATA, nullptr, 0);
    return code;
}

TAKErr CatalogDatabase2::buildTables() NOTHROWS
{
    return this->createCatalogTable();
}

TAKErr CatalogDatabase2::createCatalogTable() NOTHROWS
{
    TAKErr code;
    code = this->database->execute("CREATE TABLE " TBL_CATALOG " ("
        COL_CATALOG_ID " INTEGER PRIMARY KEY AUTOINCREMENT, "
        COL_CATALOG_PATH " TEXT, "
        COL_CATALOG_SYNC " INTEGER, "
        COL_CATALOG_APP_VERSION " INTEGER, "
        COL_CATALOG_APP_DATA " BLOB, "
        COL_CATALOG_APP_NAME " TEXT)", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database->execute("CREATE TABLE " TBL_CATALOG_METADATA " ("
        COL_CATALOG_METADATA_KEY " TEXT, "
        COL_CATALOG_METADATA_VALUE " TEXT)", nullptr, 0);
    return code;
}

TAKErr CatalogDatabase2::onCatalogEntryMarkedValid(int64_t catalogId) NOTHROWS
{
    return TE_Ok;
}

TAKErr CatalogDatabase2::onCatalogEntryRemoved(int64_t catalogId, bool automated) NOTHROWS
{
    return TE_Ok;
}

TAKErr CatalogDatabase2::onCatalogEntryAdded(int64_t catalogId) NOTHROWS
{
    return TE_Ok;
}

TAKErr CatalogDatabase2::addCatalogEntry(int64_t *rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    return this->addCatalogEntryNoSync(rowId, derivedFrom, currency);
}

TAKErr CatalogDatabase2::addCatalogEntryNoSync(int64_t *rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS
{
    TAKErr code;


    StatementPtr stmt(nullptr, nullptr);

    code = this->database->compileStatement(stmt, "INSERT INTO " TBL_CATALOG
        " (" COL_CATALOG_PATH ", "
        COL_CATALOG_SYNC ", "
        COL_CATALOG_APP_NAME ", "
        COL_CATALOG_APP_VERSION ", "
        COL_CATALOG_APP_DATA ") "
        "VALUES (?, ?, ?, ?, ?)");
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(derivedFrom, storagePath, code);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, storagePath);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(2, 0);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, currency.getName());
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(4, currency.getAppVersion());
    TE_CHECKRETURN_CODE(code);
    CatalogCurrency2::AppDataPtr appData(nullptr, nullptr);
    code = currency.getAppData(appData, derivedFrom);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindBlob(5, appData->value, appData->length);
    appData.reset();
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    stmt.reset();

    code = Databases_lastInsertRowID(rowId, *this->database);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr CatalogDatabase2::updateCatalogEntry(const int64_t rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS {
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    return this->updateCatalogEntryNoSync(rowId, derivedFrom, currency);
}

TAKErr CatalogDatabase2::updateCatalogEntryNoSync(const int64_t rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS {
    TAKErr code;

    StatementPtr stmt(nullptr, nullptr);

    code = this->database->compileStatement(stmt, "UPDATE " TBL_CATALOG " SET " COL_CATALOG_PATH " = ?, " COL_CATALOG_SYNC " = ?, " COL_CATALOG_APP_NAME
                                                  " = ?, " COL_CATALOG_APP_VERSION " = ?, " COL_CATALOG_APP_DATA
                                                  " = ? WHERE " COL_CATALOG_ID " = ?");
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(derivedFrom, storagePath, code);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, storagePath);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(2, 0);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, currency.getName());
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(4, currency.getAppVersion());
    TE_CHECKRETURN_CODE(code);
    CatalogCurrency2::AppDataPtr appData(nullptr, nullptr);
    code = currency.getAppData(appData, derivedFrom);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindBlob(5, appData->value, appData->length);
    appData.reset();
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(6, rowId);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    stmt.reset();

    return code;
}

TAKErr CatalogDatabase2::validateCatalog() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    CatalogCursorPtr result(nullptr, nullptr);
    code = this->queryCatalog(result);
    TE_CHECKRETURN_CODE(code);
    return this->validateCatalogNoSync(*result);
}

TAKErr CatalogDatabase2::validateCatalogApp(const char *appName) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    CatalogCursorPtr result(nullptr, nullptr);
    code = this->queryCatalogApp(result, appName);
    TE_CHECKRETURN_CODE(code);
    return this->validateCatalogNoSync(*result);
}

TAKErr CatalogDatabase2::validateCatalogPath(const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    CatalogCursorPtr result(nullptr, nullptr);
    code = this->queryCatalogPath(result, file);
    TE_CHECKRETURN_CODE(code);
    return this->validateCatalogNoSync(*result);
}

TAKErr CatalogDatabase2::validateCatalogNoSync(CatalogCursor &result) NOTHROWS
{
    TAKErr code;
    bool inTrans;
    code = this->database->inTransaction(&inTrans);
    TE_CHECKRETURN_CODE(code);

    const bool createTransaction = !inTrans;

    std::unique_ptr<Database2::Transaction> transaction(nullptr);

    if (createTransaction) {
        transaction.reset(new Database2::Transaction(*this->database.get()));
        if (!transaction->isValid())
            return TE_Err;
    }

    
    const char *file;
    do {
        code = result.moveToNext();
        TE_CHECKBREAK_CODE(code);

        bool validApp;
        code = validateCatalogRowNoSync(&validApp, result);
        TE_CHECKBREAK_CODE(code);

        // we don't check for the code here -- if we get a bad code we are
        // assuming the entry is not valid

        if (validApp)
        {
            // the entry is valid
            continue;
        }

        // the entry is not valid; remove it
        code = result.getPath(&file);
        TE_CHECKBREAK_CODE(code);
        code = this->deleteCatalogPath(file, true);
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    if (createTransaction) {
        code = this->database->setTransactionSuccessful();
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr CatalogDatabase2::validateCatalogRowNoSync(bool *value, CatalogCursor &row) NOTHROWS
{
    CatalogCurrency2 *currency = nullptr;
    TAKErr code;

    const char *appName;
    code = row.getAppName(&appName);
    if (code == TE_Ok)
        currency = this->currencyRegistry.getCurrency(appName);
    else
        currency = nullptr;

    code = isValidApp(value, row, currency);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr CatalogDatabase2::markCatalogEntryValid(const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->updateCatalogEntrySyncStmt.get()) {
        code = this->database->compileStatement(this->updateCatalogEntrySyncStmt, "UPDATE "
            TBL_CATALOG " SET sync = ? WHERE path = ?");
        TE_CHECKRETURN_CODE(code);
    }

    code = this->updateCatalogEntrySyncStmt->bindInt(1, 0);
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(file, storagePath, code);
    TE_CHECKRETURN_CODE(code);
    code = this->updateCatalogEntrySyncStmt->bindString(2, storagePath);
    TE_CHECKRETURN_CODE(code);

    code = this->updateCatalogEntrySyncStmt->execute();
    TE_CHECKRETURN_CODE(code);
    code = this->updateCatalogEntrySyncStmt->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = this->onCatalogEntryMarkedValid(-1);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr CatalogDatabase2::queryCatalog(CatalogCursorPtr &cursor) NOTHROWS
{
    TAKErr code;
    QueryPtr impl(nullptr, nullptr);
    code = this->database->query(impl, "SELECT * FROM " TBL_CATALOG);
    TE_CHECKRETURN_CODE(code);

#if TE_SHOULD_ADAPT_STORAGE_PATHS
    cursor = CatalogCursorPtr(new StoragePathAdapterCatalogCursor(std::move(impl)), deleteCatalogCursor);
#else
    cursor = CatalogCursorPtr(new CatalogCursor(std::move(impl)), deleteCatalogCursor);
#endif
    return code;
}

TAKErr CatalogDatabase2::queryCatalogPath(CatalogCursorPtr &cursor, const char *path) NOTHROWS
{
    TAKErr code;
    TE_GET_STORAGE_PATH_CODE(path, storagePath, code);
    TE_CHECKRETURN_CODE(code);
    return this->queryRawCatalog(cursor, DEFAULT_PATH_CATALOG_CURSOR_QUERY, storagePath);
}

TAKErr CatalogDatabase2::queryCatalogApp(CatalogCursorPtr &cursor, const char *appName) NOTHROWS
{
    return this->queryRawCatalog(cursor, DEFAULT_CURRENCY_CATALOG_CURSOR_QUERY, appName);
}

TAKErr CatalogDatabase2::queryRawCatalog(CatalogCursorPtr &cursor, const char *rawQuery, const char *arg) NOTHROWS
{
    const char *args[1];
    args[0] = arg;
    
    TAKErr code;
    QueryPtr impl(nullptr, nullptr);
    code = this->database->compileQuery(impl, rawQuery);
    TE_CHECKRETURN_CODE(code);

    if (arg) {
        code = impl->bindString(1, arg);
        TE_CHECKRETURN_CODE(code);
    }
    
#if TE_SHOULD_ADAPT_STORAGE_PATHS
    cursor = CatalogCursorPtr(new StoragePathAdapterCatalogCursor(std::move(impl)), deleteCatalogCursor);
#else
    cursor = CatalogCursorPtr(new CatalogCursor(std::move(impl)), deleteCatalogCursor);
#endif
    return code;
}

TAKErr CatalogDatabase2::query(QueryPtr &cursor, const char *table, const char **columns, const std::size_t numCols, const char *selection,
        const char **selectionArgs, const std::size_t numArgs, const char *groupBy, const char *having, const char *orderBy, const char *limit) NOTHROWS
{

    std::ostringstream sql;
    sql << "SELECT ";
    if (columns == nullptr) {
        sql << "* ";
    }
    else if (numCols > 0) {
        sql << columns[0];
        for (std::size_t i = 1u; i < numCols; i++) {
            sql << ", ";
            sql << columns[i];
        }
        sql << " ";
    }

    sql << "FROM " << table;
    if (selection != nullptr)
        sql << " WHERE " << selection;
    if (groupBy != nullptr)
        sql << " GROUP BY " << groupBy;
    if (having != nullptr)
        sql << " HAVING " << having;
    if (orderBy != nullptr)
        sql << " ORDER BY " << orderBy;
    if (limit != nullptr)
        sql << " LIMIT " << limit;

    //Logger::log(Logger::Debug, "EXECUTE SQL: %s", sql.str().c_str());

    TAKErr code;
    
    code = this->database->compileQuery(cursor, sql.str().c_str());
    TE_CHECKRETURN_CODE(code);

    if (numArgs) {
        for (std::size_t i = 0; i < numArgs; i++) {
            if (selectionArgs[i])
                code = cursor->bindString(i + 1, selectionArgs[i]);
            else
                code = cursor->bindNull(i + 1);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr CatalogDatabase2::deleteCatalogPath(const char *file) NOTHROWS
{
    return this->deleteCatalogPath(file, false);
}

TAKErr CatalogDatabase2::deleteCatalogPath(const char *path, bool automated) NOTHROWS
{
    TAKErr code;

    QueryPtr result(nullptr, nullptr);
    int64_t catalogId;

    code = this->database->compileQuery(result, "SELECT " COL_CATALOG_ID " FROM " TBL_CATALOG
        " WHERE " COL_CATALOG_PATH " = ?");
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(path, storagePath, code);
    TE_CHECKRETURN_CODE(code);
    code = result->bindString(1, storagePath);
    TE_CHECKRETURN_CODE(code);
    if (result->moveToNext() != TE_Ok)
        return TE_InvalidArg;

    code = result->getLong(&catalogId, 0);
    TE_CHECKRETURN_CODE(code);
    result.reset();

    StatementPtr stmt(nullptr, nullptr);
    code = this->database->compileStatement(stmt, "DELETE FROM " TBL_CATALOG " WHERE "
            COL_CATALOG_PATH " = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, storagePath);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    stmt.reset();

    code = this->onCatalogEntryRemoved(catalogId, automated);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr CatalogDatabase2::deleteCatalogApp(const char *appName) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(nullptr, nullptr);

    code = this->database->compileStatement(stmt, "DELETE FROM " TBL_CATALOG " WHERE "
        COL_CATALOG_APP_NAME " = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, appName);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    stmt.reset();

    unsigned long numDeleted;
    code = Databases_lastChangeCount(&numDeleted, *this->database);
    TE_CHECKRETURN_CODE(code);
    if (numDeleted > 0) {
        code = this->onCatalogEntryRemoved(-1, true);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr CatalogDatabase2::deleteAll() NOTHROWS
{
    // XXX - this implementation looks bad -- will force dump on start, but no
    //       records are actually deleted
    return this->database->setVersion(0x7FFFFFFF);
}

TAKErr CatalogDatabase2::queryFiles(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &filepaths) NOTHROWS
{
    TAKErr code;
    CatalogCursorPtr result(nullptr, nullptr);
    code = this->queryCatalog(result);
    TE_CHECKRETURN_CODE(code);

    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);
        const char *path;
        code = result->getPath(&path);
        TE_CHECKBREAK_CODE(code);
        filepaths.add(path);
    } while (true);

    return (code==TE_Done) ? TE_Ok : code;
}

void CatalogDatabase2::deleteCatalogCursor(const CatalogCursor *ptr)
{
    delete ptr;
}

int CatalogDatabase2::catalogSchemaVersion()
{
    return CATALOG_VERSION;
}

/*****************************************************************************/
// Catalog Cursor

CatalogDatabase2::CatalogCursor::CatalogCursor(QueryPtr &&cursor) NOTHROWS :
    CursorWrapper2(std::move(cursor))
{}


CatalogDatabase2::CatalogCursor::~CatalogCursor() NOTHROWS
{}

TAKErr CatalogDatabase2::CatalogCursor::getPath(const char **value) NOTHROWS
{
    std::size_t idx;
    TAKErr code;

    code = this->getColumnIndex(&idx, COLUMN_CATALOG_PATH);
    TE_CHECKRETURN_CODE(code);
    return this->getString(value, idx);
}

TAKErr CatalogDatabase2::CatalogCursor::getAppVersion(int *value) NOTHROWS
{
    std::size_t idx;
    TAKErr code;

    code = this->getColumnIndex(&idx, COLUMN_CATALOG_APP_VERSION);
    TE_CHECKRETURN_CODE(code);
    return this->getInt(value, idx);
}

TAKErr CatalogDatabase2::CatalogCursor::getAppName(const char **value) NOTHROWS
{
    std::size_t idx;
    TAKErr code;

    code = this->getColumnIndex(&idx, COLUMN_CATALOG_APP_NAME);
    TE_CHECKRETURN_CODE(code);
    return this->getString(value, idx);
}

TAKErr CatalogDatabase2::CatalogCursor::getAppData(const uint8_t **value, std::size_t *valueLen) NOTHROWS
{
    std::size_t idx;
    TAKErr code;

    code = this->getColumnIndex(&idx, COLUMN_CATALOG_APP_DATA);
    TE_CHECKRETURN_CODE(code);
    return this->getBlob(value, valueLen, idx);
}

TAKErr CatalogDatabase2::CatalogCursor::getSyncVersion(int *value) NOTHROWS
{
    std::size_t idx;
    TAKErr code;

    code = this->getColumnIndex(&idx, COLUMN_CATALOG_SYNC);
    TE_CHECKRETURN_CODE(code);
    return this->getInt(value, idx);
}

namespace
{
    TAKErr isValidApp(bool *value, CatalogDatabase2::CatalogCursor &result, CatalogCurrency2 *currency)
    {
        *value = false;

        if (!currency)
            return TE_Ok;

        TAKErr code;

        const char *file;
        code = result.getPath(&file);
        TE_CHECKRETURN_CODE(code);

        if (!atakmap::util::pathExists(file))
            return code;

        int appVersion;
        code = result.getAppVersion(&appVersion);
        TE_CHECKRETURN_CODE(code);

        const uint8_t *appData;
        std::size_t appDataLen;
        code = result.getAppData(&appData, &appDataLen);
        TE_CHECKRETURN_CODE(code);

        return currency->isValidApp(value,
            file,
            appVersion,
            CatalogCurrency2::AppData{ appData, appDataLen });
    }
    
    StoragePathAdapterCatalogCursor::StoragePathAdapterCatalogCursor(TAK::Engine::DB::QueryPtr &&cursor) NOTHROWS :
    CatalogDatabase2::CatalogCursor(std::move(cursor)),
    currentPathColumnIndex(SIZE_MAX)
    { }
    
    StoragePathAdapterCatalogCursor::~StoragePathAdapterCatalogCursor() NOTHROWS
    { }
    
    TAK::Engine::Util::TAKErr StoragePathAdapterCatalogCursor::moveToNext() NOTHROWS {
        this->currentPathColumnIndex = SIZE_MAX;
        return CatalogCursor::moveToNext();
    }
    
    TAK::Engine::Util::TAKErr StoragePathAdapterCatalogCursor::getString(const char **value, const std::size_t columnIndex) NOTHROWS {
        if (this->currentPathColumnIndex == SIZE_MAX) {
            TAKErr code = this->getColumnIndex(&this->currentPathColumnIndex, /*CatalogDatabase2::*/COLUMN_CATALOG_PATH);
            TE_CHECKRETURN_CODE(code);
        }
        if (this->currentPathColumnIndex == columnIndex) {
            const char *storagePath = nullptr;
            TAKErr code = CatalogCursor::getString(&storagePath, columnIndex);
            TE_CHECKRETURN_CODE(code);
            this->currentRuntimePath = storagePath;
            code = File_getRuntimePath(this->currentRuntimePath);
            if (value && code == TE_Ok)
                *value = this->currentRuntimePath.get();
            return code;
        }
        return CatalogCursor::getString(value, columnIndex);
    }
    
    TAK::Engine::Util::TAKErr StoragePathAdapterCatalogCursor::getColumnIndex(std::size_t *value, const char *columnName) NOTHROWS {
        std::size_t result = 0;
        TAKErr code = CatalogCursor::getColumnIndex(&result, columnName);
        TE_CHECKRETURN_CODE(code);
        if (strcmp(columnName, /*CatalogDatabase2::*/COLUMN_CATALOG_PATH) == 0) {
            this->currentPathColumnIndex = result;
        }
        if (value) *value = result;
        return code;
    }

}
