////============================================================================
////
////    FILE:           CatalogDatabase.h
////
////    DESCRIPTION:    Abstract base class for a catalog database.
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


#ifndef ATAKMAP_DB_CATALOG_DATABASE_H_INCLUDED
#define ATAKMAP_DB_CATALOG_DATABASE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <map>
#include <memory>
#include <stdint.h>
#include <utility>

#include "port/Platform.h"
#include "port/String.h"

#include "db/Cursor.h"
#include "db/DatabaseWrapper.h"

#include "util/NonCopyable.h"
#include "thread/Mutex.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


class Database;
class Statement;


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


///=============================================================================
///
///  class atakmap::db::CatalogDatabase
///
///     Abstract base class for a catalog database.
///
///=============================================================================


class CatalogDatabase
  : public virtual DatabaseWrapper
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Currency;
    class CurrencyRegistry;
    class Cursor;
    class Factory;
    class FileCursor;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~CatalogDatabase ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // data member).  This is acceptable.
    //

    //
    // Adds a catalog entry for the supplied file.  Returns the catalog ID for
    // the new entry.
    //
    // Throws std::invalid_argument if derivedFromFilePath is NULL.
    //
    int64_t
    addCatalogEntry (const char* filePath,
                     const Currency& currency);

    //
    //
    void
    beginSync ();

    //
    // Throws std::logic_error if beginSync has not been called.
    //
    void
    completeSync ();

    //
    // Changes the database version so it will be recreated on next startup.
    //
    void
    deleteAll ();

    //
    // Deletes all entries from the catalog that have the supplied application
    // name.
    //
    // Throws std::invalid_argument if appName is NULL.
    //
    void
    deleteCatalogApp (const char* appName);

    //
    // Deletes all entries from the catalog that have the supplied file path.
    //
    // Throws std::invalid_argument if filePath is NULL.
    //
    void
    deleteCatalogPath (const char* filePath,
                       bool automated = false);

    CurrencyRegistry&
    getCurrencyRegistry ()
        const
        NOTHROWS
      { return *registry_; }

    //
    // Throws std::invalid_argument if filePath is NULL.
    //
    void
    markCatalogEntryValid (const char* filePath);

    //
    // Selects the contents of the catalog.
    //
    Cursor*
    queryCatalog ();

    //
    // Selects path, version, data, and name for all catalog entries with the
    // supplied application name.
    //
    // Throws std::invalid_argument if appName is NULL.
    //
    Cursor*
    queryCatalogApp (const char* appName);

    //
    // Selects path, version, data, and name for all catalog entries with the
    // supplied file path.
    //
    // Throws std::invalid_argument if filePath is NULL.
    //
    Cursor*
    queryCatalogPath (const char* filePath);

    //
    //
    void
    setSyncSuccessful ();

    //
    //
    void
    validateCatalog ();

    //
    //
    void
    validateCatalogApp (const char* appName);

    //
    //
    void
    validateCatalogPath (const char* filePath);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED CONSTANTS
    //==================================


    static const char* const TABLE_CATALOG;
    static const char* const TABLE_CATALOG_METADATA;

    static const char* const COLUMN_CATALOG_ID;
    static const char* const COLUMN_CATALOG_PATH;
    static const char* const COLUMN_CATALOG_SYNC;
    static const char* const COLUMN_CATALOG_APP_VERSION;
    static const char* const COLUMN_CATALOG_APP_DATA;
    static const char* const COLUMN_CATALOG_APP_NAME;

    static const char* const COLUMN_CATALOG_METADATA_KEY;
    static const char* const COLUMN_CATALOG_METADATA_VALUE;


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    //
    // Throws std::invalid_argument if supplied Database or CurrencyRegistry is
    // NULL.
    //
    CatalogDatabase (Database*,
                     CurrencyRegistry*);

    int64_t
    addCatalogEntryInternal (const char* filePath,
                             const Currency& currency);

    //
    // Called with the mutex locked.
    //
    virtual
    void
    beginSyncImpl ()
      { }

    //
    // Called with the mutex locked.
    //
    virtual
    void
    completeSyncImpl ()
      { deleteOutOfSync (); }

    //
    // Returns the current value of the sync version from the database's
    // metadata.  The value of syncVersion is not modified by this call.
    //
    unsigned long
    querySyncVersion ();


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // Called (with the mutex locked) when a catalog entry is added.
    //
    virtual
    void
    catalogEntryAdded (int64_t catalogID)
        = 0;

    //
    // Called (with the mutex locked) when a catalog entry is marked valid.
    //
    virtual
    void
    catalogEntryMarkedValid (int64_t catalogID)
        = 0;

    //
    // Called (with the mutex locked) when a catalog entry is deleted.
    //
    virtual
    void
    catalogEntryRemoved (int64_t catalogID,
                         bool automated)
        = 0;

    void
    deleteOutOfSync ();

    //
    // Updates the database's metadata to reflect the current value of
    // syncVersion.
    //
    void
    updateSyncVersion ();

    void
    validateCatalogInternal (Cursor*);  // Adopts and destroys Cursor.


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::auto_ptr<CurrencyRegistry> registry_;
    bool in_sync_;
    bool sync_successful_;
    unsigned long sync_version_;
    std::unique_ptr<Statement> update_catalog_entry_sync_;
  };


