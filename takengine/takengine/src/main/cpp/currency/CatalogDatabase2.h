#ifndef TAK_ENGINE_DB_CATALOGDATABASE2_H_INCLUDED
#define TAK_ENGINE_DB_CATALOGDATABASE2_H_INCLUDED

#include <cstdint>
#include <memory>

#include "db/CursorWrapper2.h"
#include "db/DB_Error.h"
#include "db/Database2.h"
#include "port/Platform.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Currency {
            class CatalogCurrencyRegistry2;
            class CatalogCurrency2;

            class CatalogDatabase2 : private TAK::Engine::Util::NonCopyable
            {
            public :
                class CatalogCursor;
                typedef std::unique_ptr<CatalogCursor, void(*)(const CatalogCursor *)> CatalogCursorPtr;
            public :
                CatalogDatabase2(CatalogCurrencyRegistry2 &currencyRegistry) NOTHROWS;
            public :
                virtual ~CatalogDatabase2() NOTHROWS;
            public :
                TAK::Engine::Util::TAKErr open(const char *path) NOTHROWS;
            protected :
                virtual TAK::Engine::Util::TAKErr openImpl(DB::DatabasePtr &&db) NOTHROWS;
            protected :
                /**
                * Returns <code>true</code> if the database version is current, <code>false</code> otherwise.
                * If this method returns <code>false</code>, the database will be rebuilt with a call to
                * {@link #dropTables()} followed by {@link #buildTables()}. Subclasses may override this method
                * to support their own versioning mechanism.
                * <P>
                * The default implementation compares the version of the database via
                * <code>this.database</code>.{@link DatabaseIface#getVersion()} against
                * {@link #CATALOG_VERSION}.
                *
                * @return <code>true</code> if the database version is current, <code>false</code> otherwise.
                */
                virtual TAK::Engine::Util::TAKErr checkDatabaseVersion(bool *value) NOTHROWS;

                /**
                * Sets the database versioning to the current version. Subclasses may override this method to
                * support their own versioning mechanism.
                * <P>
                * The default implementation sets the version of the database via <code>this.database</code>.
                * {@link DatabaseIface#setVersion(int)} with {@link #CATALOG_VERSION}.
                */
                virtual TAK::Engine::Util::TAKErr setDatabaseVersion() NOTHROWS;

                /**
                * Drops the tables present in the database. This method is invoked in the constructor when the
                * database version is not current.
                */
                virtual TAK::Engine::Util::TAKErr dropTables() NOTHROWS;

                /**
                * Builds the tables for the database. This method is invoked in the constructor when the
                * database lacks the catalog table or when if the database version is not current.
                * <P>
                * The default implementation invokes {@link #createCatalogTable()} and returns.
                */
                virtual TAK::Engine::Util::TAKErr buildTables() NOTHROWS;

                /**
                * Creates the catalog table.
                */
                TAK::Engine::Util::TAKErr createCatalogTable() NOTHROWS;


                virtual TAK::Engine::Util::TAKErr onCatalogEntryMarkedValid(int64_t catalogId) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr onCatalogEntryRemoved(int64_t catalogId, bool automated) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr onCatalogEntryAdded(int64_t catalogId) NOTHROWS;

            public :
                virtual TAK::Engine::Util::TAKErr addCatalogEntry(int64_t *rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr updateCatalogEntry(const int64_t rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS;
            protected :
                virtual TAK::Engine::Util::TAKErr addCatalogEntryNoSync(int64_t *rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr updateCatalogEntryNoSync(const int64_t rowId, const char *derivedFrom, CatalogCurrency2 &currency) NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr validateCatalog() NOTHROWS;
                virtual TAK::Engine::Util::TAKErr validateCatalogApp(const char *appName) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr validateCatalogPath(const char *file) NOTHROWS;
            protected :
                virtual TAK::Engine::Util::TAKErr validateCatalogNoSync(CatalogCursor &result) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr validateCatalogRowNoSync(bool *value, CatalogCursor &row) NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr markCatalogEntryValid(const char *file) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr queryCatalog(CatalogCursorPtr &cursor) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr queryCatalogPath(CatalogCursorPtr &cursor, const char *path) NOTHROWS;

                virtual TAK::Engine::Util::TAKErr queryCatalogApp(CatalogCursorPtr &cursor, const char *appName) NOTHROWS;

            protected :
                TAK::Engine::Util::TAKErr queryRawCatalog(CatalogCursorPtr &cursor, const char *rawQuery, const char *arg) NOTHROWS;
            public :
                /**
                * Performs an arbitrary query on the underlying database.
                *
                * <P>It is recommended that the baked query functions be used in most
                * circumstances as the underlying schemas are subject to change.
                *
                * @param table         The table
                * @param columns       The columns to return
                * @param selection     The where clause
                * @param selectionArgs The where arguments
                * @param groupBy       The group by clause
                * @param having        The having clause
                * @param orderBy       The order by clause
                * @param limit         The limit clause
                *
                * @return  The result
                */
                virtual TAK::Engine::Util::TAKErr query(TAK::Engine::DB::QueryPtr &cursor, const char *table, const char **columns, const std::size_t numCols, const char *selection,
                    const char **selectionArgs, const std::size_t numArgs, const char *groupBy, const char *having, const char *orderBy, const char *limit) NOTHROWS;

            public :
                virtual TAK::Engine::Util::TAKErr deleteCatalogPath(const char *file) NOTHROWS;
            protected :
                virtual TAK::Engine::Util::TAKErr deleteCatalogPath(const char *path, bool automated) NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr deleteCatalogApp(const char *appName) NOTHROWS;
                /**
                * Bumps the database version so it will be recreated on next startup
                */
            public :
                virtual TAK::Engine::Util::TAKErr deleteAll() NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr queryFiles(TAK::Engine::Port::Collection<TAK::Engine::Port::String> &files) NOTHROWS;

                /**************************************************************************/

            private :
                static void deleteCatalogCursor(const CatalogCursor *ptr);
            protected :
                static int catalogSchemaVersion();

                static const char * const TABLE_CATALOG;
                static const char * const TABLE_CATALOG_METADATA;

                static const char * const COLUMN_CATALOG_ID;
                static const char * const COLUMN_CATALOG_PATH;
                static const char * const COLUMN_CATALOG_SYNC;
                static const char * const COLUMN_CATALOG_APP_VERSION;
                static const char * const COLUMN_CATALOG_APP_DATA;
                static const char * const COLUMN_CATALOG_APP_NAME;

                static const char * const COLUMN_CATALOG_METADATA_KEY;
                static const char * const COLUMN_CATALOG_METADATA_VALUE;
            protected :
                TAK::Engine::DB::DatabasePtr database;
                CatalogCurrencyRegistry2 &currencyRegistry;

                TAK::Engine::DB::StatementPtr updateCatalogEntrySyncStmt;
                Thread::Mutex mutex;
            };

            class CatalogDatabase2::CatalogCursor : public TAK::Engine::DB::CursorWrapper2
            {
            public :
                CatalogCursor(TAK::Engine::DB::QueryPtr &&cursor) NOTHROWS;
            public :
                virtual ~CatalogCursor() NOTHROWS;
            public :
                TAK::Engine::Util::TAKErr getPath(const char **value) NOTHROWS;
                TAK::Engine::Util::TAKErr getAppVersion(int *value) NOTHROWS;
                TAK::Engine::Util::TAKErr getAppName(const char **value) NOTHROWS;
                TAK::Engine::Util::TAKErr getAppData(const uint8_t **value, std::size_t *valLen) NOTHROWS;
                TAK::Engine::Util::TAKErr getSyncVersion(int *value) NOTHROWS;

                friend class CatalogDatabase2;
            };
        }
    }
}

#endif // TAK_ENGINE_DB_CATALOGDATABASE2_H_INCLUDED
