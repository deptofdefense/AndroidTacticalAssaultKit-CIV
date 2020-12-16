////============================================================================
////
////    FILE:           SpatiaLiteDB.cpp
////
////    DESCRIPTION:    Implementation of SpatiaLiteDB class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Feb 13, 2015  scott           Created.
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

#include "db/SpatiaLiteDB.h"

#include <cstring>
#include <cstdint>
#include <iostream>
#include <limits>
#include <memory>
#include <sstream>
#ifndef MSVC
#include <strings.h>
#endif
#include <utility>

#include "db/Cursor.h"
#include "db/Statement.h"

#include "sqlite3.h"                    // Must appear before spatialite.h.
#include "spatialite.h"
#include "port/String.h"
#include "thread/Lock.h"
#if DB_ASYNC_INTERRUPT
#include "thread/Thread.h"
#endif
#include "util/NonCopyable.h"


#ifdef MSVC
inline int strcasecmp(const char* lhs, const char* rhs)
{
    return TAK::Engine::Port::String_strcasecmp(lhs, rhs);
}
#endif

#define MEM_FN( fn )    "atakmap::db::SpatiaLiteDB::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

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

///=============================================================================
///
///  class CursorImpl
///
///     Implementation of atakmap::db::Cursor interface.
///
///=============================================================================s


class CursorImpl
  : public db::Cursor,
    private TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    CursorImpl (sqlite3_stmt* stmt)     // Must not be NULL.
      : impl (stmt),
        colCount (sqlite3_column_count (impl)),
        valid (false)
      { }

    ~CursorImpl ()
        NOTHROWS override;

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    void
    bindArgs (const std::vector<const char*>& args)
        throw (db::DB_Error);

    //==================================
    //  db::Cursor INTERFACE
    //==================================


    Blob
    getBlob (std::size_t column)
        const
        throw (CursorError) override;

    std::size_t
    getColumnCount ()
        const override
      { return colCount; }

    std::size_t
    getColumnIndex (const char* columnName)
        const
        throw (CursorError) override;

    const char*
    getColumnName (std::size_t column)
        const
        throw (CursorError) override;

    std::vector<const char*>
    getColumnNames ()
        const override;

    double
    getDouble (std::size_t column)
        const
        throw (CursorError) override;

    int
    getInt (std::size_t column)
        const
        throw (CursorError) override;

    int64_t
    getLong (std::size_t column)
        const
        throw (CursorError) override;

    const char*
    getString (std::size_t column)
        const
        throw (CursorError) override;

    FieldType
    getType (std::size_t column)
        const
        throw (CursorError) override;

    bool
    isNull (std::size_t column)
        const
        throw (CursorError) override;

    bool
    moveToNext ()
        throw (CursorError) override;


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    void
    fillColumnNames ()
        const
      {
        columnNames.reserve (colCount);
        for (int i(0); i < static_cast<int>(colCount); ++i)
          {
            columnNames.push_back (sqlite3_column_name (impl, i));
          }
      }

    void
    validate ()
        const
        throw (CursorError)
      {
        if (!valid)
          {
            throw CursorError (MEM_FN ("CursorImpl::validate")
                               "Invalid cursor state");
          }
      }

    void
    validateIndex (std::size_t colIndex)
        const
        throw (CursorError)
      {
        if (colIndex >= colCount)
          {
            throw CursorError (MEM_FN ("CursorImpl::validateIndex")
                               "Column index out of range");
          }
      }

    void
    validate (std::size_t colIndex)
        const
        throw (CursorError)
      {
        validate ();
        validateIndex (colIndex);
      }


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    sqlite3_stmt* impl;
    std::size_t colCount;
    mutable std::vector<TAK::Engine::Port::String> columnNames;
    bool valid;
  };


