////============================================================================
////
////    FILE:           FeatureDatabase.cpp
////
////    DESCRIPTION:    Implementation of FeatureDatabase class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 24, 2015  scott           Created.
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


#include "feature/FeatureDatabase.h"

#include <sstream>
#include <string>

#include "db/Query.h"
#include "db/DB_Error.h"
#include "db/Statement.h"
#include "feature/Geometry.h"
#include "port/String.h"
#include "port/StringBuilder.h"
#include "util/AttributeSet.h"

#include "thread/Lock.h"


#define MEM_FN( fn )    "atakmap::feature::FeatureDatabase::" fn ": "

#define TBL_GEO                 "Geometry"
#define COL_GEO_ID              "id"
#define COL_GEO_NAME            "name"
#define COL_GEO_SPL_GEOM        "geom"
#define COL_GEO_VER             "version"
#define COL_GEO_MAX_GSD         "max_gsd"
#define COL_GEO_MIN_GSD         "min_gsd"
#define COL_GEO_VIS             "visible"
#define COL_GEO_FILE_ID         "file_id"
#define COL_GEO_GRP_ID          "group_id"
#define COL_GEO_STY_ID          "style_id"
#define COL_GEO_VIS_VER         "group_visible_version"
#define IDX_GEO_GSD             "IdxGeometryGSD"
#define IDX_GEO_GRP_NAME        "IdxGeometryGroupIdName"
//
// Is this index really necessary?
//
#define IDX_GEO_FILE_ID         "IdxGeometryFileId"

#define TBL_STY                 "style"
#define COL_STY_ID              "id"
#define COL_STY_NAME            "style_name"
#define COL_STY_REP             "style_rep"
#define COL_STY_FILE_ID         "file_id"

#define TBL_GRP                 "groups"
#define COL_GRP_ID              "id"
#define COL_GRP_NAME            "name"
#define COL_GRP_FILE_ID         "file_id"
#define COL_GRP_PROV            "provider"
#define COL_GRP_TYPE            "type"
#define COL_GRP_VER             "version"
#define COL_GRP_MAX_GSD         "max_gsd"
#define COL_GRP_MIN_GSD         "min_gsd"
#define COL_GRP_VIS             "visible"
#define COL_GRP_VIS_CHK         "visible_check"
#define COL_GRP_VIS_VER         "visible_version"
#define IDX_GRP_NAME            "IdxGroupName"

#define COL_INT                 " INTEGER, "
#define COL_INT_KEY_AUTO        " INTEGER PRIMARY KEY AUTOINCREMENT, "
#define COL_INT_LAST            " INTEGER"
#define COL_REAL                " REAL, "
#define COL_REAL_LAST           " REAL"
#define COL_TEXT                " TEXT, "
#define COL_TEXT_LAST           " TEXT"
#define COL_TEXT_NOCASE         " TEXT COLLATE NOCASE, "


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


typedef std::vector<TAK::Engine::Port::String>       StringVector;


struct FeaturesSchemaMgr
  : db::DatabaseWrapper::SchemaManager
  {
    enum
      {
        FEATURE_SCHEMA_VERSION = 9
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
      { return FEATURE_SCHEMA_VERSION; }
  };


}                                       // Close unnamed namespace.


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::FeatureDatabase::Factory
///
///     Concrete factory class for creating FeatureDatabase objects.
///
///=============================================================================


class FeatureDatabase::Factory
  : public DatabaseWrapper::Factory
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Factory ()
        NOTHROWS override
      { }

    //
    // Returns an instance of FeatureDatabase for the supplied file path.
    // Returns a temporary database if the supplied file path is NULL.
    //
    FeatureDatabase*
    getDatabase (const char* filePath)
        const
        NOTHROWS
      {
        db::Database *db = db::openDatabase(filePath);

        try {
            if (filePath)
            {
                std::unique_ptr<db::Cursor> cursor(db->query("PRAGMA journal_mode"));

                if (!cursor->moveToNext())
                {
                    std::cerr << "\nFailed to get journal_mode";
                }
                else if (!std::strcmp(cursor->getString(0), "delete"))
                {
                    cursor.reset(db->query("PRAGMA journal_mode=WAL"));
                    if (!cursor->moveToNext())
                    {
                        std::cerr << "\nFailed to set journal_mode";
                    }
                    else if (std::strcmp(cursor->getString(0), "wal"))
                    {
                        std::cerr << "\nFailed to set journal_mode to WAL";
                    }
                }
            }

            // next two: silently ignore database/cursor errors here
        } catch (db::DB_Error &) {
        } catch (db::Cursor::CursorError &) {
        }

        return dynamic_cast<FeatureDatabase*>(getDatabaseWrapper(db));
      }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  DatabaseWrapper::Factory IMPLEMENTATION
    //==================================


    //
    // Returns an instance of a class derived from DatabaseWrapper for the
    // supplied Database (which will never be NULL).
    //
    DatabaseWrapper*
    createDatabaseWrapper (db::Database* db)
        const
        NOTHROWS override
      { return new FeatureDatabase (db); }
  };


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


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
insertBlobSQL ("INSERT INTO " TBL_GEO
               " (" COL_GEO_FILE_ID
               ", " COL_GEO_GRP_ID
               ", " COL_GEO_NAME
               ", " COL_GEO_SPL_GEOM
               ", " COL_GEO_STY_ID
               ", " COL_GEO_MIN_GSD
               ", " COL_GEO_MAX_GSD
               ", " COL_GEO_VIS
               ", " COL_GEO_VIS_VER
               ", " COL_GEO_VER
               ") VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?)");


