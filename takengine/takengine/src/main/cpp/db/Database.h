////============================================================================
////
////    FILE:           Database.h
////
////    DESCRIPTION:    Abstract base classes for databases.
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


#ifndef ATAKMAP_DB_DATABASE_H_INCLUDED
#define ATAKMAP_DB_DATABASE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <map>
#include <string>
#include <stdint.h>
#include <utility>
#include <vector>

#include "db/DB_Error.h"
#include "port/String.h"
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
///  class atakmap::db::Database
///
///     An abstract base class for databases.
///
///=============================================================================


class Database
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Transaction;                  // Scope-based transaction management.


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~Database ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    virtual
    void
    beginTransaction ()
        throw (DB_Error)
        = 0;

    virtual
    Statement*
    compileStatement (const char*)
        throw (DB_Error)
        = 0;

    virtual
    void
    endTransaction ()
        throw (DB_Error)
        = 0;

    virtual
    void
    execute (const char* sql)
        throw (DB_Error)
        = 0;

    virtual
    void
    execute (const char* sql,
             const std::vector<const char*>& args)
        throw (DB_Error)
        = 0;

    virtual
    unsigned long
    getVersion ()
        throw (DB_Error)
        = 0;

    virtual
    void
    interrupt ()
        throw (DB_Error)
        = 0;

    virtual
    bool
    inTransaction ()
        const
        NOTHROWS
        = 0;

    virtual
    bool
    isReadOnly ()
        const
        NOTHROWS
        = 0;

    virtual
    Cursor*
    query (const char* sql)
        throw (DB_Error)
        = 0;

    virtual
    Cursor*
    query (const char* sql,
           const std::vector<const char*>& args)
        throw (DB_Error)
        = 0;

    Cursor*
    query (const char *tables,
           const std::vector<std::string> *columns,
           const std::string *selection,
           const std::vector<std::string> *selectionArgs,
           const std::string *groupBy,
           const std::string *having,
           const std::string *orderBy,
           const std::string *limit)
        throw (DB_Error);

    virtual
    void
    setTransactionSuccessful ()
        throw (DB_Error)
        = 0;

    virtual
    void
    setVersion (unsigned long)
        throw (DB_Error)
        = 0;
  };


///=============================================================================
///
///  class atakmap::db::Database::Transaction
///
///     A concrete class that implements a scope-based database transaction.
///     Transactions are not nestable, so they are not copyable.
///
///=============================================================================


class Database::Transaction
  : TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    Transaction (Database& db)
        throw (DB_Error)
      : db (db)
      { db.beginTransaction (); }

    ~Transaction ()
        NOTHROWS
      {
        try
          { db.endTransaction (); }
        catch (...)
          { }
      }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to the following NonCopyable declarations).  This is acceptable.
    //


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    Database& db;
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


std::map<TAK::Engine::Port::String, std::vector<TAK::Engine::Port::String>, TAK::Engine::Port::StringLess>
getColumnNames (Database&);

std::vector<TAK::Engine::Port::String>
getColumnNames (Database&,
                const char* tableName);

const char*
getDatabaseFilePath (Database&);

int64_t
getNextAutoincrementID (Database&,
                        const char* table);

std::vector<TAK::Engine::Port::String>
getTableNames (Database&);

unsigned long
lastChangeCount (Database&);

int64_t
lastInsertRowID (Database&);

Database*
openDatabase (const char* databaseFilePath,
              const bool readOnly = false);


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

#endif  // #ifndef ATAKMAP_DB_DATABASE_H_INCLUDED
