////============================================================================
////
////    FILE:           DatabaseWrapper.cpp
////
////    DESCRIPTION:    Implementation of DatabaseWrapper class.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "db/DatabaseWrapper.h"

#include <cstddef>
#include <iostream>
#include <sstream>
#include <stdexcept>

#include "db/Database.h"


#define MEM_FN( fn )    "atakmap::db::DatabaseWrapper::" fn ": "


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


typedef std::vector<db::DatabaseWrapper::SchemaManager*> ManagerVector;


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

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


bool
checkSchema (db::Database& db,
             const ManagerVector& schemaMgrs)
  {
    bool validSchema (true);
    auto end (schemaMgrs.end ());

    for (auto iter (schemaMgrs.begin ());
         validSchema && iter != end;
         ++iter)
      {
        validSchema = (*iter)->checkSchemaObjects (db);
      }

    return validSchema;
  }


unsigned long
getDatabaseVersion (const ManagerVector& schemaMgrs)
  {
    //
    // The database version is a 32-bit value.  Apportion it to the available
    // SchemaManagers.
    //

    unsigned long version (0);
    std::size_t schemaBits (32 / schemaMgrs.size ());
    uint64_t versionMax ((1ULL << schemaBits) - 1);
    auto end (schemaMgrs.end ());

    for (auto iter (schemaMgrs.begin ());
         iter != end;
         ++iter)
      {
        unsigned long schemaVersion ((*iter)->getSchemaVersion ());

        if (schemaVersion > versionMax)
          {
            std::ostringstream strm;

            strm << MEM_FN ("getDatabaseVersion") "Schema version of "
                 << schemaVersion << " requires more than " << schemaBits
                 << " bits";
            throw std::range_error (strm.str ());
          }
        version = version << schemaBits | schemaVersion;
      }

    return version;
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


std::map<TAK::Engine::Port::String, std::vector<TAK::Engine::Port::String>, TAK::Engine::Port::StringLess>
getColumnNames (DatabaseWrapper& db)
  { return getColumnNames (db.getDatabase ()); }


std::vector<TAK::Engine::Port::String>
getColumnNames (DatabaseWrapper& db,
                const char* tableName)
  { return getColumnNames (db.getDatabase (), tableName); }


const char*
getDatabaseFilePath (DatabaseWrapper& db)
  { return getDatabaseFilePath (db.getDatabase ()); }


int64_t
getNextAutoincrementID (DatabaseWrapper& db,
                        const char* table)
  { return getNextAutoincrementID (db.getDatabase (), table); }


std::vector<TAK::Engine::Port::String>
getTableNames (DatabaseWrapper& db)
  { return getTableNames (db.getDatabase ()); }


unsigned long
lastChangeCount (DatabaseWrapper& db)
  { return lastChangeCount (db.getDatabase ()); }


int64_t
lastInsertRowID (DatabaseWrapper& db)
  { return lastInsertRowID (db.getDatabase ()); }


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


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

DatabaseWrapper::~DatabaseWrapper()
    NOTHROWS
{
    for (ManagerVector::const_iterator iter(schemaManagers.begin());
        iter != schemaManagers.end();
        ++iter)
    {
        delete *iter;
    }
}

Cursor*
DatabaseWrapper::query (const char* table,
                        const std::vector<const char*>& columns,
                        const char* where,
                        const std::vector<const char*>& whereArgs,
                        const char* groupBy,
                        const char* having,
                        const char* orderBy,
                        const char* limit)
  {
    if (!table)
      {
        throw std::invalid_argument (MEM_FN ("query") "Received NULL table");
      }

    std::ostringstream strm;

    strm << "SELECT ";
    if (columns.empty ())
      {
        strm << "*";
      }
    else
      {
        auto iter (columns.begin ());
        auto end (columns.end ());

        strm << *iter;
        while (++iter != end)
          {
            strm << ", " << *iter;
          }
      }
    strm << " FROM " << table;
    if (where)
      {
        strm << " WHERE " << where;
      }
    if (groupBy)
      {
        strm << " GROUP BY " << groupBy;
      }
    if (having)
      {
        strm << " HAVING " << having;
      }
    if (orderBy)
      {
        strm << " ORDER BY " << orderBy;
      }
    if (limit)
      {
        strm << " LIMIT " << limit;
      }

    return where
        ? database->query (strm.str ().c_str (), whereArgs)
        : database->query (strm.str ().c_str ());
  }


///=====================================
///  DatabaseWrapper::Factory MEMBER FUNCTIONS
///=====================================


DatabaseWrapper*
DatabaseWrapper::Factory::getDatabaseWrapper (Database* db)
    const
    NOTHROWS
  {
    DatabaseWrapper* result (nullptr);

    if (db)
      {
        try
          {
            std::unique_ptr<DatabaseWrapper> tmp (createDatabaseWrapper (db));

            if (tmp.get ())
              {
                tmp->checkSchema ();
                result = tmp.release ();
              }
          }
        catch (...)
          {
            std::cerr << MEM_FN ("Factory::getDatabaseWrapper")
                      << "Caught unknown exception";
          }
      }

    return result;
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


DatabaseWrapper::DatabaseWrapper (Database* db)
  : mutex (TEMT_Recursive),
    database (db)
  {
    if (!db)
      {
        throw std::invalid_argument (MEM_FN ("DatabaseWrapper")
                                     "Received NULL Database");
      }
  }

void
DatabaseWrapper::addSchemaManager(SchemaManager* mgr)
{
    if (mgr)
    {
        schemaManagers.push_back(mgr);
    }
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
DatabaseWrapper::checkSchema ()
  {
    //
    // If the database version is not what's expected or one of the
    // SchemaManagers has an issue, drop and recreate the schema objects and set
    // the database version.
    //

    unsigned long dbVersion (getDatabaseVersion (schemaManagers));
    unsigned long clientDbVersion = database->getVersion();
    bool schemaCheck = ::checkSchema(*database, schemaManagers);
    if (clientDbVersion != dbVersion
        || !schemaCheck)
      {
        if (database->isReadOnly ())
          {
            throw std::runtime_error (MEM_FN ("checkSchema")
                                      "Database is read-only");
          }

        ManagerVector::const_iterator end (schemaManagers.end ());

        for (ManagerVector::const_iterator iter (schemaManagers.begin ());
             iter != end;
             ++iter)
          {
            (*iter)->dropSchemaObjects (*database);
          }
        for (ManagerVector::const_iterator iter (schemaManagers.begin ());
             iter != end;
             ++iter)
          {
            (*iter)->createSchemaObjects (*database);
          }
        database->setVersion (dbVersion);
      }
  }


}                                       // Close db namespace.
}                                       // Close atakmap namespace.