const char* const
insertWKB_SQL ("INSERT INTO " TBL_GEO
               " (" COL_GEO_FILE_ID
               ", " COL_GEO_GRP_ID
               ", " COL_GEO_NAME
               ", " COL_GEO_SPL_GEOM
               ", " COL_GEO_STY_ID
               ", " COL_GEO_MIN_GSD
               ", " COL_GEO_MAX_GSD
               ", " COL_GEO_VIS
               ", " COL_GEO_VIS_VER
               ", " COL_GEO_VER
               ") VALUES (?, ?, ?, GeomFromWkb(?, 4326), ?, ?, ?, 1, 0, ?)");


const char* const
insertWKT_SQL ("INSERT INTO " TBL_GEO
               " (" COL_GEO_FILE_ID
               ", " COL_GEO_GRP_ID
               ", " COL_GEO_NAME
               ", " COL_GEO_SPL_GEOM
               ", " COL_GEO_STY_ID
               ", " COL_GEO_MIN_GSD
               ", " COL_GEO_MAX_GSD
               ", " COL_GEO_VIS
               ", " COL_GEO_VIS_VER
               ", " COL_GEO_VER
               ") VALUES (?, ?, ?, GeomFromText(?, 4326), ?, ?, ?, 1, 0, ?)");


const char* const
insertStyleSQL ("INSERT INTO " TBL_STY
                " (" COL_STY_FILE_ID
                ", " COL_STY_REP
                ") VALUES (?, ?)");


const char* const
selectBlobSQL ("SELECT " TBL_GEO "." COL_GEO_ID
               ", " TBL_GEO "." COL_GEO_NAME
               ", " TBL_GEO "." COL_GEO_SPL_GEOM
               ", " TBL_STY "." COL_STY_REP
               ", " TBL_GEO "." COL_GEO_MIN_GSD
               ", " TBL_GEO "." COL_GEO_MAX_GSD
               " FROM " TBL_GEO " LEFT JOIN " TBL_STY
               " ON " TBL_STY "." COL_STY_ID " = " TBL_GEO "." COL_GEO_STY_ID);

const char* const
selectWKB_SQL ("SELECT " TBL_GEO "." COL_GEO_ID
               ", " TBL_GEO "." COL_GEO_NAME
               ", AsBinary(" TBL_GEO "." COL_GEO_SPL_GEOM ")"
               ", " TBL_STY "." COL_STY_REP
               ", " TBL_GEO "." COL_GEO_MIN_GSD
               ", " TBL_GEO "." COL_GEO_MAX_GSD
               " FROM " TBL_GEO " LEFT JOIN " TBL_STY
               " ON " TBL_STY "." COL_STY_ID " = " TBL_GEO "." COL_GEO_STY_ID);

const char* const
selectWKT_SQL ("SELECT " TBL_GEO "." COL_GEO_ID
               ", " TBL_GEO "." COL_GEO_NAME
               ", AsText(" TBL_GEO "." COL_GEO_SPL_GEOM ")"
               ", " TBL_STY "." COL_STY_REP
               ", " TBL_GEO "." COL_GEO_MIN_GSD
               ", " TBL_GEO "." COL_GEO_MAX_GSD
               " FROM " TBL_GEO " LEFT JOIN " TBL_STY
               " ON " TBL_STY "." COL_STY_ID " = " TBL_GEO "." COL_GEO_STY_ID);


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


inline
void
bindID (db::Statement& statement,
        std::size_t index,
        int64_t ID)
  {
    if (ID > 0)
      {
        statement.bind (index, ID);
      }
    else
      {
        statement.bindNULL (index);
      }
  }