class DB_Lock
  : TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    DB_Lock (sqlite3* connection)
      : mutex (sqlite3_db_mutex (connection))
      { sqlite3_mutex_enter (mutex); }

    ~DB_Lock ()
        NOTHROWS
      { sqlite3_mutex_leave (mutex); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    sqlite3_mutex* mutex;
  };


///=============================================================================
///
///  class StatementImpl
///
///     Implementation of atakmap::db::Statement interface.
///
///=============================================================================


class StatementImpl
  : public db::Statement,
    TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    StatementImpl (sqlite3_stmt* stmt)  // Must not be NULL.
      : impl (stmt)
      { }

    ~StatementImpl ()
        NOTHROWS override;

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //


    //==================================
    //  db::Statement INTERFACE
    //==================================


    void
    bind (std::size_t index,
          const char* value)
        throw (db::DB_Error) override;

    void
    bind (std::size_t index,
          double value)
        throw (db::DB_Error) override;

    void
    bind (std::size_t index,
          int value)
        throw (db::DB_Error) override;

    void
    bind (std::size_t index,
          int64_t value)
        throw (db::DB_Error) override;

    void
    bind (std::size_t index,
          const Blob& value)
        throw (db::DB_Error) override;

    void
    bindNULL (std::size_t index)
        throw (db::DB_Error) override;

    void
    clearBindings ()
        throw (db::DB_Error) override;

    void
    execute ()
        throw (db::DB_Error) override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    sqlite3_stmt* impl;
  };

  TAK::Engine::Port::String catStrings(const char *a, const char *b)
  {
      std::ostringstream strm;
      strm << a << b;
      return strm.str().c_str();
  }

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


struct GoodbyeKiss
  {
    ~GoodbyeKiss ()
      { spatialite_shutdown (); }
  } kisser;


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


///=====================================
///  FORWARD DECLARATIONS
///=====================================


void
throwDB_Error (int response,
               const char* header,      // MEM_FN (mumble) ends with ' '
               const char* doing)
    throw (db::DB_Error);


///=====================================
///  FUNCTION DEFINITIONS
///=====================================


sqlite3_stmt*
prepareStatement (const char* sql,
                  sqlite3* connection)
    throw (db::DB_Error)
  {
    if (!sql)
      {
        throw db::DB_Error (MEM_FN ("prepareStatement")
                            "Received NULL SQL string");
      }

    sqlite3_stmt* statement (nullptr);
    int response (sqlite3_prepare_v2 (connection,
                                      sql, static_cast<int>(std::strlen (sql) + 1),
                                      &statement, nullptr));

    if (response != SQLITE_OK || !statement)
      {
//        const char *error = sqlite3_errmsg(connection);
        sqlite3_finalize (statement);   // Statement should be NULL anyways.
        std::ostringstream strm;
        strm << "preparing statement: " << sql;
        throwDB_Error (response, MEM_FN ("prepareStatement"),
                       strm.str().c_str());
      }

    return statement;
  }


void
throwCursorError (int response,
                  const char* header,   // MEM_FN (mumble) ends with ' '
                  const char* doing)
    throw (db::Cursor::CursorError)
  {
    std::ostringstream strm;

    strm << header << "Error (" << sqlite3_errstr (response) << ") " << doing;

    switch (response)
      {
      case SQLITE_BUSY:

        throw db::Cursor::CursorBusy (strm.str ().c_str ());
        break;

      case SQLITE_INTERRUPT:

        throw db::Cursor::CursorInterrupted (strm.str ().c_str ());
        break;

      default:

        throw db::Cursor::CursorError (strm.str ().c_str ());
      }
  }


void
throwDB_Error (int response,
               const char* header,      // MEM_FN (mumble) ends with ' '
               const char* doing)
    throw (db::DB_Error)
  {
    std::ostringstream strm;

    strm << header << "Error (" << sqlite3_errstr (response) << ") " << doing;

    switch (response)
      {
      case SQLITE_BUSY:

        throw db::DB_Busy (strm.str ().c_str ());
        break;

      case SQLITE_INTERRUPT:

        throw db::DB_Interrupted (strm.str ().c_str ());
        break;

      default:

        throw db::DB_Error (strm.str ().c_str ());
      }
  }


