////============================================================================
////
////    FILE:           CatalogDatabase.cpp
////
////    DESCRIPTION:    Implementation of CatalogDatabase class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 21, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "db/CatalogDatabase.h"

#include <algorithm>
#include <functional>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <vector>

#include "db/Cursor.h"
#include "db/Database.h"
#include "db/Statement.h"
#include "thread/Lock.h"
#include "util/IO.h"
#include "util/IO2.h"

#define MEM_FN( fn )    "atakmap::db::CatalogDatabase::" fn ": "

#define TBL_CAT                 "catalog"
#define TBL_CAT_META            TBL_CAT "_metadata"
#define COL_CAT_ID              "id"
#define COL_CAT_PATH            "path"
#define COL_CAT_SYNC            "sync"
#define COL_CAT_APP_DATA        "appdata"
#define COL_CAT_APP_NAME        "appname"
#define COL_CAT_APP_VERSION     "appversion"
#define COL_CAT_META_KEY        "key"
#define COL_CAT_META_VAL        "value"


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

using namespace TAK::Engine::Thread;

////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


struct CatalogSchemaMgr
  : db::CatalogDatabase::SchemaManager
  {
    enum
      {
        CATALOG_SCHEMA_VERSION = 2
      };


    //==================================
    //  SchemaManager INTERFACE
    //==================================


    bool
    checkSchemaObjects (db::Database&)
        const
        NOTHROWS override;

    void
    createSchemaObjects (db::Database&)
        const override;

    void
    dropSchemaObjects (db::Database&)
        const
        NOTHROWS override;

    unsigned long
    getSchemaVersion ()
        const
        NOTHROWS override
      { return CATALOG_SCHEMA_VERSION; }
  };


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


const char* const
catalogEntryInsert ("INSERT INTO " TBL_CAT " ("
                    COL_CAT_PATH ", "
                    COL_CAT_SYNC ", "
                    COL_CAT_APP_NAME ", "
                    COL_CAT_APP_VERSION ", "
                    COL_CAT_APP_DATA ") VALUES (?, ?, ?, ?, ?)");


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


///=====================================
///  CatalogSchemaMgr MEMBER FUNCTIONS
///=====================================


bool
CatalogSchemaMgr::checkSchemaObjects (db::Database& db)
    const
    NOTHROWS
try
  {
    const std::vector<TAK::Engine::Port::String> tableNames (getTableNames (db));
    auto end (tableNames. end ());

    return end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_CAT))
        && end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_CAT_META));
  }
catch (...)
  { return false; }


void
CatalogSchemaMgr::createSchemaObjects (db::Database& db)
    const
  {
    db.execute ("CREATE TABLE " TBL_CAT " ("
                COL_CAT_ID " INTEGER PRIMARY KEY AUTOINCREMENT, "
                COL_CAT_PATH " TEXT, "
                COL_CAT_SYNC " INTEGER, "
                COL_CAT_APP_VERSION " INTEGER, "
                COL_CAT_APP_DATA " BLOB, "
                COL_CAT_APP_NAME " TEXT)");
    db.execute ("CREATE TABLE " TBL_CAT_META " ("
                COL_CAT_META_KEY " TEXT, "
                COL_CAT_META_VAL " TEXT)");
  }


void
CatalogSchemaMgr::dropSchemaObjects (db::Database& db)
    const
    NOTHROWS
  {
    try
      {
        db.execute ("DROP TABLE IF EXISTS " TBL_CAT);
        db.execute ("DROP TABLE IF EXISTS " TBL_CAT_META);
      }
    catch (...)
      { }
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


///=====================================
///  CatalogDatabase MEMBER FUNCTIONS
///=====================================


int64_t
CatalogDatabase::addCatalogEntry (const char* filePath,
                                  const Currency& currency)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("addCatalogEntry")
                                     "Received NULL filePath");
      }

    Lock lock(getMutex());

    return addCatalogEntryInternal (filePath, currency);
  }


void
CatalogDatabase::beginSync ()
  {
    Lock lock(getMutex());

    in_sync_ = true;
    sync_version_ = querySyncVersion () + 1;
    beginSyncImpl ();
  }


void
CatalogDatabase::completeSync ()
  {
    Lock lock(getMutex());

    if (!in_sync_)
      {
        throw std::logic_error (MEM_FN ("completeSync") "Not synchronizing");
      }
    try
      {
        if (!sync_successful_)
          {
            sync_version_ = querySyncVersion ();
          }
        completeSyncImpl ();
        if (sync_successful_)
          {
            updateSyncVersion ();
          }
      }
    catch (...)
      {
        in_sync_ = false;
        throw;
      }
  }