void
deleteGroup (db::Database& db,
             int64_t groupID)
  {
    std::unique_ptr<db::Statement> stmt
        (db.compileStatement ("DELETE FROM " TBL_GRP
                              " WHERE " COL_GRP_ID " = ?"));

    stmt->bind (1, groupID);
    stmt->execute ();
    stmt.reset (db.compileStatement ("DELETE FROM " TBL_GEO
                                     " WHERE " COL_GEO_GRP_ID " = ?"));
    stmt->bind (1, groupID);
    stmt->execute ();
  }


///=====================================
///  FeaturesSchemaMgr MEMBER FUNCTIONS
///=====================================


bool
FeaturesSchemaMgr::checkSchemaObjects (db::Database& db)
    const
    NOTHROWS
try
  {
    const std::vector<TAK::Engine::Port::String> tableNames (db::getTableNames (db));
    auto end (tableNames. end ());

    return end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_GEO))
        && end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_STY))
        && end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_GRP));
  }
catch (...)
  { return false; }


void
FeaturesSchemaMgr::createSchemaObjects (db::Database& db)
    const
  {
    db.execute ("CREATE TABLE " TBL_GEO " ("
                COL_GEO_ID              COL_INT_KEY_AUTO
                COL_GEO_FILE_ID         COL_INT
                COL_GEO_GRP_ID          COL_INT
                COL_GEO_STY_ID          COL_INT
                COL_GEO_VER             COL_INT
                COL_GEO_NAME            COL_TEXT_NOCASE
                COL_GEO_MIN_GSD         COL_REAL
                COL_GEO_MAX_GSD         COL_REAL
                COL_GEO_VIS             COL_INT
                COL_GEO_VIS_VER         COL_INT_LAST
                ")");
    db.execute ("CREATE INDEX IF NOT EXISTS " IDX_GEO_GSD " ON "
                TBL_GEO "(" COL_GEO_MIN_GSD ", " COL_GEO_MAX_GSD ")");
    db.execute ("CREATE INDEX IF NOT EXISTS " IDX_GEO_GRP_NAME " ON "
                TBL_GEO "(" COL_GEO_GRP_ID ", " COL_GEO_NAME ")");

    //
    // Is this index really necessary given the index on group_id & name?
    //
    db.execute ("CREATE INDEX IF NOT EXISTS " IDX_GEO_FILE_ID " ON "
                TBL_GEO "(" COL_GEO_FILE_ID ")");

    db.execute ("CREATE TABLE " TBL_STY " ("
                COL_STY_ID              COL_INT_KEY_AUTO
                COL_STY_FILE_ID         COL_INT
                COL_STY_NAME            COL_TEXT
                COL_STY_REP             COL_TEXT_LAST
                ")");

    db.execute ("CREATE TABLE " TBL_GRP " ("
                COL_GRP_ID              COL_INT_KEY_AUTO
                COL_GRP_FILE_ID         COL_INT
                COL_GRP_VER             COL_INT
                COL_GRP_NAME            COL_TEXT_NOCASE
                COL_GRP_PROV            COL_TEXT
                COL_GRP_TYPE            COL_TEXT
                COL_GRP_MIN_GSD         COL_REAL
                COL_GRP_MAX_GSD         COL_REAL
                COL_GRP_VIS             COL_INT
                COL_GRP_VIS_CHK         COL_INT
                COL_GRP_VIS_VER         COL_INT_LAST ")");
    db.execute ("CREATE INDEX IF NOT EXISTS " IDX_GRP_NAME " ON "
                TBL_GRP "(" COL_GRP_NAME ")");

    //
    // Set up spatial column and index.
    //
    std::pair<int, int> spatialiteVersion(atakmap::feature::getSpatialiteVersion(db));
    std::unique_ptr<db::Cursor> cursor
        (db.query (spatialiteVersion.first > 4
                   || (spatialiteVersion.first == 4
                       && spatialiteVersion.second > 0)
                   ? "SELECT InitSpatialMetadata(1)"
                   : "SELECT InitSpatialMetadata()"));

    cursor->moveToNext ();              // Ignore the result.
    cursor.reset (db.query ("SELECT AddGeometryColumn(\'" TBL_GEO
                            "\', \'" COL_GEO_SPL_GEOM
                            "\', 4326, \'GEOMETRY\', \'XY\')"));
    cursor->moveToNext ();              // Ignore the result.
    cursor.reset (db.query ("SELECT CreateSpatialIndex(\'" TBL_GEO
                            "\', \'" COL_GEO_SPL_GEOM "\')"));
    cursor->moveToNext ();              // Ignore the result.
  }


