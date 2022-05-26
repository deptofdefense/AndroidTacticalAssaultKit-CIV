
//NOTE: This is a modified version of FeatureDatabase.cpp intended to add temporary
//      support for storing AttributeSets until a more perminent solution arrives.


////============================================================================
////
////    FILE:           AttributedFeatureDatabase.cpp
////
////    DESCRIPTION:    Implementation of AttributedFeatureDatabase class.
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


#include "feature/FeatureDatabase.h" // getSpatialiteVersion
#include "feature/AttributedFeatureDatabase.h"

#include <sstream>
#include <string>

#include "db/Cursor2.h"
#include "db/DB_Error.h"
#include "db/Statement.h"
#include "feature/Geometry.h"
#include "util/AttributeSet.h"
#include "util/IO.h"

#include "string/String.hh"
#include "string/StringEqual.hh"
#include "string/StringHacks.hh"
#include "thread/Lock.h"


#define MEM_FN( fn )    "atakmap::feature::AttributedFeatureDatabase::" fn ": "

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
#define COL_GEO_META            "meta"
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
#define COL_BLOB                " BLOB, "
#define COL_BLOB_LAST           " TEXT"


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


typedef std::vector<PGSC::String>       StringVector;


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
        throw ();

    void
    createSchemaObjects (db::Database&)
        const;

    void
    dropSchemaObjects (db::Database&)
        const
        throw ();

    unsigned long
    getSchemaVersion ()
        const
        throw ()
      { return FEATURE_SCHEMA_VERSION; }
  };


}                                       // Close unnamed namespace.


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::AttributedFeatureDatabase::Factory
///
///     Concrete factory class for creating AttributedFeatureDatabase objects.
///
///=============================================================================


class AttributedFeatureDatabase::Factory
  : public DatabaseWrapper::Factory
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Factory ()
        throw ()
      { }

    //
    // Returns an instance of AttributedFeatureDatabase for the supplied file path.
    // Returns a temporary database if the supplied file path is NULL.
    //
    AttributedFeatureDatabase*
    getDatabase (const char* filePath)
        const
        throw ()
      {
        db::Database *db = db::openDatabase(filePath);

        try {
            if (filePath)
            {
                std::auto_ptr<db::Cursor> cursor(db->query("PRAGMA journal_mode"));

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
        } catch (db::DB_Error &ignored) {
        } catch (db::Cursor::CursorError &ignored) {
        }

        return dynamic_cast<AttributedFeatureDatabase*>(getDatabaseWrapper(db));
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
        throw ()
      { return new AttributedFeatureDatabase (db); }
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
               ", " COL_GEO_META
               ") VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?)");


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
               ", " COL_GEO_META
               ") VALUES (?, ?, ?, GeomFromWkb(?, 4326), ?, ?, ?, 1, 0, ?, ?)");


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
               ", " COL_GEO_META
               ") VALUES (?, ?, ?, GeomFromText(?, 4326), ?, ?, ?, 1, 0, ?, ?)");


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
    std::auto_ptr<db::Statement> stmt
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
    throw ()