///=====================================
///  CursorImpl MEMBER FUNCTIONS
///=====================================


CursorImpl::~CursorImpl ()
    NOTHROWS
  {
    int response (sqlite3_finalize (impl));

    if (response != SQLITE_OK)
      {
        std::cerr << "\n" << MEM_FN ("CursorImpl::~CursorImpl")
                  << "Error (" << sqlite3_errstr (response)
                  << ") finalizing statement: " << sqlite3_sql (impl);
      }
  }


void
CursorImpl::bindArgs (const std::vector<const char*>& args)
    throw (db::DB_Error)
  {
    for (std::size_t index = 0; index < args.size (); ++index)
      {
        const char* value (args[index]);
        int response (sqlite3_bind_text (impl, static_cast<int>(index + 1), value,
                                         value ? static_cast<int>(std::strlen (value)) : 0,
                                         SQLITE_TRANSIENT));

        if (response != SQLITE_OK)
          {
            std::ostringstream strm;

            strm << "binding text parameter " << index << " to: "
                 << (value ? value : "NULL");

            throwDB_Error (response, MEM_FN ("CursorImpl::bindArgs"),
                           strm.str ().c_str ());
          }
      }
  }


CursorImpl::Cursor::Blob
CursorImpl::getBlob (std::size_t column)
    const
    throw (CursorError)
  {
    validate (column);

    const auto* result
        (static_cast<const unsigned char*> (sqlite3_column_blob (impl, static_cast<int>(column))));

    return std::make_pair(result, result + sqlite3_column_bytes(impl, static_cast<int>(column)));
  }


std::size_t
CursorImpl::getColumnIndex (const char* columnName)
    const
    throw (CursorError)
  {
    if (!columnName)
      {
        throw CursorError (MEM_FN ("getColumnIndex")
                           "Received NULL column name");
      }

    if (columnNames.empty ())
      {
        fillColumnNames ();
      }

    std::size_t colIndex (0);

    while (colIndex < columnNames.size ()
           && ::strcasecmp (columnNames[colIndex], columnName))
      {
        colIndex++;
      }
    if (colIndex == columnNames.size ())
      {
        throw CursorError (catStrings
                                             (MEM_FN ("getColumnIndex")
                                              "Unknown column name: ",
                                              columnName));
      }
    return colIndex;
  }


const char*
CursorImpl::getColumnName (std::size_t column)
    const
    throw (CursorError)
  {
    validateIndex (column);
    return columnNames.empty ()
        ? sqlite3_column_name (impl, static_cast<int>(column))
        : static_cast<const char*> (columnNames[column]);
  }


std::vector<const char*>
CursorImpl::getColumnNames ()
    const
  {
    if (columnNames.empty ())
      {
        fillColumnNames ();
      }

    return std::vector<const char*> (columnNames.begin (), columnNames.end ());
  }


double
CursorImpl::getDouble (std::size_t column)
    const
    throw (CursorError)
  {
    validate (column);
    return sqlite3_column_double(impl, static_cast<int>(column));
  }


int
CursorImpl::getInt (std::size_t column)
    const
    throw (CursorError)
  {
    validate (column);
    return sqlite3_column_int(impl, static_cast<int>(column));
  }


int64_t
CursorImpl::getLong (std::size_t column)
    const
    throw (CursorError)
  {
    validate (column);
    return sqlite3_column_int64(impl, static_cast<int>(column));
  }


const char*
CursorImpl::getString (std::size_t column)
    const
    throw (CursorError)
  {
    validate (column);
    return reinterpret_cast<const char*>(sqlite3_column_text(impl, static_cast<int>(column)));
  }