void
FeaturesSchemaMgr::dropSchemaObjects (db::Database& db)
    const
    NOTHROWS
  {
    try
      {
        db.execute ("DROP INDEX IF EXISTS " IDX_GEO_GSD);
        db.execute ("DROP INDEX IF EXISTS " IDX_GEO_GRP_NAME);

        //
        // Is this index really necessary?
        //
        db.execute ("DROP INDEX IF EXISTS " IDX_GEO_FILE_ID);
        db.execute ("DROP INDEX IF EXISTS " IDX_GRP_NAME);

        const std::vector<TAK::Engine::Port::String> tableNames (db::getTableNames (db));
        auto end (tableNames. end ());

        if (end != std::find_if (tableNames.begin (), end,
                                 TAK::Engine::Port::StringEqual
                                     ("idx_" TBL_GEO "_" COL_GEO_SPL_GEOM)))
          {
            std::unique_ptr<db::Cursor> cursor
                (db.query ("SELECT DisableSpatialIndex(\'" TBL_GEO
                           "\', \'" COL_GEO_SPL_GEOM "\')"));

            cursor->moveToNext ();      // Ignore the result.
            db.execute ("DROP TABLE idx_" TBL_GEO "_" COL_GEO_SPL_GEOM);
          }

        const std::vector<TAK::Engine::Port::String> columnNames
            (db::getColumnNames (db, TBL_GEO));

        end = columnNames.end ();
        if (end != std::find_if (columnNames.begin (), end,
                                 TAK::Engine::Port::StringEqual (COL_GEO_SPL_GEOM)))
          {
            std::unique_ptr<db::Cursor> cursor
                (db.query ("SELECT DiscardGeometryColumn(\'" TBL_GEO
                           "\', \'" COL_GEO_SPL_GEOM "\')"));

            cursor->moveToNext ();      // Ignore the result.
          }
        db.execute ("DROP TABLE IF EXISTS File");       // Legacy table?
        db.execute ("DROP TABLE IF EXISTS " TBL_GEO);
        db.execute ("DROP TABLE IF EXISTS " TBL_STY);
        db.execute ("DROP TABLE IF EXISTS " TBL_GRP);
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
namespace feature                       // Open feature namespace.
{


void
FeatureDatabase::beginTransaction ()
  {
    Lock lock(getMutex());
    ThreadID thisThread (Thread_currentThreadID());

    if (transCount && (transThread != thisThread))
      {
        throw db::DB_Error (MEM_FN ("beginTransaction")
                                 "Transaction is held by a different thread");
      }
    if (!transCount)
      {
        getDatabase ().beginTransaction ();
        transThread = thisThread;
        transSuccess = true;
        transInnerSuccess = false;
      }
    else if (transInnerSuccess)
      {
        throw db::DB_Error (MEM_FN ("beginTransaction")
                            "Transaction successful but not yet ended");
      }
    ++transCount;
  }


FeatureDatabase*
FeatureDatabase::createDatabase (const char* filePath)
  {
    static Factory factory;

    return factory.getDatabase (filePath);
  }


void
FeatureDatabase::deleteFeature (int64_t featureID)
  {
    std::unique_ptr<db::Statement> stmt
        (getDatabase ().compileStatement ("DELETE FROM " TBL_GEO
                                          " WHERE " COL_GEO_ID " = ?"));

    stmt->bind (1, featureID);
    stmt->execute ();
  }


void
FeatureDatabase::deleteGroup (int64_t groupID)
  {
    Lock lock(getMutex());
    Transaction trans (*this);

    ::deleteGroup (getDatabase (), groupID);
    setTransactionSuccessful ();
  }


void
FeatureDatabase::deleteGroup (int64_t catalogID,
                              const char* groupName)
  {
    std::vector<const char*> args (2, groupName);
    TAK::Engine::Port::StringBuilder strm;

    strm << catalogID;
    args[1] = strm.c_str();

    db::Database& db (getDatabase ());
    Lock lock(getMutex());
    std::unique_ptr<db::Cursor> cursor
        (db.query ("SELECT " COL_GRP_ID " FROM " TBL_GRP
                   " WHERE " COL_GRP_NAME " = ?"
                   " AND " COL_GRP_FILE_ID " = ?",
                   args));
    Transaction trans (*this);

    while (cursor->moveToNext ())
      {
        ::deleteGroup (db, cursor->getLong (0));
      }
    setTransactionSuccessful ();
  }


void
FeatureDatabase::endTransaction ()
  {
    Lock lock(getMutex());

    if (!transCount)
      {
        throw db::DB_Error (MEM_FN ("endTransaction")
                            "No transaction in effect");
      }
    if (transThread != Thread_currentThreadID())
      {
        throw db::DB_Error (MEM_FN ("endTransaction")
                                 "Transaction is held by a different thread");
      }
    if (transInnerSuccess)
      {
        transInnerSuccess = false;
      }
    else
      {
        transSuccess = false;
      }
    if (!--transCount)
      {
        db::Database& db (getDatabase ());

        if (transSuccess)
          {
            db.setTransactionSuccessful ();
          }
        db.endTransaction ();
      }
  }


void
FeatureDatabase::setTransactionSuccessful ()
  {
    Lock lock(getMutex());

    if (!transCount)
      {
        throw db::DB_Error (MEM_FN ("setTransactionSuccessful")
                            "No transaction in effect");
      }
    if (transThread != Thread_currentThreadID())
      {
        throw db::DB_Error (MEM_FN ("setTransactionSuccessful")
                                 "Transaction is held by a different thread");
      }
    if (transInnerSuccess)
      {
        throw db::DB_Error (MEM_FN ("setTransactionSuccessful")
                            "Transaction already successful");
      }
    transInnerSuccess = true;
  }


///=====================================
///  FeatureDatabase::Cursor MEMBER FUNCTIONS
///=====================================


FeatureDataSource::FeatureDefinition*
FeatureDatabase::Cursor::getFeatureDefinition ()
    const
    throw (CursorError)
try
  {
    typedef FeatureDataSource::FeatureDefinition FeatureDef;

    std::unique_ptr<FeatureDef> result
        (new FeatureDef (getString (colName), util::AttributeSet ()));

    switch (encoding)
      {
      case FeatureDef::WKT:

        result->setGeometry (getString (colGeometry));
        break;

      case FeatureDef::WKB:
      case FeatureDef::BLOB:

        result->setGeometry (getBlob (colGeometry), encoding);
        break;

      default:                  // GEOMETRY not supported.

        throw CursorError ("GEOMETRY encoding not supported");
      }
    result->setStyle (getString (colStyle));

    return result.release ();
  }
catch (const std::invalid_argument& iaExc)
  {
    throw CursorError (iaExc.what ());
  }
catch (const CursorError&)
  { throw; }
catch (...)
  {
    throw CursorError ("Caught unknown exception");
  }

int64_t
FeatureDatabase::addFeature (int64_t groupID,
                             const FeatureDataSource::FeatureDefinition& def,
                             int64_t styleID,   // 0 = no style.
                             double minRes,     // 0 = no minimum.
                             double maxRes)     // 0 = no maximum.
  { return addFeature (0, groupID, def, styleID, minRes, maxRes); }


int64_t
FeatureDatabase::addGroup (const char* provider,
                           const char* type,
                           const char* groupName,
                           double minRes,       // 0 = no minimum.
                           double maxRes)       // 0 = no maximum.
  { return addGroup (0, provider, type, groupName, minRes, maxRes); }


int64_t
FeatureDatabase::addStyle (const char* styleRep)
  { return addStyle (0, styleRep); }


FeatureDatabase::Cursor
FeatureDatabase::queryFeatures
    (FeatureDataSource::FeatureDefinition::Encoding encoding)
  {
    static std::vector<const char*> noArgs;

    return queryFeaturesInternal (encoding, nullptr, noArgs);
  }

}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=====================================
///  FeatureDatabase CONSTANTS
///=====================================


const char* const
FeatureDatabase::TABLE_GEO (TBL_GEO);
const char* const
FeatureDatabase::TABLE_STYLE (TBL_STY);
const char* const
FeatureDatabase::TABLE_GROUP (TBL_GRP);

const char* const
FeatureDatabase::COLUMN_GEO_ID (COL_GEO_ID);
const char* const
FeatureDatabase::COLUMN_GEO_CATALOG_ID (COL_GEO_FILE_ID);
const char* const
FeatureDatabase::COLUMN_GEO_GROUP_ID (COL_GEO_GRP_ID);
const char* const
FeatureDatabase::COLUMN_GEO_STYLE_ID (COL_GEO_STY_ID);
const char* const
FeatureDatabase::COLUMN_GEO_VERSION (COL_GEO_VER);
const char* const
FeatureDatabase::COLUMN_GEO_NAME (COL_GEO_NAME);
const char* const
FeatureDatabase::COLUMN_GEO_SPATIAL_GEOMETRY (COL_GEO_SPL_GEOM);
const char* const
FeatureDatabase::COLUMN_GEO_MAX_GSD (COL_GEO_MAX_GSD);
const char* const
FeatureDatabase::COLUMN_GEO_MIN_GSD (COL_GEO_MIN_GSD);
const char* const
FeatureDatabase::COLUMN_GEO_VISIBILITY (COL_GEO_VIS);
const char* const
FeatureDatabase::COLUMN_GEO_VISIBILITY_VERSION (COL_GEO_VIS_VER);

const char* const
FeatureDatabase::COLUMN_GROUP_ID (COL_GRP_ID);
const char* const
FeatureDatabase::COLUMN_GROUP_CATALOG_ID (COL_GRP_FILE_ID);
const char* const
FeatureDatabase::COLUMN_GROUP_VERSION (COL_GRP_VER);
const char* const
FeatureDatabase::COLUMN_GROUP_NAME (COL_GRP_NAME);
const char* const
FeatureDatabase::COLUMN_GROUP_PROVIDER (COL_GRP_PROV);
const char* const
FeatureDatabase::COLUMN_GROUP_TYPE (COL_GRP_TYPE);
const char* const
FeatureDatabase::COLUMN_GROUP_MAX_GSD (COL_GRP_MAX_GSD);
const char* const
FeatureDatabase::COLUMN_GROUP_MIN_GSD (COL_GRP_MIN_GSD);
const char* const
FeatureDatabase::COLUMN_GROUP_VISIBILITY (COL_GRP_VIS);
const char* const
FeatureDatabase::COLUMN_GROUP_VISIBILITY_CHECK (COL_GRP_VIS_CHK);
const char* const
FeatureDatabase::COLUMN_GROUP_VISIBILITY_VERSION (COL_GRP_VIS_VER);

const char* const
FeatureDatabase::COLUMN_STYLE_ID (COL_STY_ID);
const char* const
FeatureDatabase::COLUMN_STYLE_CATALOG_ID (COL_STY_FILE_ID);
const char* const
FeatureDatabase::COLUMN_STYLE_NAME (COL_STY_NAME);
const char* const
FeatureDatabase::COLUMN_STYLE_REPRESENTATION (COL_STY_REP);


///=====================================
///  FeatureDatabase MEMBER FUNCTIONS
///=====================================


FeatureDatabase::FeatureDatabase (db::Database* database)
  : DatabaseWrapper (database),
    transCount (0),
    transThread (Thread_currentThreadID()),
    transSuccess (false),
    transInnerSuccess (false)
  { addSchemaManager (new FeaturesSchemaMgr); }


int64_t
FeatureDatabase::addFeature (int64_t catalogID,
                             int64_t groupID,
                             const FeatureDataSource::FeatureDefinition& def,
                             int64_t styleID,
                             double minResolution,
                             double maxResolution)
  {
    int64_t result (0);

    if (!def.getRawGeometry ())
      {
        throw std::invalid_argument (MEM_FN ("addFeature")
                                     "Received FeatureDefinition with NULL "
                                     "Geometry");
      }
    if (minResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received negative minResolution");
      }
    if (maxResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received negative maxResolution");
      }

    switch (def.getEncoding ())
      {
      case FeatureDataSource::FeatureDefinition::WKT:

        result = addFeatureWKT (catalogID, groupID, def.getName (),
                                static_cast<const char*> (def.getRawGeometry ()),
                                styleID, minResolution, maxResolution);
        break;

      case FeatureDataSource::FeatureDefinition::WKB:

        result = addFeatureWKB (catalogID, groupID, def.getName (),
                                def.getGeometryByteBuffer (),
                                styleID, minResolution, maxResolution);
        break;

      case FeatureDataSource::FeatureDefinition::BLOB:

        result = addFeatureBlob (catalogID, groupID, def.getName (),
                                 def.getGeometryByteBuffer (),
                                 styleID, minResolution, maxResolution);
        break;

      default:                          // GEOMETRY is converted to a blob.
          {
            std::ostringstream strm;

            static_cast<const Geometry*> (def.getRawGeometry ())->toBlob (strm);

            std::string blobString (strm.str ());
            const auto* blob
                (reinterpret_cast<const unsigned char*> (blobString.data ()));

            result = addFeatureBlob (catalogID, groupID, def.getName (),
                                     std::make_pair (blob,
                                                     blob + blobString.size ()),
                                     styleID, minResolution, maxResolution);
          }
        break;
      }

    return result;
  }


int64_t
FeatureDatabase::addGroup (int64_t catalogID,
                           const char* provider,
                           const char* type,
                           const char* groupName,
                           double minResolution,
                           double maxResolution)
  {
    if (!provider)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received NULL provider");
      }
    if (!type)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received NULL type");
      }
    if (!groupName)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received NULL groupName");
      }
    if (minResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received negative minResolution");
      }
    if (maxResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addGroup")
                                     "Received negative maxResolution");
      }

    std::unique_ptr<db::Statement> stmt
        (getDatabase ().compileStatement ("INSERT INTO " TBL_GRP "("
                                          COL_GRP_FILE_ID ", "
                                          COL_GRP_NAME ", "
                                          COL_GRP_PROV ", "
                                          COL_GRP_TYPE ", "
                                          COL_GRP_VIS ", "
                                          COL_GRP_VIS_CHK ", "
                                          COL_GRP_VIS_VER ", "
                                          COL_GRP_MIN_GSD ", "
                                          COL_GRP_MAX_GSD ", "
                                          COL_GRP_VER ")"
                                          " VALUES(?, ?, ?, ?, 1, 0, 0, ?, ?, ?)"));

    stmt->bind (1, catalogID);
    stmt->bind (2, groupName);
    stmt->bind (3, provider);
    stmt->bind (4, type);
    stmt->bind (5, minResolution);
    stmt->bind (6, maxResolution);
    if (!catalogID)
      {
        stmt->bind (7, 1);              // Non-catalog groups are versioned.
      }

    Lock lock(getMutex());

    stmt->execute ();
    return db::lastInsertRowID (getDatabase ());
  }