void
CatalogDatabase::deleteAll ()
  { getDatabase ().setVersion (0); }


void
CatalogDatabase::deleteCatalogApp (const char* appName)
  {
    if (!appName)
      {
        throw std::invalid_argument (MEM_FN ("deleteCatalogApp")
                                     "Received NULL appName");
      }

    Database& db (getDatabase ());
    Lock lock(getMutex());
    std::unique_ptr<Statement> statement
        (db.compileStatement ("DELETE FROM " TBL_CAT
                              " WHERE " COL_CAT_APP_NAME " = ?"));

    statement->bind (1, appName);
    statement->execute ();
    if (lastChangeCount (db))
      {
        catalogEntryRemoved (-1, true);
      }
  }


void
CatalogDatabase::deleteCatalogPath (const char* filePath,
                                    bool automated)     // Defaults to false.
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("deleteCatalogPath")
                                     "Received NULL filePath");
      }

    Database& db (getDatabase ());
    Lock lock(getMutex());

      TE_GET_STORAGE_PATH_THROW(filePath, storageFilePath, std::invalid_argument("filePath has no storage path"));
      
    std::unique_ptr<db::Cursor> dbResult
        (db.query ("SELECT " COL_CAT_ID " FROM " TBL_CAT
                   " WHERE " COL_CAT_PATH " = ?",
                   std::vector<const char*> (1, storageFilePath)));

    if (dbResult->moveToNext ())
      {
        std::unique_ptr<Statement> statement
            (db.compileStatement ("DELETE FROM " TBL_CAT
                                  " WHERE " COL_CAT_PATH " = ?"));

        statement->bind (1, storageFilePath);
        statement->execute ();

        catalogEntryRemoved (dbResult->getLong (0), automated);
      }
  }


void
CatalogDatabase::markCatalogEntryValid (const char* filePath)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("markCatalogEntryValid")
                                     "Received NULL filePath");
      }

    Database& db (getDatabase ());
    Lock lock(getMutex());

    if (!update_catalog_entry_sync_.get ())
      {
        update_catalog_entry_sync_.reset
            (db.compileStatement ("UPDATE " TBL_CAT
                                  " SET " COL_CAT_SYNC
                                  " = ? WHERE " COL_CAT_PATH " = ?"));
      }
    update_catalog_entry_sync_->bind (1, static_cast<int> (sync_version_));
    update_catalog_entry_sync_->bind (2, filePath);
    update_catalog_entry_sync_->execute ();
    update_catalog_entry_sync_->clearBindings ();
    catalogEntryMarkedValid (-1);
  }


CatalogDatabase::Cursor*
CatalogDatabase::queryCatalog ()
  {
    std::unique_ptr<db::Cursor> result (getDatabase ().query
                          ("SELECT * FROM " TBL_CAT));
    return new Cursor(std::move(result));
 }


CatalogDatabase::Cursor*
CatalogDatabase::queryCatalogApp (const char* appName)
  {
    if (!appName)
      {
        throw std::invalid_argument (MEM_FN ("queryCatalogApp")
                                     "Received NULL appName");
      }

    std::unique_ptr<db::Cursor> result (getDatabase ().query
                           ("SELECT "
                            COL_CAT_PATH ", "
                            COL_CAT_APP_VERSION ", "
                            COL_CAT_APP_DATA ", "
                            COL_CAT_APP_NAME
                            " FROM " TBL_CAT " WHERE " COL_CAT_APP_NAME " = ?",
                            std::vector<const char*> (1, appName)));
    return new Cursor(std::move(result));
  }


CatalogDatabase::Cursor*
CatalogDatabase::queryCatalogPath (const char* filePath)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("queryCatalogPath")
                                     "Received NULL filePath");
      }

    TE_GET_STORAGE_PATH_THROW(filePath, storagePath, std::invalid_argument(MEM_FN ("queryCatalogPath") "No existing storage path"));
      
    std::unique_ptr<db::Cursor> result (getDatabase ().query
                           ("SELECT "
                            COL_CAT_PATH ", "
                            COL_CAT_APP_VERSION ", "
                            COL_CAT_APP_DATA ", "
                            COL_CAT_APP_NAME
                            " FROM " TBL_CAT " WHERE " COL_CAT_PATH " = ?",
                            std::vector<const char*> (1, storagePath)));
    return new Cursor(std::move(result));
  }


void
CatalogDatabase::setSyncSuccessful ()
  {
    Lock lock(getMutex());

    sync_successful_ = true;
  }