db::Cursor::FieldType
CursorImpl::getType (std::size_t column)
    const
    throw (CursorError)
  {
    validateIndex (column);

    FieldType type (NULL_FIELD);

    switch (sqlite3_column_type(impl, static_cast<int>(column)))
      {
      case SQLITE_INTEGER:      type = INTEGER_FIELD;   break;
      case SQLITE_FLOAT:        type = FLOAT_FIELD;     break;
      case SQLITE_BLOB:         type = BLOB_FIELD;      break;
      case SQLITE3_TEXT:        type = STRING_FIELD;    break;
      case SQLITE_NULL:         type = NULL_FIELD;      break;

      default:

        throw CursorError (MEM_FN ("CursorImpl::getType") "Unknown column type");
      }

    return type;
  }


bool
CursorImpl::isNull (std::size_t column)
    const
    throw (CursorError)
  {
    validateIndex (column);
    return sqlite3_column_type (impl, static_cast<int>(column)) == SQLITE_NULL;
  }


bool
CursorImpl::moveToNext ()
    throw (CursorError)
  {
    int response (sqlite3_step (impl));

    switch (response)
      {
      case SQLITE_ROW:  valid = true;   break;
      case SQLITE_DONE: valid = false;  break;

      default:

        throwCursorError (response, MEM_FN ("CursorImpl::moveToNext"),
                          "stepping cursor");
      }

    return valid;
  }


///=====================================
///  StatementImpl MEMBER FUNCTIONS
///=====================================


StatementImpl::~StatementImpl ()
    NOTHROWS
  {
    int response (sqlite3_finalize (impl));

    if (response != SQLITE_OK)
      {
        std::cerr << "\n" << MEM_FN ("StatementImpl::~StatementImpl")
                  << "Error (" << sqlite3_errstr (response)
                  << ") finalizing statement: " << sqlite3_sql (impl);
      }
  }


void
StatementImpl::bind (std::size_t index,
                     const char* value)
    throw (db::DB_Error)
  {
    int response (sqlite3_bind_text (impl, static_cast<int>(index), value,
                                     value ? static_cast<int>(std::strlen (value)) : 0,
                                     SQLITE_TRANSIENT));

    if (response != SQLITE_OK)
      {
        std::ostringstream strm;

        strm << "binding text parameter " << index << " to: "
             << (value ? value : "NULL");

        throwDB_Error (response, MEM_FN ("StatementImpl::bind"),
                       strm.str ().c_str ());
      }
  }


void
StatementImpl::bind (std::size_t index,
                     double value)
    throw (db::DB_Error)
  {
    int response (sqlite3_bind_double (impl, static_cast<int>(index), value));

    if (response != SQLITE_OK)
      {
        std::ostringstream strm;

        strm << "binding double parameter " << index << " to: " << value;

        throwDB_Error (response, MEM_FN ("StatementImpl::bind"),
                       strm.str ().c_str ());
      }
  }


void
StatementImpl::bind (std::size_t index,
                     int value)
    throw (db::DB_Error)
  {
    int response (sqlite3_bind_int (impl, static_cast<int>(index), value));

    if (response != SQLITE_OK)
      {
        std::ostringstream strm;

        strm << "binding integer parameter " << index << " to: " << value;

        throwDB_Error (response, MEM_FN ("StatementImpl::bind"),
                       strm.str ().c_str ());
      }
  }


void
StatementImpl::bind (std::size_t index,
                     int64_t value)
    throw (db::DB_Error)
  {
    int response (sqlite3_bind_int64 (impl, static_cast<int>(index), value));

    if (response != SQLITE_OK)
      {
        std::ostringstream strm;

        strm << "binding long parameter " << index << " to: " << value;

        throwDB_Error (response, MEM_FN ("StatementImpl::bind"),
                       strm.str ().c_str ());
      }
  }