try
  {
    const std::vector<PGSC::String> tableNames (db::getTableNames (db));
    std::vector<PGSC::String>::const_iterator end (tableNames. end ());

    return end != std::find_if (tableNames.begin (), end,
                                PGSC::StringEqual (TBL_GEO))
        && end != std::find_if (tableNames.begin (), end,
                                PGSC::StringEqual (TBL_STY))
        && end != std::find_if (tableNames.begin (), end,
                                PGSC::StringEqual (TBL_GRP));
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
                COL_GEO_VIS_VER         COL_INT
                COL_GEO_META            COL_BLOB_LAST
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
    std::auto_ptr<db::Cursor> cursor
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
    throw ()
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

        const std::vector<PGSC::String> tableNames (db::getTableNames (db));
        std::vector<PGSC::String>::const_iterator end (tableNames. end ());

        if (end != std::find_if (tableNames.begin (), end,
                                 PGSC::StringEqual
                                     ("idx_" TBL_GEO "_" COL_GEO_SPL_GEOM)))
          {
            std::auto_ptr<db::Cursor> cursor
                (db.query ("SELECT DisableSpatialIndex(\'" TBL_GEO
                           "\', \'" COL_GEO_SPL_GEOM "\')"));

            cursor->moveToNext ();      // Ignore the result.
            db.execute ("DROP TABLE idx_" TBL_GEO "_" COL_GEO_SPL_GEOM);
          }

        const std::vector<PGSC::String> columnNames
            (db::getColumnNames (db, TBL_GEO));

        end = columnNames.end ();
        if (end != std::find_if (columnNames.begin (), end,
                                 PGSC::StringEqual (COL_GEO_SPL_GEOM)))
          {
            std::auto_ptr<db::Cursor> cursor
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


    void serializeBytes(std::vector<uint8_t> &buffer, const void *bytes, size_t count) {
        size_t i = buffer.size();
        buffer.insert(buffer.end(), count, 0);
        memcpy(&buffer[i], bytes, count);
    }
    
    void serializeInt32(std::vector<uint8_t> &buffer, int32_t value) {
        serializeBytes(buffer, &value, sizeof(value));
    }
    
    void serializeInt64(std::vector<uint8_t> &buffer, int64_t value) {
        serializeBytes(buffer, &value, sizeof(value));
    }
    
    void serializeBlob(std::vector<uint8_t> &buffer, const atakmap::util::AttributeSet::Blob &blob) {
        size_t size = blob.second - blob.first;
        if (size > 0x7fffffff) {
            //XXX--
            serializeInt32(buffer, 0);
        } else {
            serializeInt32(buffer, size & 0x7fffffff);
            serializeBytes(buffer, blob.first, size);
        }
    }
    
    void serializeString(std::vector<uint8_t> &buffer, const char *str) {
        size_t len = strlen(str);
        int32_t lenTrunc = len & 0x7fffffff;
        serializeInt32(buffer, lenTrunc);
        serializeBytes(buffer, str, lenTrunc);
    }
    
    void serializeDouble(std::vector<uint8_t> &buffer, double value) {
        serializeBytes(buffer, &value, sizeof(value));
    }
    
    void serializeIntArray(std::vector<uint8_t> &buffer, std::pair<const int*, const int*> ptrs) {
        size_t count = ptrs.second - ptrs.first;
        if (count <= 0x7fffffff) {
            serializeInt32(buffer, count & 0x7fffffff);
            for (size_t i = 0; i < count; ++i) {
                serializeInt32(buffer, ptrs.first[i]);
            }
        } else {
            // XXX-
            serializeInt32(buffer, 0);
        }
    }
    
    void serializeLongArray(std::vector<uint8_t> &buffer, std::pair<const int64_t*, const int64_t*> ptrs) {
        size_t count = ptrs.second - ptrs.first;
        if (count <= 0x7fffffff) {
            serializeInt32(buffer, count & 0x7fffffff);
            for (size_t i = 0; i < count; ++i) {
                serializeInt64(buffer, ptrs.first[i]);
            }
        } else {
            // XXX-
            serializeInt32(buffer, 0);
        }
    }
    
    void serializeDoubleArray(std::vector<uint8_t> &buffer, std::pair<const double*, const double*> ptrs) {
        size_t count = ptrs.second - ptrs.first;
        if (count <= 0x7fffffff) {
            serializeInt32(buffer, count & 0x7fffffff);
            for (size_t i = 0; i < count; ++i) {
                serializeDouble(buffer, ptrs.first[i]);
            }
        } else {
            // XXX-
            serializeInt32(buffer, 0);
        }
    }
    
    void serializeStringArray(std::vector<uint8_t> &buffer, std::pair<const char* const*, const char* const*> ptrs) {
        size_t count = ptrs.second - ptrs.first;
        if (count <= 0x7fffffff) {
            serializeInt32(buffer, count & 0x7fffffff);
            for (size_t i = 0; i < count; ++i) {
                serializeString(buffer, ptrs.first[i]);
            }
        } else {
            // XXX-
            serializeInt32(buffer, 0);
        }
    }
    
    void serializeBlobArray(std::vector<uint8_t> &buffer, std::pair<const atakmap::util::AttributeSet::Blob*, const atakmap::util::AttributeSet::Blob*> ptrs) {
        size_t count = ptrs.second - ptrs.first;
        if (count <= 0x7fffffff) {
            serializeInt32(buffer, count & 0x7fffffff);
            for (size_t i = 0; i < count; ++i) {
                serializeBlob(buffer, ptrs.first[i]);
            }
        } else {
            // XXX-
            serializeInt32(buffer, 0);
        }
    }

    
    void serializeAttributeSet(std::vector<uint8_t> &buffer, const atakmap::util::AttributeSet &attrs) {
        
        using namespace atakmap::util;
        
        std::vector<const char *> attrNames = attrs.getAttributeNames();
        serializeInt32(buffer, attrNames.size() & 0x7fffffff);
        
        for (const char *name : attrNames) {
            AttributeSet::Type type = attrs.getAttributeType(name);

            serializeString(buffer, name);
            buffer.push_back(type);
            
            switch (type) {
                case AttributeSet::STRING: serializeString(buffer, attrs.getString(name)); break;
                case AttributeSet::INT: serializeInt32(buffer, attrs.getInt(name)); break;
                case AttributeSet::DOUBLE: serializeDouble(buffer, attrs.getDouble(name)); break;
                case AttributeSet::LONG: serializeInt64(buffer, attrs.getLong(name)); break;
                case AttributeSet::BLOB: serializeBlob(buffer, attrs.getBlob(name)); break;
                case AttributeSet::ATTRIBUTE_SET: serializeAttributeSet(buffer, attrs.getAttributeSet(name)); break;
                case AttributeSet::INT_ARRAY: serializeIntArray(buffer, attrs.getIntArray(name)); break;
                case AttributeSet::LONG_ARRAY: serializeLongArray(buffer, attrs.getLongArray(name)); break;
                case AttributeSet::DOUBLE_ARRAY: serializeDoubleArray(buffer, attrs.getDoubleArray(name)); break;
                case AttributeSet::STRING_ARRAY: serializeStringArray(buffer, attrs.getStringArray(name)); break;
                case AttributeSet::BLOB_ARRAY: //serializeBlobArray(buffer, attrs.getBlobArray(name));
                    break;
            }
        }
    }
    
    int32_t deserializeInt32(atakmap::util::DataInput &input) {
        int32_t value = 0;
        input.read(reinterpret_cast<uint8_t *>(&value), 4);
        return value;
    }
    
    int64_t deserializeInt64(atakmap::util::DataInput &input) {
        int64_t value = 0;
        input.read(reinterpret_cast<uint8_t *>(&value), 4);
        return value;
    }
    
    std::string deserializeString(atakmap::util::DataInput &input) {
        int32_t len = deserializeInt32(input);
        std::string str;
        str.insert(0, len, ' ');
        input.read(reinterpret_cast<uint8_t *>(&str[0]), len);
        return str;
    }
    
    atakmap::util::AttributeSet::Type deserializeType(atakmap::util::DataInput &input) {
        atakmap::util::AttributeSet::Type type;
        uint8_t byte = 0;
        input.read(&byte, 1);
        type = static_cast<atakmap::util::AttributeSet::Type>(byte);
        return type;
    }
    
    double deserializeDouble(atakmap::util::DataInput &input) {
        double value = 0;
        input.read(reinterpret_cast<uint8_t *>(&value), sizeof(value));
        return value;
    }
    
    atakmap::util::BlobImpl deserializeBlob(atakmap::util::DataInput &input) {
        int32_t len = deserializeInt32(input);
        uint8_t *data = new uint8_t[len];
        input.read(data, len);
        return atakmap::util::makeBlobWithDeleteCleanup(data, data + len);
    }
    
    std::vector<int> deserializeIntArray(atakmap::util::DataInput &input) {
        std::vector<int> result;
        int32_t len = deserializeInt32(input);
        result.reserve(len);
        for (int32_t i = 0; i < len; ++i) {
            int value = deserializeInt32(input);
            result.push_back(value);
        }
        return result;
    }
    
    std::vector<int64_t> deserializeLongArray(atakmap::util::DataInput &input) {
        std::vector<int64_t> result;
        int32_t len = deserializeInt32(input);
        result.reserve(len);
        for (int32_t i = 0; i < len; ++i) {
            long value = deserializeInt64(input);
            result.push_back(value);
        }
        return result;
    }
    
    std::vector<double> deserializeDoubleArray(atakmap::util::DataInput &input) {
        std::vector<double> result;
        int32_t len = deserializeInt32(input);
        result.reserve(len);
        for (int32_t i = 0; i < len; ++i) {
            double value = deserializeDouble(input);
            result.push_back(value);
        }
        return result;
    }
    
    std::vector<const char *> deserializeStringArray(atakmap::util::DataInput &input) {
        std::vector<const char *> result;
        int32_t len = deserializeInt32(input);
        result.reserve(len);
        for (int32_t i = 0; i < len; ++i) {
            std::string str = deserializeString(input);
            result.push_back(strdup(str.c_str()));
        }
        return result;
    }
    
    
    void deserializeAttributeSet(atakmap::util::DataInput &input, atakmap::util::AttributeSet &output) {
        
        int32_t count = deserializeInt32(input);
        for (int32_t i = 0; i < count; ++i) {
            
            std::string name = deserializeString(input);
            atakmap::util::AttributeSet::Type type = deserializeType(input);
            switch (type) {
                case atakmap::util::AttributeSet::STRING: output.setString(name.c_str(), deserializeString(input).c_str()); break;
                case atakmap::util::AttributeSet::INT: output.setInt(name.c_str(), deserializeInt32(input)); break;
                case atakmap::util::AttributeSet::LONG: output.setLong(name.c_str(), deserializeInt64(input)); break;
                case atakmap::util::AttributeSet::DOUBLE: output.setDouble(name.c_str(), deserializeDouble(input)); break;
                    
                case atakmap::util::AttributeSet::BLOB: {
                    atakmap::util::BlobImpl blob = deserializeBlob(input);
                    output.setBlob(name.c_str(), std::make_pair(blob.first, blob.second));
                }
                    break;
                    
                case atakmap::util::AttributeSet::ATTRIBUTE_SET: {
                    atakmap::util::AttributeSet attrs;
                    deserializeAttributeSet(input, attrs);
                    output.setAttributeSet(name.c_str(), attrs);
                }
                    
                    break;
                    
                case atakmap::util::AttributeSet::INT_ARRAY: {
                    std::vector<int> arr = deserializeIntArray(input);
                    output.setIntArray(name.c_str(), std::make_pair(arr.data(), arr.data() + arr.size()));
                }
                    break;
                    
                case atakmap::util::AttributeSet::LONG_ARRAY: {
                    std::vector<int64_t> arr = deserializeLongArray(input);
                    output.setLongArray(name.c_str(), std::make_pair(arr.data(), arr.data() + arr.size()));
                }
                    break;
                    
                case atakmap::util::AttributeSet::DOUBLE_ARRAY: {
                    std::vector<double> arr = deserializeDoubleArray(input);
                    output.setDoubleArray(name.c_str(), std::make_pair(arr.data(), arr.data() + arr.size()));
                }
                    break;
                    
                case atakmap::util::AttributeSet::STRING_ARRAY: {
                    std::vector<const char *> arr = deserializeStringArray(input);
                    output.setStringArray(name.c_str(), std::make_pair(arr.data(), arr.data() + arr.size()));
                }
                    break;
                    
                case atakmap::util::AttributeSet::BLOB_ARRAY:
                    break;
            }
            
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
namespace feature                       // Open feature namespace.
{


void
AttributedFeatureDatabase::beginTransaction ()
  {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");

    ThreadID thisThread (Thread_currentThreadID ());

    if (transCount && (transThread != thisThread))
      {
        throw db::DB_Error(MEM_FN ("beginTransaction")
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


AttributedFeatureDatabase*
AttributedFeatureDatabase::createDatabase (const char* filePath)
  {
    static Factory factory;

    return factory.getDatabase (filePath);
  }


void
AttributedFeatureDatabase::deleteFeature (int64_t featureID)
  {
    std::auto_ptr<db::Statement> stmt
        (getDatabase ().compileStatement ("DELETE FROM " TBL_GEO
                                          " WHERE " COL_GEO_ID " = ?"));

    stmt->bind (1, featureID);
    stmt->execute ();
  }


void
AttributedFeatureDatabase::deleteGroup (int64_t groupID)
  {
      TAKErr code(TE_Ok);
      LockPtr lock(NULL, NULL);
      code = Lock_create(lock, getMutex());
      if (code != TE_Ok)
          throw db::DB_Error(MEM_FN("beginTransaction")
          "Failed to acquire lock");
    Transaction trans (*this);

    ::deleteGroup (getDatabase (), groupID);
    setTransactionSuccessful ();
  }


void
AttributedFeatureDatabase::deleteGroup (int64_t catalogID,
                              const char* groupName)
  {
    std::vector<const char*> args (2, groupName);
    std::ostringstream strm;

    strm << catalogID;
    args[1] = strm.str ().c_str ();

    db::Database& db (getDatabase ());
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");
    std::auto_ptr<db::Cursor> cursor
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
AttributedFeatureDatabase::endTransaction ()
  {
      TAKErr code(TE_Ok);
      LockPtr lock(NULL, NULL);
      code = Lock_create(lock, getMutex());
      if (code != TE_Ok)
          throw db::DB_Error(MEM_FN("beginTransaction")
          "Failed to acquire lock");

    if (!transCount)
      {
        throw db::DB_Error (MEM_FN ("endTransaction")
                            "No transaction in effect");
      }
    if (transThread != Thread_currentThreadID ())
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
AttributedFeatureDatabase::setTransactionSuccessful ()
  {
      TAKErr code(TE_Ok);
      LockPtr lock(NULL, NULL);
      code = Lock_create(lock, getMutex());
      if (code != TE_Ok)
          throw db::DB_Error(MEM_FN("beginTransaction")
          "Failed to acquire lock");

    if (!transCount)
      {
        throw db::DB_Error (MEM_FN ("setTransactionSuccessful")
                            "No transaction in effect");
      }
    if (transThread != Thread_currentThreadID ())
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
///  AttributedFeatureDatabase::Cursor MEMBER FUNCTIONS
///=====================================


FeatureDataSource::FeatureDefinition*
AttributedFeatureDatabase::Cursor::getFeatureDefinition ()
    const
    throw (CursorError)
try
  {
    typedef FeatureDataSource::FeatureDefinition FeatureDef;

    std::auto_ptr<FeatureDef> result
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
///  AttributedFeatureDatabase CONSTANTS
///=====================================


const char* const
AttributedFeatureDatabase::TABLE_GEO (TBL_GEO);
const char* const
AttributedFeatureDatabase::TABLE_STYLE (TBL_STY);
const char* const
AttributedFeatureDatabase::TABLE_GROUP (TBL_GRP);

const char* const
AttributedFeatureDatabase::COLUMN_GEO_ID (COL_GEO_ID);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_CATALOG_ID (COL_GEO_FILE_ID);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_GROUP_ID (COL_GEO_GRP_ID);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_STYLE_ID (COL_GEO_STY_ID);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_VERSION (COL_GEO_VER);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_NAME (COL_GEO_NAME);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_SPATIAL_GEOMETRY (COL_GEO_SPL_GEOM);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_MAX_GSD (COL_GEO_MAX_GSD);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_MIN_GSD (COL_GEO_MIN_GSD);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_VISIBILITY (COL_GEO_VIS);
const char* const
AttributedFeatureDatabase::COLUMN_GEO_VISIBILITY_VERSION (COL_GEO_VIS_VER);

const char* const
AttributedFeatureDatabase::COLUMN_GROUP_ID (COL_GRP_ID);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_CATALOG_ID (COL_GRP_FILE_ID);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_VERSION (COL_GRP_VER);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_NAME (COL_GRP_NAME);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_PROVIDER (COL_GRP_PROV);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_TYPE (COL_GRP_TYPE);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_MAX_GSD (COL_GRP_MAX_GSD);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_MIN_GSD (COL_GRP_MIN_GSD);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_VISIBILITY (COL_GRP_VIS);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_VISIBILITY_CHECK (COL_GRP_VIS_CHK);
const char* const
AttributedFeatureDatabase::COLUMN_GROUP_VISIBILITY_VERSION (COL_GRP_VIS_VER);

const char* const
AttributedFeatureDatabase::COLUMN_STYLE_ID (COL_STY_ID);
const char* const
AttributedFeatureDatabase::COLUMN_STYLE_CATALOG_ID (COL_STY_FILE_ID);
const char* const
AttributedFeatureDatabase::COLUMN_STYLE_NAME (COL_STY_NAME);
const char* const
AttributedFeatureDatabase::COLUMN_STYLE_REPRESENTATION (COL_STY_REP);


///=====================================
///  AttributedFeatureDatabase MEMBER FUNCTIONS
///=====================================


AttributedFeatureDatabase::AttributedFeatureDatabase (db::Database* database)
  : DatabaseWrapper (database),
    transCount (0),
    transThread (Thread_currentThreadID()),
    transSuccess (false),
    transInnerSuccess (false)
  { addSchemaManager (new FeaturesSchemaMgr); }


int64_t
AttributedFeatureDatabase::addFeature (int64_t catalogID,
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
                                def.getAttributes(),
                                styleID, minResolution, maxResolution);
        break;

      case FeatureDataSource::FeatureDefinition::WKB:

        result = addFeatureWKB (catalogID, groupID, def.getName (),
                                def.getGeometryByteBuffer (),
                                def.getAttributes(),
                                styleID, minResolution, maxResolution);
        break;

      case FeatureDataSource::FeatureDefinition::BLOB:

        result = addFeatureBlob (catalogID, groupID, def.getName (),
                                 def.getGeometryByteBuffer (),
                                 def.getAttributes(),
                                 styleID, minResolution, maxResolution);
        break;

      default:                          // GEOMETRY is converted to a blob.
          {
            std::ostringstream strm;

            static_cast<const Geometry*> (def.getRawGeometry ())->toBlob (strm);

            std::string blobString (strm.str ());
            const unsigned char* blob
                (reinterpret_cast<const unsigned char*> (blobString.data ()));

            result = addFeatureBlob (catalogID, groupID, def.getName (),
                                     std::make_pair (blob,
                                                     blob + blobString.size ()),
                                     def.getAttributes(),
                                     styleID, minResolution, maxResolution);
          }
        break;
      }

    return result;
  }


int64_t
AttributedFeatureDatabase::addGroup (int64_t catalogID,
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

    std::auto_ptr<db::Statement> stmt
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

    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");

    stmt->execute ();
    return db::lastInsertRowID (getDatabase ());
  }


int64_t
AttributedFeatureDatabase::addStyle (int64_t catalogID,
                           const char* styleRep)
  {
    if (!styleRep)
      {
        throw std::invalid_argument (MEM_FN ("addStyle")
                                     "Received NULL styleRep");
      }

    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");
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


AttributedFeatureDatabase::Cursor
AttributedFeatureDatabase::queryFeaturesInternal
    (FeatureDataSource::FeatureDefinition::Encoding encoding,
     const char* where,
     const std::vector<const char*>& whereArgs)
  {
    const char* selectSQL (NULL);

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

    PGSC::String selectWhereSQL (where
                                 ? PGSC::catStrings (selectSQL, " ", where)
                                 : NULL);

    if (selectWhereSQL)
      {
        selectSQL = selectWhereSQL;
      }
    return Cursor (getDatabase ().query (selectSQL, whereArgs), encoding);
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
AttributedFeatureDatabase::addFeatureBlob (int64_t catalogID,
                                 int64_t groupID,
                                 const char* featureName,
                                 const ByteBuffer& blob,
                                 const util::AttributeSet &attrs,
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
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");

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
    
      std::vector<uint8_t> buffer;
      serializeAttributeSet(buffer, attrs);
      db::Statement::Blob attrBlob = atakmap::util::makeBlob(&buffer.front(), &buffer.back() + 1);
      insertBlobStmt->bind(9, attrBlob);
      
    insertBlobStmt->execute ();

    return db::lastInsertRowID (db);
  }


int64_t
AttributedFeatureDatabase::addFeatureWKB (int64_t catalogID,
                                int64_t groupID,
                                const char* featureName,
                                const ByteBuffer& blob,
                                          const util::AttributeSet &attrs,
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
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");

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
      
      std::vector<uint8_t> buffer;
      serializeAttributeSet(buffer, attrs);
      db::Statement::Blob attrBlob = atakmap::util::makeBlob(&buffer.front(), &buffer.back() + 1);
      insertWKB_Stmt->bind(9, attrBlob);
      
    insertWKB_Stmt->execute ();

    return db::lastInsertRowID (db);
  }


int64_t
AttributedFeatureDatabase::addFeatureWKT (int64_t catalogID,
                                int64_t groupID,
                                const char* featureName,
                                const char* geometryWKT,
                                          const util::AttributeSet &attrs,
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
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, getMutex());
    if (code != TE_Ok)
        throw db::DB_Error(MEM_FN("beginTransaction")
        "Failed to acquire lock");

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
      
      std::vector<uint8_t> buffer;
      serializeAttributeSet(buffer, attrs);
      db::Statement::Blob attrBlob = atakmap::util::makeBlob(&buffer.front(), &buffer.back() + 1);
      insertWKT_Stmt->bind(9, attrBlob);
      
    insertWKT_Stmt->execute ();

    return db::lastInsertRowID (db);
  }


    void AttributedFeatureDatabase::deserializeAttributeSet(atakmap::util::AttributeSet &attrSet, atakmap::util::BlobImpl &blob) {
        atakmap::util::MemoryInput input;
        input.open(blob.first, static_cast<size_t>(blob.second - blob.first));
        ::deserializeAttributeSet(input, attrSet);
    }
    
    void AttributedFeatureDatabase::serializeAttributeSet(std::vector<uint8_t> &buffer, const atakmap::util::AttributeSet &attrs) {
        ::serializeAttributeSet(buffer, attrs);
    }
    
///=====================================
///  AttributedFeatureDatabase::Cursor MEMBER FUNCTIONS
///=====================================


AttributedFeatureDatabase::Cursor::Cursor
    (db::Cursor* subject,
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