void
CatalogDatabase::validateCatalog ()
  {
    Lock lock(getMutex());

    validateCatalogInternal (queryCatalog ());
  }


void
CatalogDatabase::validateCatalogApp (const char* appName)
  {
    Lock lock(getMutex());

    validateCatalogInternal (queryCatalogApp (appName));
  }


void
CatalogDatabase::validateCatalogPath (const char* filePath)
  {
    Lock lock(getMutex());

    validateCatalogInternal (queryCatalogPath (filePath));
  }


///=====================================
///  CatalogDatabase::CurrencyRegistry MEMBER FUNCTIONS
///=====================================


CatalogDatabase::CurrencyRegistry::CurrencyRegistry ()
  : mutex (TEMT_Recursive)
  { }


const std::shared_ptr<CatalogDatabase::Currency>
CatalogDatabase::CurrencyRegistry::getCurrency (const char* name)
    const
  {
    if (!name)
      {
        throw std::invalid_argument (MEM_FN ("CurrencyRegistry::getCurrency")
                                     "Received NULL Currency name");
      }

    Lock lock(mutex);
    for (auto it = currencies.begin(); it != currencies.end(); it++) {
        if (TAK::Engine::Port::String_equal(it->first, name)) {
            return it->second;
        }
    }
    return nullptr;
  }


void
CatalogDatabase::CurrencyRegistry::registerCurrency (Currency* currency)
  {
    if (!currency)
      {
        throw std::invalid_argument
                  (MEM_FN ("CurrencyRegistry::registerCurrency")
                   "Received NULL Currency");
      }
    if (!currency->getName ())
      {
        throw std::invalid_argument
                  (MEM_FN ("CurrencyRegistry::registerCurrency")
                   "Received Currency with NULL name");
      }

    Lock lock(mutex);

    currencies.insert (std::make_pair (currency->getName (), currency));
  }


void
CatalogDatabase::CurrencyRegistry::unregisterCurrency (const char* name)
  {
    if (!name)
      {
        throw std::invalid_argument
                  (MEM_FN ("CurrencyRegistry::unregisterCurrency")
                   "Received NULL Currency name");
      }

    Lock lock(mutex);

    currencies.erase (name);
  }


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


///=====================================
///  CatalogDatabase CONSTANTS
///=====================================


const char* const
CatalogDatabase::TABLE_CATALOG (TBL_CAT);
const char* const
CatalogDatabase::TABLE_CATALOG_METADATA (TBL_CAT_META);

const char* const
CatalogDatabase::COLUMN_CATALOG_ID (COL_CAT_ID);
const char* const
CatalogDatabase::COLUMN_CATALOG_PATH (COL_CAT_PATH);
const char* const
CatalogDatabase::COLUMN_CATALOG_SYNC (COL_CAT_SYNC);
const char* const
CatalogDatabase::COLUMN_CATALOG_APP_DATA (COL_CAT_APP_DATA);
const char* const
CatalogDatabase::COLUMN_CATALOG_APP_NAME (COL_CAT_APP_NAME);
const char* const
CatalogDatabase::COLUMN_CATALOG_APP_VERSION (COL_CAT_APP_VERSION);

const char* const
CatalogDatabase::COLUMN_CATALOG_METADATA_KEY (COL_CAT_META_KEY);
const char* const
CatalogDatabase::COLUMN_CATALOG_METADATA_VALUE (COL_CAT_META_VAL);


///=====================================
///  CatalogDatabase MEMBER FUNCTIONS
///=====================================


CatalogDatabase::CatalogDatabase (Database* db,
                                  CurrencyRegistry* registry)
  : registry_ (registry),
    in_sync_ (false),
    sync_successful_ (false),
#if 0
    syncVersion (querySyncVersion ())
#else
    sync_version_(-1)
#endif
  {
    if (!registry)
      {
        throw std::invalid_argument (MEM_FN ("CatalogDatabase")
                                     "Received NULL CurrencyRegistry");
      }

    addSchemaManager (new CatalogSchemaMgr);
  }


int64_t
CatalogDatabase::addCatalogEntryInternal (const char* derivedFromFilePath,
                                          const Currency& currency)
  {
    Database& db (getDatabase ());
    int64_t nextCatalogID (getNextAutoincrementID (db, TABLE_CATALOG));
    std::unique_ptr<Statement> statement
        (db.compileStatement (catalogEntryInsert));

    TE_GET_STORAGE_PATH_THROW(derivedFromFilePath, storagePath, std::invalid_argument(MEM_FN ("addCatalogEntryInternal") "No existing storage path"));
  
    statement->bind (1, storagePath);
    statement->bind (2, static_cast<int> (sync_version_));
    statement->bind (3, currency.getName ());
    statement->bind (4, static_cast<int> (currency.getVersion ()));
#if 1
    statement->bind (5, currency.getData (derivedFromFilePath));
#else
    statement->bindNULL(5);
#endif
    statement->execute ();
    catalogEntryAdded (nextCatalogID);

    return nextCatalogID;
  }