void
StatementImpl::bind (std::size_t index,
                     const Blob& value)
    throw (db::DB_Error)
  {
    int response (sqlite3_bind_blob (impl, static_cast<int>(index), value.first,
                                     static_cast<int>(value.second - value.first),
                                     SQLITE_TRANSIENT));

    if (response != SQLITE_OK)
      {
        std::ostringstream strm;

        strm << "binding blob parameter " << index;
        throwDB_Error (response, MEM_FN ("StatementImpl::bind"),
                       strm.str ().c_str ());
      }
  }


void
StatementImpl::bindNULL (std::size_t index)
    throw (db::DB_Error)
  {
    int response (sqlite3_bind_null (impl, static_cast<int>(index)));

    if (response != SQLITE_OK)
      {
        std::ostringstream strm;

        strm << "binding NULL to parameter " << index;
        throwDB_Error (response, MEM_FN ("StatementImpl::bindNULL"),
                       strm.str ().c_str ());
      }
  }


void
StatementImpl::clearBindings ()
    throw (db::DB_Error)
  {
    int response (sqlite3_clear_bindings (impl));

    if (response != SQLITE_OK)
      {
        throwDB_Error (response, MEM_FN ("StatementImpl::clearBindings"),
                       "clearing parameter bindings");
      }

#if 1
    // for consistency with the Java API, we'll reset prior to every execute
    int resetResponse (sqlite3_reset (impl));

    if (resetResponse != SQLITE_OK)
      {
        throwDB_Error (resetResponse, MEM_FN ("StatementImpl::clearBindings"),
                       "resetting statement");
      }
#endif
  }


void
StatementImpl::execute ()
    throw (db::DB_Error)
  {
#if 0
    // for consistency with the Java API, we'll reset prior to every execute
    int resetResponse (sqlite3_reset (impl));

    if (resetResponse != SQLITE_OK)
      {
        throwDB_Error (resetResponse, MEM_FN ("StatementImpl::execute"),
                       "resetting statement");
      }
#endif

    int response (sqlite3_step (impl));

    if (response == SQLITE_ROW)
      {
        std::ostringstream strm;

        strm << MEM_FN ("StatementImpl::execute")
             << "Execution of statement has result for: "
             << sqlite3_sql (impl);
        throw db::DB_Error (strm.str ().c_str ());
      }
    else if (response != SQLITE_DONE)
      {
        throwDB_Error (response, MEM_FN ("StatementImpl::execute"),
                       catStrings ("executing statement: ",
                                                       sqlite3_sql (impl)));
      }
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


SpatiaLiteDB::SpatiaLiteDB(const char* filePath)
    throw (DB_Error)
    : mutex(TEMT_Recursive),
    connection(nullptr),
    cache(nullptr),
    inTrans(false),
    successfulTrans(false),
    readOnly(false)
#if DB_ASYNC_INTERRUPT
    ,
    interrupting (false),
    finished (false)
#endif
  {
    init(filePath);
  }

SpatiaLiteDB::SpatiaLiteDB(const char* filePath, const bool ro)
    throw (DB_Error)
  : mutex(TEMT_Recursive),
    connection(nullptr),
    cache(nullptr),
    inTrans(false),
    successfulTrans(false),
    readOnly(ro)
#if DB_ASYNC_INTERRUPT
    ,
    interrupting(false),
    finished(false)
#endif
{
    init(filePath);
}

void SpatiaLiteDB::init(const char *filePath) throw (DB_Error)
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
              throwDB_Error(response,
                  MEM_FN("SpatiaLiteDB"),
                  filePath
                  ? catStrings
                  ("opening database at ",
                  filePath)
                  : "opening a temporary database");
          }
          cache = spatialite_alloc_connection();
          spatialite_init_ex(connection, cache, false);

#if DB_ASYNC_INTERRUPT
          {
              // Detached.
              TAKErr code(TE_Ok);
              ThreadPtr detached(NULL, NULL);
              code = Thread_start(detached, interruptThreadFn, this);
              if (code == TE_Ok)
                  detached->detach();
          }