int64_t
FeatureDatabase::addStyle (int64_t catalogID,
                           const char* styleRep)
  {
    if (!styleRep)
      {
        throw std::invalid_argument (MEM_FN ("addStyle")
                                     "Received NULL styleRep");
      }

    Lock lock(getMutex());
    int64_t result (db::getNextAutoincrementID (getDatabase (), TBL_STY));

    if (!insertStyleStmt.get ())
      {
        insertStyleStmt.reset (getDatabase ().compileStatement (insertStyleSQL));
      }
    insertStyleStmt->clearBindings ();
    insertStyleStmt->bind (1, catalogID);
    insertStyleStmt->bind (2, styleRep);
    insertStyleStmt->execute ();

    return result;
  }


FeatureDatabase::Cursor
FeatureDatabase::queryFeaturesInternal
    (FeatureDataSource::FeatureDefinition::Encoding encoding,
     const char* where,
     const std::vector<const char*>& whereArgs)
  {
    const char* selectSQL (nullptr);

    switch (encoding)
      {
      case FeatureDataSource::FeatureDefinition::WKT:

        selectSQL = selectWKT_SQL;
        break;

      case FeatureDataSource::FeatureDefinition::WKB:

        selectSQL = selectWKB_SQL;
        break;

      case FeatureDataSource::FeatureDefinition::BLOB:

        selectSQL = selectBlobSQL;
        break;

      default:                          // GEOMETRY is not supported.

        throw std::invalid_argument (MEM_FN ("queryFeaturesInternal")
                                     "GEOMETRY encoding is not supported");
      }

    TAK::Engine::Port::String selectWhereSQL;
    if(where) {
        std::ostringstream strm;
        strm << selectSQL << " " << where;
        selectWhereSQL = strm.str().c_str();
    }

    if (selectWhereSQL)
      {
        selectSQL = selectWhereSQL;
      }
    std::unique_ptr<db::Cursor> result(getDatabase ().query (selectSQL, whereArgs));
    return Cursor (std::move(result), encoding);
  }