///=============================================================================
///
///  class atakmap::db::CatalogDatabase::Currency
///
///     Abstract base class for a catalog database currency.
///
///=============================================================================


class CatalogDatabase::Currency
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    virtual
    atakmap::util::BlobImpl
    getData (const char* filePath)
        const
        = 0;

    virtual
    const char*
    getName ()
        const
        NOTHROWS
        = 0;

    virtual
    unsigned long
    getVersion ()
        const
        NOTHROWS
        = 0;

    //
    // Throws std::invalid_argument if the supplied filePath is NULL or if
    // either of the supplied data buffer pointers are NULL.
    //
    virtual
    bool
    isValid (const char* filePath,
             unsigned long version,
             const atakmap::util::BlobImpl& data)
        const
        = 0;
  };


///=============================================================================
///
///  class atakmap::db::CatalogDatabase::CurrencyRegistry
///
///     Concrete registry for catalog database currency.
///
///=============================================================================


class CatalogDatabase::CurrencyRegistry
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~CurrencyRegistry ()
        NOTHROWS
      { }

    //
    // A private constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // data member).  This is acceptable.
    //

    //
    // Retrieves the registered Currency with the supplied name.
    //
    // Throws std::invalid_argument if the supplied name is NULL.
    //
    const std::shared_ptr<Currency>
    getCurrency (const char* name)
        const;

    //
    // Registers (and adopts) the supplied Currency.
    //
    // Throws std::invalid_argument if the supplied Currency is NULL.
    //
    void
    registerCurrency (Currency*);

    //
    // Throws std::invalid_argument if the supplied name is NULL.
    //
    void
    unregisterCurrency (const char* name);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class Factory;


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef std::map<const char*, std::shared_ptr<Currency> > CurrencyMap;


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    CurrencyRegistry ();                // For CatalogDatabase::Factory.


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    mutable TAK::Engine::Thread::Mutex mutex;
    CurrencyMap currencies;
  };


///=============================================================================
///
///  class atakmap::db::CatalogDatabase::Cursor
///
///     Concrete cursor for a CatalogDatabase catalog query result.
///
///=============================================================================


class CatalogDatabase::Cursor
  : public CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Cursor ()
        NOTHROWS
      { }

    //
    // A private constructor is defined below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    Blob
    getData ()
        const
        throw (CursorError)
      { return getBlob (getColumnIndex (COLUMN_CATALOG_APP_DATA)); }

    const char*
    getName ()
        const
        throw (CursorError)
      { return getString (getColumnIndex (COLUMN_CATALOG_APP_NAME)); }


    const char*
    getPath ()
        const
        throw (CursorError)
#if TE_SHOULD_ADAPT_STORAGE_PATHS
      ;
#else
      { return getString (getColumnIndex (COLUMN_CATALOG_PATH)); }
#endif
      
    std::size_t
    getVersion ()
        const
        throw (CursorError)
      { return getInt (getColumnIndex (COLUMN_CATALOG_APP_VERSION)); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

#if TE_SHOULD_ADAPT_STORAGE_PATHS
      mutable TAK::Engine::Port::String cachedRuntimePath;
#endif
      
      
    friend class CatalogDatabase;


    Cursor (const std::shared_ptr<db::Cursor> &subject)      // Adopts subject cursor.
      : CursorProxy (subject)
      { }
  };


///=============================================================================
///
///  class atakmap::db::CatalogDatabase::Factory
///
///     Abstract base class for a factory for creating catalog database objects.
///
///=============================================================================


class CatalogDatabase::Factory
  : public DatabaseWrapper::Factory
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Factory ()
        NOTHROWS
      { }

    //
    // Returns an instance of a class derived from CatalogDatabase for the
    // supplied Database.  Returns NULL if the supplied Database is NULL.
    //
    CatalogDatabase*
    getCatalogDatabase (Database* db)
        const
        NOTHROWS
    { return dynamic_cast<CatalogDatabase*> (getDatabaseWrapper (db)); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // Returns an instance of a class derived from CatalogDatabase for the
    // supplied Database and CurrencyRegistry (neither of which will be NULL).
    //
    virtual
    CatalogDatabase*
    createCatalogDatabase (Database*,
                           CurrencyRegistry*)
        const
        NOTHROWS
        = 0;


    ///=====================================
    ///  DatabaseWrapper INTERFACE
    ///=====================================


    DatabaseWrapper*
    createDatabaseWrapper (Database* db)
        const
        NOTHROWS;
  };


///=============================================================================
///
///  class atakmap::db::CatalogDatabase::FileCursor
///
///     Implementation of db::FileCursor.
///
///=============================================================================


class CatalogDatabase::FileCursor
  : public db::FileCursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    FileCursor (const std::shared_ptr<CatalogDatabase::Cursor> &subject)
      : db::FileCursor (subject),
        catCursor (subject)
      { }

    const char*
    getFile ()
        const
        throw (CursorError)
      { return catCursor->getPath (); }

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::shared_ptr<CatalogDatabase::Cursor> catCursor;
  };


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_DB_CATALOG_DATABASE_H_INCLUDED