#endif
#ifdef CHATTY_CONNECTION
          const char* option(NULL);
          int i(0);

          std::cout << "\nCompile-time options:";
          while ((option = sqlite3_compileoption_get(i++)))
          {
              std::cout << "\n  " << option;
          }
          switch (sqlite3_threadsafe())
          {
          case 0:   std::cout << "\n*NOT* Threadsafe";      break;
          case 1:   std::cout << "\nThreadsafe";            break;
          default:  std::cout << "\nThreadsafe???";
          }

          std::auto_ptr<Cursor> cursor(query("PRAGMA busy_timeout"));

          if (!cursor->moveToNext())
          {
              std::cerr << "\nFailed to get busy_timeout";
          }
          else
          {
              std::cout << "\nBusy timeout = " << cursor->getLong(0);
          }
          cursor.reset(query("PRAGMA journal_mode"));
          if (!cursor->moveToNext())
          {
              std::cerr << "\nFailed to get journal_mode";
          }
          else
          {
              std::cout << "\nJournal mode = " << cursor->getString(0);
          }
          std::cout << "\nOpened DB connection " << connection << " to "
              << (filePath ? filePath : "in-memory database") << std::flush;
#endif
    }
    catch (const std::exception &ex)
    {
          std::ostringstream strm;

          strm << MEM_FN("SpatiaLiteDB") "Caught std::exception: " << ex.what();
          throw DB_Error(strm.str().c_str());
    }
    catch (...)
    {
        throw DB_Error(MEM_FN("SpatiaLiteDB") "Caught unknown exception");
    }
  }

void
SpatiaLiteDB::beginTransaction ()
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    if (inTrans)
        {
        throw DB_Error (MEM_FN ("beginTransaction")
                        "Already in a transaction");
        }
    execute ("BEGIN EXCLUSIVE");
    inTrans = true;
  }


db::Statement*
SpatiaLiteDB::compileStatement (const char* sql)
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    return new StatementImpl (prepareStatement (sql, connection));
  }


void
SpatiaLiteDB::endTransaction ()
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    if (!inTrans)
        {
        throw DB_Error (MEM_FN ("endTransaction") "Not in a transaction");
        }

    //
    // Must reset any busy statements before committing or rolling back.
    //

    for (sqlite3_stmt* next (sqlite3_next_stmt (connection, nullptr));
            next;
            next = sqlite3_next_stmt (connection, next))
        {
        if (sqlite3_stmt_busy (next))
            {
            int response (sqlite3_reset (next));

            if (response != SQLITE_OK)
                {
                throwDB_Error (response,
                                MEM_FN ("endTransaction"),
                                catStrings
                                                    ("resetting busy statement: ",
                                                    sqlite3_sql (next)));
                }
            }
        }

    execute (successfulTrans ? "COMMIT" : "ROLLBACK");
    inTrans = successfulTrans = false;
  }


void
SpatiaLiteDB::execute (const char* sql)
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    if (!sql)
        {
        throw DB_Error (MEM_FN ("execute") "Received NULL SQL string");
        }

    int response (sqlite3_exec (connection, sql, nullptr, nullptr, nullptr));

    if (response != SQLITE_OK)
        {
        throwDB_Error (response,
                        MEM_FN ("execute"),
                        catStrings ("executing SQL: ",
                                                        sql));
        }
  }


void
SpatiaLiteDB::execute (const char* sql,
                       const std::vector<const char*>& args)
    throw (DB_Error)
  {
    if (args.empty ())
      {
        execute (sql);
      }
    else
      {
        std::unique_ptr<db::Statement> stmt (compileStatement (sql));

        for (std::size_t i (0); i < args.size (); ++i)
          {
            stmt->bind (i + 1, args[i]);
          }
        stmt->execute ();
      }
  }