std::pair<int, int>
getSpatialiteVersion(db::Database& db)
  {
    int majorVersion(-1);
    int minorVersion(-1);
    std::unique_ptr<db::Cursor> cursor
        (db.query("SELECT spatialite_version()"));

    if (cursor->moveToNext())
    {
        std::istringstream strm(cursor->getString(0));
        char dot(0);

        if (!(((strm >> majorVersion).get(dot)) >> minorVersion && dot == '.'))
        {
            majorVersion = minorVersion = -1;
        }
    }

    return std::make_pair(majorVersion, minorVersion);
  }

std::pair<int, int>
getSpatialiteVersion(TAK::Engine::DB::Database2& db)
  {
    using namespace TAK::Engine::DB;
    using namespace TAK::Engine::Util;

    int majorVersion(-1);
    int minorVersion(-1);

    TAK::Engine::Util::TAKErr code;
    QueryPtr cursor(nullptr, nullptr);
    code = db.query(cursor, "SELECT spatialite_version()");

    code = cursor->moveToNext();
    if (code == TE_Ok)
    {
        const char *verStr;
        code = cursor->getString(&verStr, 0);
        if (code == TE_Ok) {
            std::istringstream strm(verStr);
            char dot(0);

            if (!(((strm >> majorVersion).get(dot)) >> minorVersion && dot == '.'))
            {
                majorVersion = minorVersion = -1;
            }
        }
    }

    return std::make_pair(majorVersion, minorVersion);
  }



}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


