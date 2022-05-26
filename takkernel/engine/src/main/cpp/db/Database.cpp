////============================================================================
////
////    FILE:           Database.cpp
////
////    DESCRIPTION:    Implementation of database functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 22, 2014  scott           Created.
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


#include "db/Database.h"

#include <cstring>
#include <fstream>
#include <memory>
#include <utility>
#include <sstream>

#include "db/Cursor.h"
#include "db/SpatiaLiteDB.h"
#include "db/Statement.h"
#include "port/String.h"
#include "port/StringBuilder.h"


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;


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


const char* const autoincrementQuery
    ("SELECT seq FROM sqlite_sequence WHERE name = ?");

const char* const tableNameQuery
    ("SELECT name FROM sqlite_master WHERE type=\'table\'");


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


inline
std::string
createTableInfoQuery (const char* tableName)
  {
    std::ostringstream strm;
    strm << "PRAGMA table_info(" << tableName << ")";
    return strm.str();
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


Database::~Database()
    NOTHROWS
{ }

Cursor*
Database::query(const char *tables, const std::vector<std::string> *columns,
                const std::string *selection, const std::vector<std::string> *selectionArgs,
                const std::string *groupBy, const std::string *having,
                const std::string *orderBy, const std::string *limit)
    throw (DB_Error)
{
    std::stringstream sql;

    if ((groupBy == nullptr || !groupBy->length()) && !(having == nullptr || !having->length())) {
        throw DB_Error(
            "HAVING argument without groupby argument is not allowed");
    }
    sql << "SELECT ";
    if (columns && columns->size() != 0) {
        size_t n = columns->size();

        for (size_t i = 0; i < n; i++) {
            std::string c = columns->at(i);

            if (i > 0) {
                sql << ", ";
            }
            sql << c;
        }
        sql << " ";
    } else {
        sql << "* ";
    }
    sql << "FROM " << tables;
    if (selection && selection->length() != 0)
        sql << " WHERE " << *selection;
    if (groupBy && groupBy->length() != 0)
        sql << " GROUP BY " << *groupBy;
    if (having && having->length() != 0)
        sql << " HAVING " << *having;
    if (orderBy && orderBy->length() != 0)
        sql << " ORDER BY " << orderBy;
    if (limit && limit->length() != 0)
        sql << " LIMIT " << limit;

    std::vector<const char *> selectionArgStrings;
    if (selectionArgs) {
        for (size_t i = 0; i < selectionArgs->size(); ++i)
            selectionArgStrings.push_back(selectionArgs->at(i).c_str());
    }

    std::string sqlstr = sql.str();
    return query(sqlstr.c_str(), selectionArgStrings);
}


std::map<TAK::Engine::Port::String, std::vector<TAK::Engine::Port::String>, TAK::Engine::Port::StringLess>
getColumnNames (Database& db)
  {
    std::map<TAK::Engine::Port::String, std::vector<TAK::Engine::Port::String>, TAK::Engine::Port::StringLess> result;
    std::unique_ptr<Cursor> tableResult (db.query (tableNameQuery));

    while (tableResult->moveToNext ())
      {
        TAK::Engine::Port::String tableName (tableResult->getString (0));

        result.insert (std::make_pair (tableName,
                                       getColumnNames (db, tableName)));
      }

    return result;
  }


std::vector<TAK::Engine::Port::String>
getColumnNames (Database& db,
                const char* tableName)
  {
    std::string tableInfoQuery = createTableInfoQuery (tableName);
    std::vector<TAK::Engine::Port::String> result;
    std::unique_ptr<Cursor> colResult
        (db.query (tableInfoQuery.c_str()));
    std::size_t nameCol (colResult->getColumnIndex ("name"));

    while (colResult->moveToNext ())
      {
          const char *colName = colResult->getString(nameCol);
        result.push_back (TAK::Engine::Port::String(colName));
      }

    return result;
  }


const char*
getDatabaseFilePath (Database& db)
  {
    const char* result (nullptr);
#ifndef MSVC
    SpatiaLiteDB* spatialDB (dynamic_cast<SpatiaLiteDB*> (&db));
    if (spatialDB)
      {
        result = spatialDB->getFilePath ();
      }
    else
#endif
      {
        std::unique_ptr<Cursor> dbResult (db.query ("PRAGMA database_list"));

        while (!result && dbResult->moveToNext ())
          {
            if (!TAK::Engine::Port::String_strcasecmp(dbResult->getString (1), "main"))
              {
                result = dbResult->getString (2);
              }
          }
      }

    return result;
  }


int64_t
getNextAutoincrementID (Database& db,
                        const char* table)
  {
    std::unique_ptr<Cursor> dbResult
        (db.query (autoincrementQuery, std::vector<const char*> (1, table)));

    return dbResult->moveToNext () ? dbResult->getLong (0) + 1 : 1;
  }


std::vector<TAK::Engine::Port::String>
getTableNames (Database& db)
  {
    std::vector<TAK::Engine::Port::String> result;
    std::unique_ptr<Cursor> tableResult (db.query (tableNameQuery));

    const char *tblName;
    while (tableResult->moveToNext ())
      {
          tblName = tableResult->getString(0);
        result.push_back (TAK::Engine::Port::String(tblName));
      }

    return result;
  }


//bool
//isSQLiteDatabase (const char* filePath)
//  {
//    static char header[16] = { };
//    std::ifstream strm (filePath);
//
//    return strm.read (header, 15) && !strcmp (header, "SQLite format 3");
//  }


unsigned long
lastChangeCount (Database& db)
  {
    std::unique_ptr<Cursor> qResult (db.query ("SELECT changes()"));

    return qResult->moveToNext () ? qResult->getInt (0) : 0;
  }


int64_t
lastInsertRowID (Database& db)
  {
    std::unique_ptr<Cursor> qResult (db.query ("SELECT last_insert_rowid()"));

    return qResult->moveToNext () ? qResult->getLong (0) : 0;
  }


Database*
openDatabase (const char* dbFilePath, bool readOnly)
  {
      return new SpatiaLiteDB (dbFilePath, readOnly);
  }


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

////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