const char*
SpatiaLiteDB::getFilePath ()
    const throw (DB_Error)
  {
    const char* result (nullptr);

    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    result = sqlite3_db_filename (connection, "main");

    return result;
  }


unsigned long
SpatiaLiteDB::getVersion ()
    throw (DB_Error)
  {
    std::unique_ptr<db::Cursor> cursor (query ("PRAGMA user_version"));

    if (!cursor->moveToNext ())
      {
        throw DB_Error (MEM_FN ("getVersion") "No version set");
      }

    return static_cast<unsigned long>(cursor->getLong (0));
  }


void
SpatiaLiteDB::interrupt ()
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

#if DB_ASYNC_INTERRUPT
    if (connection && !interrupting)
        {
        interrupting = true;        // Don't let the connection close.
        interruptCV.broadcast (*lock);
        }
#else
    if (connection)
        {
        sqlite3_interrupt (connection);
        }
#endif
  }


db::Cursor*
SpatiaLiteDB::query (const char* sql)
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    return new CursorImpl (prepareStatement (sql, connection));
  }


db::Cursor*
SpatiaLiteDB::query (const char* sql,
                     const std::vector<const char*>& args)
    throw (DB_Error)
  {
    std::unique_ptr<CursorImpl> result;

    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    result.reset (new CursorImpl (prepareStatement (sql, connection)));

    result->bindArgs (args);
    return result.release ();
  }


void
SpatiaLiteDB::setTransactionSuccessful ()
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    if (!inTrans)
        {
        throw DB_Error (MEM_FN ("setTransactionSuccessful")
                        "Not in a transaction");
        }

    successfulTrans = true;
  }


void
SpatiaLiteDB::setVersion (unsigned long version)
    throw (DB_Error)
  {
    std::ostringstream strm;

    if (version >= std::numeric_limits<unsigned int>::max())
      {
        strm << MEM_FN ("setVersion") "Version is limited to 32 bits: "
             << version << " is out of range";
        throw DB_Error (strm.str ().c_str ());
      }

    strm << "PRAGMA user_version = " << version;
    execute (strm.str ().c_str ());
  }


}                                       // Close db namespace.
}                                       // Close atakmap namespace.


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


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


void
SpatiaLiteDB::closeConnection ()
    throw (DB_Error)
  {
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        throw db::DB_Error("Failed to acquire lock");

    if (connection)
        {
#if DB_ASYNC_INTERRUPT
        while (interrupting)
            {
            interruptCV.wait (*lock);
            }
#endif

        int response (sqlite3_close_v2 (connection));

        connection = nullptr;
        if (response != SQLITE_OK)
            {
            std::cerr << "\n" << MEM_FN ("closeConnection")
                        << "Error (" << sqlite3_errstr (response)
                        << ") closing connection";
            }
        spatialite_cleanup_ex (cache);
        cache = nullptr;
#if DB_ASYNC_INTERRUPT
        interruptCV.broadcast (*lock);       // Alert interruptor thread.
        while (!finished)
            {
            interruptCV.wait (*lock);
            }
#endif
        }
  }


#if DB_ASYNC_INTERRUPT
void*
SpatiaLiteDB::interruptThreadFn (void* threadData)
  {
    SpatiaLiteDB* self (static_cast<SpatiaLiteDB*> (threadData));

    do {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, self->mutex);
        if (code != TE_Ok) {
            std::cerr << MEM_FN("interruptDB") "Failed to acquire mutex, code="
                << code;
            break;
        }
        while (self->connection)
        {
            while (!self->interrupting)
            {
                self->interruptCV.wait(*lock);
            }

            sqlite3_interrupt(self->connection);
            self->interrupting = false;
            self->interruptCV.broadcast(*lock);
        }
        self->finished = true;
        self->interruptCV.broadcast(*lock);
    } while (false);

    return NULL;
  }
#endif


}                                       // Close db namespace.
}                                       // Close atakmap namespace.