int64_t
FeatureDatabase::addFeatureBlob (int64_t catalogID,
                                 int64_t groupID,
                                 const char* featureName,
                                 const ByteBuffer& blob,
                                 int64_t styleID,
                                 double minResolution,
                                 double maxResolution)
  {
    if (!featureName)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureBlob")
                                     "Received NULL featureName");
      }
    if (minResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureBlob")
                                     "Received negative minResolution");
      }
    if (maxResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureBlob")
                                     "Received negative maxResolution");
      }

    db::Database& db (getDatabase ());
    Lock lock(getMutex());

    if (!insertBlobStmt.get ())
      {
        insertBlobStmt.reset (db.compileStatement (insertBlobSQL));
      }

    insertBlobStmt->clearBindings ();
    bindID (*insertBlobStmt, 1, catalogID);
    insertBlobStmt->bind (2, groupID);
    insertBlobStmt->bind (3, featureName);
    insertBlobStmt->bind (4, blob);
    bindID (*insertBlobStmt, 5, styleID);
    insertBlobStmt->bind (6, minResolution);
    insertBlobStmt->bind (7, maxResolution);
    if (!catalogID)
      {
        insertBlobStmt->bind (8, 1);    // Non-catalog features are versioned.
      }
    insertBlobStmt->execute ();

    return db::lastInsertRowID (db);
  }


