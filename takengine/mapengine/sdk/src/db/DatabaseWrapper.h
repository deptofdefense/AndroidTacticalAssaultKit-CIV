////============================================================================
////
////    FILE:           DatabaseWrapper.h
////
////    DESCRIPTION:    Abstract base class for a database wrapper.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Mar 18, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_DB_DATABASE_WRAPPER_H_INCLUDED
#define ATAKMAP_DB_DATABASE_WRAPPER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <map>
#include <memory>
#include <stdint.h>
#include <vector>

#include "port/String.h"
#include "thread/Mutex.h"
#include "util/NonCopyable.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


class Cursor;
class Database;


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
///  class atakmap::db::DatabaseWrapper
///
///     Abstract base class for a database wrapper.
///
///=============================================================================


class DatabaseWrapper
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Factory;
    class SchemaManager;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~DatabaseWrapper ()
        NOTHROWS;

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // data member).  This is acceptable.
    //

    //
    // Performs an arbitrary query on the underlying database.
    //
    // It is recommended that the baked query functions be used in most
    // circumstances as the underlying schemas are subject to change.
    //
    // If columns is empty, selects "*".
    // If where is NULL, whereArgs are ignored.
    // Throws std::invalid_argument if table is NULL.
    //
    virtual
    db::Cursor*
    query (const char* table,
           const std::vector<const char*>& columns,
           const char* where,
           const std::vector<const char*>& whereArgs,
           const char* groupBy = nullptr,
           const char* having = nullptr,
           const char* orderBy = nullptr,
           const char* limit = nullptr);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //
    // Throws std::invalid_argument if supplied Database is NULL.
    //
    DatabaseWrapper (Database*);

    //
    // Derived classes may add SchemaManagers in their constructors.
    // SchemaManagers are called after construction is complete, but before the
    // DatabaseWrapper is returned from the factory.  SchemaManagers are called
    // in the order in which they were added.
    //
    void
    addSchemaManager (SchemaManager*);

    Database&
    getDatabase ()
        const
        NOTHROWS
      { return *database; }

    TAK::Engine::Thread::Mutex&
    getMutex ()
        const
        NOTHROWS
      { return mutex; }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class Factory;


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef std::vector<SchemaManager*> ManagerVector;


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    void
    checkSchema ();                     // Called by DatabaseWrapper::Factory.


    ///
    /// Friendly overloads of functions defined for Database.
    ///

    friend
    std::map<TAK::Engine::Port::String, std::vector<TAK::Engine::Port::String>, TAK::Engine::Port::StringLess>
    getColumnNames (DatabaseWrapper&);

    friend
    std::vector<TAK::Engine::Port::String>
    getColumnNames (DatabaseWrapper&,
                    const char* tableName);

    friend
    const char*
    getDatabaseFilePath (DatabaseWrapper&);

    friend
    int64_t
    getNextAutoincrementID (DatabaseWrapper&,
                            const char* table);

    friend
    std::vector<TAK::Engine::Port::String>
    getTableNames (DatabaseWrapper&);

    friend
    unsigned long
    lastChangeCount (DatabaseWrapper&);

    friend
    int64_t
    lastInsertRowID (DatabaseWrapper&);


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    mutable TAK::Engine::Thread::Mutex mutex;
    std::auto_ptr<Database> database;
    ManagerVector schemaManagers;
  };


///=============================================================================
///
///  class atakmap::db::DatabaseWrapper::Factory
///
///     Abstract base class for a factory for creating DatabaseWrapper objects.
///
///=============================================================================


class DatabaseWrapper::Factory
  : TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~Factory ()
        NOTHROWS
      { }

    //
    // Returns an instance of a class derived from DatabaseWrapper for the
    // supplied Database.  Returns NULL if the supplied Database is NULL.
    //
    DatabaseWrapper*
    getDatabaseWrapper (Database*)
        const
        NOTHROWS;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // Returns an instance of a class derived from DatabaseWrapper for the
    // supplied Database (which will never be NULL).
    //
    virtual
    DatabaseWrapper*
    createDatabaseWrapper (Database*)
        const
        NOTHROWS
        = 0;
  };


///=============================================================================
///
///  class atakmap::db::DatabaseWrapper::SchemaManager
///
///     Abstract base class for a database table management.
///
///=============================================================================


class DatabaseWrapper::SchemaManager
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~SchemaManager ()
        NOTHROWS
      { }

    //
    // Returns true if no managed tables or indices in the supplied Database
    // need to be (re)created.
    //
    virtual
    bool
    checkSchemaObjects (Database&)
        const
        NOTHROWS
        = 0;

    //
    // Should create all managed tables and indices in the supplied database.
    // This method is invoked (after dropSchemaObjects) when the database
    // version is not current or when any SchemaManager returns false from
    // checkSchemaObjects.
    //
    virtual
    void
    createSchemaObjects (Database&)
        const
        = 0;

    //
    // Should drop all managed tables and indices present in the supplied
    // database.  This method is invoked when the database version is not
    // current or when any SchemaManager returns false from checkSchemaObjects.
    //
    virtual
    void
    dropSchemaObjects (Database&)
        const
        NOTHROWS
        = 0;

    //
    // Returns the version of the managed schema, which should be incremented
    // any time the schema is changed in a way that makes it incompatible with
    // its former definition.
    //
    virtual
    unsigned long
    getSchemaVersion ()
        const
        NOTHROWS
        = 0;
  };


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


///
///  DatabaseWrapper-befriended overloads of functions defined for Database.
///


std::map<TAK::Engine::Port::String, std::vector<TAK::Engine::Port::String>, TAK::Engine::Port::StringLess>
getColumnNames (DatabaseWrapper&);


std::vector<TAK::Engine::Port::String>
getColumnNames (DatabaseWrapper&,
                const char* tableName);


const char*
getDatabaseFilePath (DatabaseWrapper&);


int64_t
getNextAutoincrementID (DatabaseWrapper&,
                        const char* table);


std::vector<TAK::Engine::Port::String>
getTableNames (DatabaseWrapper&);


unsigned long
lastChangeCount (DatabaseWrapper&);


int64_t
lastInsertRowID (DatabaseWrapper&);


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


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


#endif  // #ifndef ATAKMAP_DB_DATABASE_WRAPPER_H_INCLUDED