unsigned long
CatalogDatabase::querySyncVersion ()
  {
    std::unique_ptr<db::Cursor> dbResult
        (getDatabase ().query ("SELECT " COL_CAT_META_VAL
                               " FROM " TBL_CAT_META
                               " WHERE " COL_CAT_META_KEY " = ?",
                               std::vector<const char*> (1, "syncVersion")));

    return dbResult->moveToNext () ? dbResult->getInt (0) : 0;
  }


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


void
CatalogDatabase::deleteOutOfSync ()
  {
    Database& db (getDatabase ());
    std::unique_ptr<Statement> statement
        (db.compileStatement ("DELETE FROM " TBL_CAT
                              " WHERE " COL_CAT_SYNC " != ?"));

    statement->bind (1, static_cast<int> (sync_version_));
    statement->execute ();
    if (lastChangeCount (db))
      {
        catalogEntryRemoved (-1, true);
      }
  }


void
CatalogDatabase::updateSyncVersion ()
  {
    Database& db (getDatabase ());
    std::unique_ptr<db::Cursor> dbResult
        (db.query ("SELECT * FROM " TBL_CAT_META
                   " WHERE " COL_CAT_META_KEY " = ?",
                   std::vector<const char*> (1, "syncVersion")));
    std::unique_ptr<Statement> statement;

    if (!dbResult->moveToNext ())
      {
        statement.reset
            (db.compileStatement ("INSERT INTO " TBL_CAT_META
                                  " (" COL_CAT_META_KEY
                                  ", " COL_CAT_META_VAL
                                  ") VALUES (?, ?)"));
        statement->bind (1, "syncVersion");
        statement->bind (2, static_cast<int> (sync_version_));
      }
    else
      {
        statement.reset
            (db.compileStatement ("UPDATE " TBL_CAT_META
                                  " SET " COL_CAT_META_VAL " = ?"
                                  " WHERE " COL_CAT_META_KEY " = ?"));
        statement->bind (1, static_cast<int> (sync_version_));
        statement->bind (2, "syncVersion");
      }
    statement->execute ();
  }


void
CatalogDatabase::validateCatalogInternal (Cursor* catCursor)
  {
    std::unique_ptr<Cursor> cursor (catCursor);
    Database& db (getDatabase ());
    std::unique_ptr<Database::Transaction> transaction
        (!db.inTransaction ()
         ? new Database::Transaction (db)
         : nullptr);

    while (cursor->moveToNext ())
      {
        const char* filePath (cursor->getPath ());

        if (filePath)
          {
            std::shared_ptr<Currency> currency (registry_->getCurrency (cursor->getName ()));

            if (
#ifndef MSVC
                !util::pathExists (filePath) ||
#endif
                !currency
                || !currency->isValid (filePath,
                                       static_cast<unsigned long>(cursor->getVersion ()),
                                       cursor->getData ()))
              {
                deleteCatalogPath (filePath, true);
              }
          }
      }
    if (transaction.get ())
      {
        db.setTransactionSuccessful ();
      }
  }


///=====================================
///  CatalogDatabase::Factory MEMBER FUNCTIONS
///=====================================


DatabaseWrapper*
CatalogDatabase::Factory::createDatabaseWrapper (Database* db)
    const
    NOTHROWS
  {
    DatabaseWrapper* result (nullptr);

    if (db)
      {
        try
          {
            result = createCatalogDatabase (db, new CurrencyRegistry);
          }
        catch (...)
          {
            std::cerr << MEM_FN ("Factory::createDatabaseWrapper")
                      << "Caught unknown exception";
          }
      }

    return result;
  }
    
#if TE_SHOULD_ADAPT_STORAGE_PATHS
const char*
CatalogDatabase::Cursor::getPath () const throw (CursorError) {
    this->cachedRuntimePath = getString (getColumnIndex (COLUMN_CATALOG_PATH));
    TAK::Engine::Util::File_getRuntimePath(this->cachedRuntimePath);
    return this->cachedRuntimePath;
}
#endif

}                                       // Close db namespace.
}                                       // Close atakmap namespace.