int64_t
FeatureDatabase::addFeatureWKB (int64_t catalogID,
                                int64_t groupID,
                                const char* featureName,
                                const ByteBuffer& blob,
                                int64_t styleID,
                                double minResolution,
                                double maxResolution)
  {
    if (!featureName)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureWKB")
                                     "Received NULL featureName");
      }
    if (minResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureWKB")
                                     "Received negative minResolution");
      }
    if (maxResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureWKB")
                                     "Received negative maxResolution");
      }

    db::Database& db (getDatabase ());
    Lock lock(getMutex());

    if (!insertWKB_Stmt.get ())
      {
        insertWKB_Stmt.reset (db.compileStatement (insertWKB_SQL));
      }

    insertWKB_Stmt->clearBindings ();
    bindID (*insertWKB_Stmt, 1, catalogID);
    insertWKB_Stmt->bind (2, groupID);
    insertWKB_Stmt->bind (3, featureName);
    insertWKB_Stmt->bind (4, blob);
    bindID (*insertWKB_Stmt, 5, styleID);
    insertWKB_Stmt->bind (6, minResolution);
    insertWKB_Stmt->bind (7, maxResolution);
    if (!catalogID)
      {
        insertWKB_Stmt->bind (8, 1);    // Non-catalog features are versioned.
      }
    insertWKB_Stmt->execute ();

    return db::lastInsertRowID (db);
  }


int64_t
FeatureDatabase::addFeatureWKT (int64_t catalogID,
                                int64_t groupID,
                                const char* featureName,
                                const char* geometryWKT,
                                int64_t styleID,
                                double minResolution,
                                double maxResolution)
  {
    if (!featureName)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureWKT")
                                     "Received NULL featureName");
      }
    if (minResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureWKT")
                                     "Received negative minResolution");
      }
    if (maxResolution < 0)
      {
        throw std::invalid_argument (MEM_FN ("addFeatureWKT")
                                     "Received negative maxResolution");
      }

    db::Database& db (getDatabase ());
    Lock lock(getMutex());

    if (!insertWKT_Stmt.get ())
      {
        insertWKT_Stmt.reset (db.compileStatement (insertWKT_SQL));
      }

    insertWKT_Stmt->clearBindings ();
    bindID (*insertWKT_Stmt, 1, catalogID);
    insertWKT_Stmt->bind (2, groupID);
    insertWKT_Stmt->bind (3, featureName);
    insertWKT_Stmt->bind (4, geometryWKT);
    bindID (*insertWKT_Stmt, 5, styleID);
    insertWKT_Stmt->bind (6, minResolution);
    insertWKT_Stmt->bind (7, maxResolution);
    if (!catalogID)
      {
        insertWKB_Stmt->bind (8, 1);    // Non-catalog features are versioned.
      }
    insertWKT_Stmt->execute ();

    return db::lastInsertRowID (db);
  }


///=====================================
///  FeatureDatabase::Cursor MEMBER FUNCTIONS
///=====================================


FeatureDatabase::Cursor::Cursor
    (const std::shared_ptr<db::Cursor> &subject,
     FeatureDataSource::FeatureDefinition::Encoding encoding)
  : CursorProxy (subject),
    encoding (encoding),
    colID (subject->getColumnIndex (COL_GEO_ID)),
    colName (subject->getColumnIndex (COL_GEO_NAME)),
    colGeometry (subject->getColumnIndex (COL_GEO_SPL_GEOM)),
    colStyle (subject->getColumnIndex (COL_STY_REP)),
    colMinResolution (subject->getColumnIndex (COL_GEO_MIN_GSD)),
    colMaxResolution (subject->getColumnIndex (COL_GEO_MAX_GSD))
  { }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
