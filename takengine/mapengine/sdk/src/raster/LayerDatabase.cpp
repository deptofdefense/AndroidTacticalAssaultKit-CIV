////============================================================================
////
////    FILE:           LayerDatabase.cpp
////
////    DESCRIPTION:    Implementation of LayerDatabase class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 24, 2014  scott           Created.
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


#include "raster/LayerDatabase.h"

#include <cmath>
#include <memory>
#include <sstream>
#include <string>
#include <utility>

#include "core/GeoPoint.h"
#include "db/Database.h"
#include "db/Statement.h"
#include "feature/Geometry.h"
#include "raster/DatasetDescriptor.h"
#include "port/String.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Memory.h"

#include "thread/Lock.h"
#include "thread/Thread.h"


#define MEM_FN( fn )    "atakmap::raster::LayerDatabase::" fn ": "

#define TBL_LAY                 "layers"
#define COL_LAY_ID              "id"
#define COL_LAY_PATH            "path"
#define COL_LAY_CAT_LINK        "cataloglink"
#define COL_LAY_INFO            "info"
#define COL_LAY_NAME            "name"
#define COL_LAY_DS_TYPE         "datasettype"
#define COL_LAY_PROV            "provider"
#define COL_LAY_SRID            "srid"
#define COL_LAY_MAX_GSD         "maxgsd"
#define COL_LAY_MAX_LAT         "maxlat"
#define COL_LAY_MAX_LON         "maxlon"
#define COL_LAY_MIN_GSD         "mingsd"
#define COL_LAY_MIN_LAT         "minlat"
#define COL_LAY_MIN_LON         "minlon"
#define COL_LAY_REMOTE          "remote"

#define TBL_IMG_TYP             "imagerytypes"
#define COL_IMG_TYP_NAME        "name"
#define COL_IMG_TYP_LAY_ID      "layerid"
#define COL_IMG_TYP_GEOM        "geom"
#define COL_IMG_TYP_MAX_GSD     "maxgsd"
#define COL_IMG_TYP_MIN_GSD     "mingsd"

#define TBL_RSRC                "resources"
#define COL_RSRC_PATH           "path"
#define COL_RSRC_LINK           "link"

#define COL_BLOB                " BLOB, "
#define COL_INT                 " INTEGER, "
#define COL_INT_KEY_AUTO        " INTEGER PRIMARY KEY AUTOINCREMENT, "
#define COL_INT_LAST            " INTEGER"
#define COL_REAL                " REAL, "
#define COL_REAL_LAST           " REAL"
#define COL_TEXT                " TEXT, "
#define COL_TEXT_LAST           " TEXT"


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;

using namespace TAK::Engine::Util;
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


struct LayersSchemaMgr
  : db::CatalogDatabase::SchemaManager
  {
    enum
      {
          LAYERS_SCHEMA_VERSION = 10
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
      { return LAYERS_SCHEMA_VERSION; }
  };


}                                       // Close unnamed namespace.


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


///=============================================================================
///
///  class atakmap::raster::LayerDatabase::Factory
///
///     Concrete factory class for creating LayerDatabase objects.
///
///=============================================================================


class LayerDatabase::Factory
  : public CatalogDatabase::Factory
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~Factory ()
        NOTHROWS override
      { }

    //
    // Returns an instance of LayerDatabase for the supplied file path.
    // Returns NULL if the supplied file path is NULL.
    //
    LayerDatabase*
    getLayerDatabase (const char* filePath)
        const
        NOTHROWS;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  CatalogDatabase::Factory IMPLEMENTATION
    //==================================


    //
    // Returns an instance of a class derived from CatalogDatabase for the
    // supplied Database and CurrencyRegistry (neither of which will be NULL).
    //
    CatalogDatabase*
    createCatalogDatabase (db::Database* db,
                           CurrencyRegistry* registry)
        const
        NOTHROWS override
      { return new LayerDatabase (db, registry); }
  };


}                                       // Close raster namespace.
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
IMG_TYP_INSERT ("INSERT INTO " TBL_IMG_TYP " ("
                COL_IMG_TYP_LAY_ID ", "
                COL_IMG_TYP_NAME ", "
                COL_IMG_TYP_GEOM ", "
                COL_IMG_TYP_MIN_GSD ", "
                COL_IMG_TYP_MAX_GSD ") VALUES (?, ?, ?, ?, ?)");


const char* const
LAYER_INSERT ("INSERT INTO " TBL_LAY " ("
              COL_LAY_PATH ", "
              COL_LAY_CAT_LINK ", "
              COL_LAY_INFO ", "
              COL_LAY_NAME ", "
              COL_LAY_PROV ", "
              COL_LAY_DS_TYPE ", "
              COL_LAY_SRID ", "
              COL_LAY_REMOTE ", "
              COL_LAY_MAX_LAT ", "
              COL_LAY_MIN_LON ", "
              COL_LAY_MIN_LAT ", "
              COL_LAY_MAX_LON ", "
              COL_LAY_MIN_GSD ", "
              COL_LAY_MAX_GSD ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


void*
deleteResources (void* threadData)
  {
    const std::unique_ptr<std::vector<TAK::Engine::Port::String> > resources
        (static_cast<std::vector<TAK::Engine::Port::String>*> (threadData));

    std::for_each (resources->begin (),
                   resources->end (),
                   std::ptr_fun (util::deletePath));
    return nullptr;
  }


TAK::Engine::Port::String
deleteUnlinkedImageTypesSQL (const char* catalogTable,
                             const char* catalogID_Column)
  {
    std::ostringstream strm;

    strm << "DELETE FROM " TBL_IMG_TYP " WHERE " COL_IMG_TYP_LAY_ID
            " IN (SELECT " COL_LAY_ID " FROM " TBL_LAY " LEFT JOIN "
         << catalogTable
         << " ON " TBL_LAY "." COL_LAY_CAT_LINK " = "
         << catalogTable << "." << catalogID_Column
         << " WHERE " << catalogTable << "." << catalogID_Column << " IS NULL)";

    return strm.str ().c_str ();
  }


TAK::Engine::Port::String
deleteUnlinkedLayersSQL (const char* catalogTable,
                         const char* catalogID_Column)
  {
    std::ostringstream strm;

    strm << "DELETE FROM " TBL_LAY " WHERE " COL_LAY_CAT_LINK
            " IN (SELECT " COL_LAY_CAT_LINK " FROM " TBL_LAY " LEFT JOIN "
         << catalogTable
         << " ON " TBL_LAY "." COL_LAY_CAT_LINK " = "
         << catalogTable << "." << catalogID_Column
         << " WHERE " << catalogTable << "." << catalogID_Column << " IS NULL)";

    return strm.str ().c_str ();
  }


TAK::Engine::Port::String
deleteUnlinkedResourcesSQL (const char* catalogTable,
                            const char* catalogID_Column)
  {
    std::ostringstream strm;

    strm << "DELETE FROM " TBL_RSRC " WHERE " COL_RSRC_LINK
            " IN (SELECT " COL_RSRC_LINK " FROM " TBL_RSRC " LEFT JOIN "
         << catalogTable
         << " ON " TBL_RSRC "." COL_RSRC_LINK " = "
         << catalogTable << "." << catalogID_Column
         << " WHERE " << catalogTable << "." << catalogID_Column << " IS NULL)";

    return strm.str ().c_str ();
  }


TAK::Engine::Port::String
selectUnlinkedResourcesSQL (const char* catalogTable,
                            const char* catalogID_Column)
  {
    std::ostringstream strm;

    strm << "SELECT FROM " TBL_RSRC " WHERE " COL_RSRC_LINK
            " IN (SELECT " COL_RSRC_LINK " FROM " TBL_RSRC " LEFT JOIN "
         << catalogTable
         << " ON " TBL_RSRC "." COL_RSRC_LINK " = "
         << catalogTable << "." << catalogID_Column
         << " WHERE " << catalogTable << "." << catalogID_Column << " IS NULL)";

    return strm.str ().c_str ();
  }


///=====================================
///  LayerSchemaMgr MEMBER FUNCTIONS
///=====================================


bool
LayersSchemaMgr::checkSchemaObjects (db::Database& db)
    const
    NOTHROWS
try
  {
    const std::vector<TAK::Engine::Port::String> tableNames (db::getTableNames (db));
    auto end (tableNames. end ());

    return end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_LAY))
        && end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_IMG_TYP))
        && end != std::find_if (tableNames.begin (), end,
                                TAK::Engine::Port::StringEqual (TBL_RSRC));
  }
catch (...)
  { return false; }


void
LayersSchemaMgr::createSchemaObjects (db::Database& db)
    const
  {
    db.execute ("CREATE TABLE " TBL_LAY " ("
                COL_LAY_ID              COL_INT_KEY_AUTO
                COL_LAY_PATH            COL_TEXT
                COL_LAY_CAT_LINK        COL_INT
                COL_LAY_INFO            COL_BLOB
                COL_LAY_NAME            COL_TEXT
                COL_LAY_PROV            COL_TEXT
                COL_LAY_DS_TYPE         COL_TEXT
                COL_LAY_SRID            COL_INT
                COL_LAY_REMOTE          COL_INT
                COL_LAY_MAX_LAT         COL_REAL
                COL_LAY_MIN_LON         COL_REAL
                COL_LAY_MIN_LAT         COL_REAL
                COL_LAY_MAX_LON         COL_REAL
                COL_LAY_MIN_GSD         COL_REAL
                COL_LAY_MAX_GSD         COL_REAL_LAST
                ")");
    db.execute ("CREATE TABLE " TBL_IMG_TYP " ("
                COL_IMG_TYP_LAY_ID      COL_INT
                COL_IMG_TYP_NAME        COL_TEXT
                COL_IMG_TYP_GEOM        COL_BLOB
                COL_IMG_TYP_MIN_GSD     COL_REAL
                COL_IMG_TYP_MAX_GSD     COL_REAL_LAST
                ")");
    db.execute ("CREATE TABLE " TBL_RSRC " ("
                COL_RSRC_PATH           COL_TEXT
                COL_RSRC_LINK           COL_INT_LAST
                ")");
  }


void
LayersSchemaMgr::dropSchemaObjects (db::Database& db)
    const
    NOTHROWS
  {
    try
      {
        db.execute ("DROP TABLE IF EXISTS " TBL_LAY);
        db.execute ("DROP TABLE IF EXISTS " TBL_IMG_TYP);

        const std::vector<TAK::Engine::Port::String> tableNames (db::getTableNames (db));
        const std::vector<TAK::Engine::Port::String>::const_iterator end (tableNames. end ());

        if (end != std::find_if (tableNames.begin (), end,
                                 TAK::Engine::Port::StringEqual (TBL_RSRC)))
          {
            std::unique_ptr<std::vector<TAK::Engine::Port::String> > invalidResources
                (new std::vector<TAK::Engine::Port::String>);
            std::unique_ptr<db::Cursor> cursor
                (db.query ("SELECT " COL_RSRC_PATH " FROM " TBL_RSRC));

            while (cursor->moveToNext ())
              {
                invalidResources->push_back (cursor->getString (0));
              }
            if (!invalidResources->empty ())
              {
                //
                // Spin off a detached thread to delete the invalid resources.
                // The vector's memory will be adopted (and manaaged) by the
                // thread.
                //
                TAKErr code(TE_Ok);
                  {
                    ThreadPtr t(nullptr, nullptr);
                    code = Thread_start(t,
                        deleteResources,
                        static_cast<void*> (invalidResources.get()));
                    if(code == TE_Ok) {

                        t->detach(); // Detached thread.
                        invalidResources.release ();
                    } else {
                        std::cerr << MEM_FN("LayersSchemaMgr::dropSchemaObjects")
                            << "Failed to create invalid resource cleanup thread code=" << code;
                    }
                  }
              }
          }
        db.execute ("DROP TABLE IF EXISTS " TBL_RSRC);
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
namespace raster                        // Open raster namespace.
{


void
LayerDatabase::addLayers (const char* filePath,
                          const DescriptorVector& descriptors,
                          const char* workingDir,
                          Currency& currency)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("addLayers")
                                     "Received NULL filePath");
      }
    if (checkCatalogEntryExists (filePath))
      {
        std::ostringstream msg;
        msg << MEM_FN("addLayers") <<
            "Entry for " <<
            filePath <<
            " already exists";
        throw std::runtime_error (msg.str());
      }

    Lock lock(getMutex());
    db::Database& db (getDatabase ());
//    db::Database::Transaction transaction (db);

    int64_t catalogID (addCatalogEntryInternal (filePath, currency));
    int64_t layerID (db::getNextAutoincrementID (db, TABLE_LAYERS));


#if 0
    StatementRefPtr layerStatement
        (!insertLayerStmt
         ? StatementRefPtr (db.compileStatement (LAYER_INSERT))
         : insertLayerStmt);
    StatementRefPtr imageryStatement
        (!insertImageryStmt
         ? StatementRefPtr (db.compileStatement (IMG_TYP_INSERT))
         : insertImageryStmt);
#else
    std::unique_ptr<atakmap::db::Statement> layerStatement(db.compileStatement(LAYER_INSERT));
    std::unique_ptr<atakmap::db::Statement> imageryStatement(db.compileStatement(IMG_TYP_INSERT));
#endif
    auto end (descriptors.end ());

    for (auto iter (descriptors.begin ());
         iter != end;
         ++iter, ++layerID)
      {
        std::size_t layer_idx (1);
        feature::Envelope envelope ((*iter)->getCoverage ()->getEnvelope ());

        layerStatement->clearBindings ();
          
        TE_GET_STORAGE_PATH_THROW(filePath, storageFilePath, std::invalid_argument("filePath has no storage path"));
          
        layerStatement->bind (layer_idx++, storageFilePath);
        layerStatement->bind (layer_idx++, catalogID);
        layerStatement->bind (layer_idx++, (*iter)->encode (layerID));
        layerStatement->bind (layer_idx++, (*iter)->getName ());
        layerStatement->bind (layer_idx++, (*iter)->getProvider ());
        layerStatement->bind (layer_idx++, (*iter)->getDatasetType ());
        layerStatement->bind (layer_idx++, (*iter)->getSpatialReferenceID ());
        layerStatement->bind (layer_idx++, (*iter)->isRemote ());
        layerStatement->bind (layer_idx++, envelope.maxY);
        layerStatement->bind (layer_idx++, envelope.minX);
        layerStatement->bind (layer_idx++, envelope.minY);
        layerStatement->bind (layer_idx++, envelope.maxX);
        layerStatement->bind (layer_idx++, (*iter)->getMinResolution ());
        layerStatement->bind (layer_idx++, (*iter)->getMaxResolution ());
        layerStatement->execute ();

        StringVector imageryTypes ((*iter)->getImageryTypes ());
        StringVector::const_iterator typeEnd (imageryTypes.end ());

        for (StringVector::const_iterator typeIter (imageryTypes.begin ());
             typeIter != typeEnd;
             ++typeIter)
          {
            std::ostringstream strm (std::ios_base::out
                                     | std::ios_base::binary);

            (*iter)->getCoverage (*typeIter)->toWKB (strm, true);

            std::string buffString (strm.str ());
            std::size_t buffSize (buffString.size ());
            auto* buff (new unsigned char[buffSize]);
            std::size_t imagery_idx (1);

            std::memcpy (buff, buffString.data (), buffSize);
              
            db::Statement::Blob blob = atakmap::util::makeBlobWithDeleteCleanup(buff, buff + buffSize);
              
            imageryStatement->clearBindings ();
            imageryStatement->bind (imagery_idx++, layerID);
            imageryStatement->bind (imagery_idx++, *typeIter);
            imageryStatement->bind (imagery_idx++,
                                    blob);
            imageryStatement->bind (imagery_idx++,
                                    (*iter)->getMinResolution (*typeIter));
            imageryStatement->bind (imagery_idx++,
                                    (*iter)->getMaxResolution (*typeIter));
            imageryStatement->execute ();
          }
      }

    std::unique_ptr<db::Statement> resourceStmt
        (db.compileStatement ("INSERT INTO resources (link, path) VALUES (?, ?)"));

    resourceStmt->bind (1, catalogID);
    resourceStmt->bind (2, workingDir);
    resourceStmt->execute ();

//    db.setTransactionSuccessful ();
  }


LayerDatabase*
LayerDatabase::createDatabase (const char* filePath)
  {
    static Factory factory;

    return factory.getLayerDatabase (filePath);
  }


unsigned long
LayerDatabase::getFileCount (const char* filePath)
  { return util::getFileCount (filePath); }


unsigned long
LayerDatabase::getFileSize (const char* filePath)
  { return util::getFileSize (filePath); }


unsigned long
LayerDatabase::getLastModified (const char* filePath)
  { return util::getLastModified (filePath); }


DatasetDescriptor*
LayerDatabase::getLayer (int64_t layerID)
  {
    std::ostringstream strm;

    strm << layerID;

    Cursor cursor
        (queryLayersInternal (std::vector<const char*> (1, COLUMN_LAYERS_INFO),
                              COL_LAY_ID " = ?",
                              std::vector<const char*> (1, strm.str ().c_str ())));

    return cursor.moveToNext () ? cursor.getLayerInfo () : nullptr;
  }


std::vector<DatasetDescriptor*> LayerDatabase::getLayers (const char* filePath)
  {
    if (! filePath)
      {
        throw std::invalid_argument (MEM_FN ("getLayers")
                                     "Received NULL filePath");
      }

    std::vector<DatasetDescriptor*> result;
      
    TE_GET_STORAGE_PATH_THROW(filePath, storageFilePath, std::invalid_argument("filePath has no storage path"));
      
    Cursor cursor
        (queryLayersInternal (std::vector<const char*> (1, COLUMN_LAYERS_INFO),
                              COL_LAY_PATH " = ?",
                              std::vector<const char*> (1, storageFilePath)));

    TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
    while (cursor.moveToNext ()) {
        DatasetDescriptorUniquePtr clonePtr(nullptr, nullptr);
        code = cursor.getLayerInfo()->clone(clonePtr);
        TE_CHECKBREAK_CODE(code);
        result.push_back(clonePtr.release());
      }

    return result;
  }

TAK::Engine::Util::TAKErr
LayerDatabase::getLayers(TAK::Engine::Port::Collection<std::shared_ptr<const DatasetDescriptor>> &value, const char *filePath) NOTHROWS
  {
    if (!filePath)
    {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, MEM_FN("getLayers")
            "Received NULL filePath");
        return TAK::Engine::Util::TE_InvalidArg;
    }

    TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);

    TE_GET_STORAGE_PATH_CODE(filePath, storageFilePath, code);
    TE_CHECKRETURN_CODE(code);
      
    Cursor cursor
        (queryLayersInternal(std::vector<const char*>(1, COLUMN_LAYERS_INFO),
        COL_LAY_PATH " = ?",
        std::vector<const char*>(1, storageFilePath)));

    while (cursor.moveToNext())
      {
          DatasetDescriptorUniquePtr clonePtr(nullptr, nullptr);
          code = cursor.getLayerInfo()->clone(clonePtr);
          TE_CHECKBREAK_CODE(code);
          code = value.add(std::shared_ptr<const DatasetDescriptor>(std::move(clonePtr)));
          TE_CHECKBREAK_CODE(code);
      }
    TE_CHECKRETURN_CODE(code);

    return code;
  }

LayerDatabase::Cursor
LayerDatabase::queryLayers (const core::GeoPoint& ul,
                            const core::GeoPoint& lr,
                            const char* datasetType,    // Defaults to NULL.
                            double minGSD,              // Defaults to NaN.
                            double maxGSD,              // Defaults to NaN.
                            int spatialReferenceID)     // Defaults to -1.
  {
    std::ostringstream strm;

    strm << COL_LAY_MIN_LAT " <= " << ul.latitude
         << " AND " COL_LAY_MAX_LAT " >= " << lr.latitude
         << " AND " COL_LAY_MIN_LON " <= " << lr.longitude
         << " AND " COL_LAY_MAX_LON " >= " << ul.longitude;
    if (datasetType)
      {
        strm << " AND " COL_LAY_DS_TYPE " = \'" << datasetType << "\'";
      }
    if (spatialReferenceID != -1)
      {
        strm << " AND " COL_LAY_SRID " = " << spatialReferenceID;
      }
    if (!isnan (minGSD))
      {
        strm << " AND " COL_LAY_MIN_GSD " >= " << minGSD;
      }
    if (!isnan (maxGSD))
      {
        strm << " AND " COL_LAY_MAX_GSD " <= " << maxGSD;
      }

    return queryLayersInternal (std::vector<const char*> (),
                                strm.str ().c_str (),
                                std::vector<const char*> ());
  }


LayerDatabase::Cursor
LayerDatabase::queryLayers (int spatialReferenceID)
  {
    std::ostringstream strm;

    strm << spatialReferenceID;

    return queryLayersInternal (std::vector<const char*> (),
                                COL_LAY_SRID " = ?",
                                std::vector<const char*>
                                    (1, strm.str ().c_str ()));
  }


LayerDatabase::Cursor
LayerDatabase::queryLayers (const char* layerName)
  {
    if (!layerName)
      {
        throw std::invalid_argument (MEM_FN ("queryLayers")
                                     "Received NULL layerName");
      }

    return queryLayersInternal (std::vector<const char*> (),
                                COL_LAY_NAME " = ?",
                                std::vector<const char*> (1, layerName));
  }


///=====================================
///  LayerDatabase::Cursor MEMBER FUNCTIONS
///=====================================


DatasetDescriptor*
LayerDatabase::Cursor::getLayerInfo ()
    throw (CursorError)
  {
      if (!rowDesc.get())
        {
          try
          {
              int64_t layerID(getLong(colID));

              rowDesc.reset(DatasetDescriptor::decode(getBlob(colInfo)));
              if (rowDesc.get() && rowDesc->getLayerID() != layerID)
              {
                  std::ostringstream strm;

                  strm << MEM_FN("Cursor::getLayerInfo") "Mismatched layer IDs: "
                      << "layerID=" << rowDesc->getLayerID()
                      << " databaseID=" << layerID;
                  throw CursorError(strm.str().c_str());
              }
          }
          catch (const util::IO_Error& ioErr)
          {
              std::ostringstream msg;
              msg << MEM_FN("Cursor::getLayerInfo") <<
                  "Caught IO_Error: " <<
                  ioErr.what();
              throw CursorError(msg.str().c_str());
          }
        }
    return rowDesc.get();
  }

bool
LayerDatabase::Cursor::moveToNext()
    throw (CursorError)
  {
    rowDesc.reset(nullptr);
    return db::CursorProxy::moveToNext();
  }

///=====================================
///  LayerDatabase::Factory MEMBER FUNCTIONS
///=====================================


LayerDatabase*
LayerDatabase::Factory::getLayerDatabase (const char* filePath)
    const
    NOTHROWS
  {
    return dynamic_cast<LayerDatabase*>
               (getCatalogDatabase (db::openDatabase (filePath)));
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


///=====================================
///  LayerDatabase CONSTANTS
///=====================================


const char* const
LayerDatabase::TABLE_LAYERS (TBL_LAY);
const char* const
LayerDatabase::COLUMN_LAYERS_ID (COL_LAY_ID);
const char* const
LayerDatabase::COLUMN_LAYERS_PATH (COL_LAY_PATH);
const char* const
LayerDatabase::COLUMN_LAYERS_CATALOG_LINK (COL_LAY_CAT_LINK);
const char* const
LayerDatabase::COLUMN_LAYERS_INFO (COL_LAY_INFO);
const char* const
LayerDatabase::COLUMN_LAYERS_NAME (COL_LAY_NAME);
const char* const
LayerDatabase::COLUMN_LAYERS_DATASET_TYPE (COL_LAY_DS_TYPE);
const char* const
LayerDatabase::COLUMN_LAYERS_PROVIDER (COL_LAY_PROV);
const char* const
LayerDatabase::COLUMN_LAYERS_SRID (COL_LAY_SRID);
const char* const
LayerDatabase::COLUMN_LAYERS_MAX_GSD (COL_LAY_MAX_GSD);
const char* const
LayerDatabase::COLUMN_LAYERS_MAX_LAT (COL_LAY_MAX_LAT);
const char* const
LayerDatabase::COLUMN_LAYERS_MAX_LON (COL_LAY_MAX_LON);
const char* const
LayerDatabase::COLUMN_LAYERS_MIN_GSD (COL_LAY_MIN_GSD);
const char* const
LayerDatabase::COLUMN_LAYERS_MIN_LAT (COL_LAY_MIN_LAT);
const char* const
LayerDatabase::COLUMN_LAYERS_MIN_LON (COL_LAY_MIN_LON);
const char* const
LayerDatabase::COLUMN_LAYERS_REMOTE(COL_LAY_REMOTE);

const char* const
LayerDatabase::TABLE_IMAGERY_TYPES (TBL_IMG_TYP);
const char* const
LayerDatabase::COLUMN_IMAGERY_TYPES_NAME (COL_IMG_TYP_NAME);
const char* const
LayerDatabase::COLUMN_IMAGERY_TYPES_LAYER_ID (COL_IMG_TYP_LAY_ID);
const char* const
LayerDatabase::COLUMN_IMAGERY_TYPES_GEOM (COL_IMG_TYP_GEOM);
const char* const
LayerDatabase::COLUMN_IMAGERY_TYPES_MAX_GSD (COL_IMG_TYP_MAX_GSD);
const char* const
LayerDatabase::COLUMN_IMAGERY_TYPES_MIN_GSD (COL_IMG_TYP_MIN_GSD);


///=====================================
///  LayerDatabase MEMBER FUNCTIONS
///=====================================


#if 0
void
LayerDatabase::beginSyncImpl ()
  {
    db::Database& db (getDatabase ());

    insertLayerStmt = StatementRefPtr (db.compileStatement (LAYER_INSERT));
    insertImageryStmt = StatementRefPtr (db.compileStatement (IMG_TYP_INSERT));
  }
#endif


void
LayerDatabase::completeSyncImpl ()
  {
#if 0
    static StatementRefPtr nullStatement;

    insertLayerStmt = nullStatement;
    insertImageryStmt = nullStatement;
#endif
    db::Database& db (getDatabase ());
    db::Database::Transaction transaction (db);

    CatalogDatabase::completeSyncImpl ();
    db.setTransactionSuccessful ();
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


LayerDatabase::LayerDatabase (db::Database* database,
                              CurrencyRegistry* registry)
  : DatabaseWrapper (database),
    CatalogDatabase (database, registry)
  { addSchemaManager (new LayersSchemaMgr); }


void
LayerDatabase::catalogEntryRemoved (int64_t catalogID,
                                    bool automated)
  {
    db::Database& db (getDatabase ());
    std::unique_ptr<db::Statement> stmt;

    if (catalogID > 0)
      {
        stmt.reset (db.compileStatement ("DELETE FROM " TBL_IMG_TYP
                                         " WHERE " COL_IMG_TYP_LAY_ID
                                         " IN (SELECT " COL_LAY_ID
                                         " FROM " TBL_LAY
                                         " WHERE " COL_LAY_CAT_LINK " = ?)"));
        stmt->bind (1, catalogID);
        stmt->execute ();

        stmt.reset (db.compileStatement ("DELETE FROM " TBL_LAY
                                         " WHERE " COL_LAY_CAT_LINK " = ?"));
        stmt->bind (1, catalogID);
        stmt->execute ();
        stmt.reset ();

        std::ostringstream strm;
        strm << "SELECT " COL_RSRC_PATH <<
                    " FROM " TBL_RSRC <<
                    " WHERE " COL_RSRC_LINK " = " <<
                    catalogID;

        std::unique_ptr<db::Cursor> cursor
            (db.query (strm.str ().c_str ()));

        while (cursor->moveToNext ())
          {
            util::deletePath (cursor->getString (0));
          }

        stmt.reset (db.compileStatement ("DELETE FROM " TBL_RSRC
                                         " WHERE " COL_RSRC_LINK " = ?"));
        stmt->bind (1, catalogID);
        stmt->execute ();

      }
    else
      {
        static TAK::Engine::Port::String deleteImageTypes
            (deleteUnlinkedImageTypesSQL (TABLE_CATALOG, COLUMN_CATALOG_ID));
        static TAK::Engine::Port::String deleteLayers
            (deleteUnlinkedLayersSQL (TABLE_CATALOG, COLUMN_CATALOG_ID));
        static TAK::Engine::Port::String deleteResources
            (deleteUnlinkedResourcesSQL (TABLE_CATALOG, COLUMN_CATALOG_ID));

        stmt.reset (db.compileStatement (deleteImageTypes));
        stmt->execute ();
        stmt.reset (db.compileStatement (deleteLayers));
        stmt->execute ();

        static TAK::Engine::Port::String selectResources
            (selectUnlinkedResourcesSQL (TABLE_CATALOG, COLUMN_CATALOG_ID));
        std::unique_ptr<db::Cursor> cursor (db.query (selectResources));

        while (cursor->moveToNext ())
          {
            util::deletePath (cursor->getString (0));
          }

        stmt.reset (db.compileStatement (deleteResources));
        stmt->execute ();
      }
  }


bool
LayerDatabase::checkCatalogEntryExists (const char* filePath)
  {
    return std::unique_ptr<db::Cursor> (queryCatalogPath (filePath))
               ->moveToNext ();
  }


LayerDatabase::Cursor
LayerDatabase::getLayerInternal (int64_t layerID)
  {
    std::ostringstream strm;

    strm << layerID;
    return queryLayersInternal (std::vector<const char*> (1, COLUMN_LAYERS_INFO),
                                COL_LAY_ID " = ?",
                                std::vector<const char*> (1, strm.str ().c_str ()));
  }


LayerDatabase::Cursor
LayerDatabase::queryLayersInternal (const std::vector<const char*>& columns,
                                    const char* where,
                                    const std::vector<const char*>& whereArgs,
                                    const char* groupBy,
                                    const char* having,
                                    const char* orderBy,
                                    const char* limit)
  {
    std::vector<const char *> emptyCols;
    std::unique_ptr<db::Cursor> result(CatalogDatabase::query(TABLE_LAYERS, emptyCols,
        where, whereArgs,
        groupBy, having, orderBy, limit));
    return Cursor (std::move(result));
  }


///=====================================
///  LayerDatabase::Cursor MEMBER FUNCTIONS
///=====================================

LayerDatabase::Cursor::Cursor(const Cursor &c)
    : db::CursorProxy(c),
    colID(c.colID),
    colInfo(c.colInfo),
    colMaxGSD(c.colMaxGSD),
    colMaxLat(c.colMaxLat),
    colMaxLon(c.colMaxLon),
    colMinGSD(c.colMinGSD),
    colMinLat(c.colMinLat),
    colMinLon(c.colMinLon),
    colName(c.colName),
    colPath(c.colPath),
    colProvider(c.colProvider),
    colSRID(c.colSRID),
    colType(c.colType),
    rowDesc(std::move(const_cast<std::unique_ptr<DatasetDescriptor> &>(c.rowDesc))) { }

LayerDatabase::Cursor::Cursor (const std::shared_ptr<db::Cursor> &subject)
  : CursorProxy (subject),
    colID (subject->getColumnIndex (COLUMN_LAYERS_ID)),
    colInfo (subject->getColumnIndex (COLUMN_LAYERS_INFO)),
    colMaxGSD (subject->getColumnIndex (COLUMN_LAYERS_MAX_GSD)),
    colMaxLat (subject->getColumnIndex (COLUMN_LAYERS_MAX_LAT)),
    colMaxLon (subject->getColumnIndex (COLUMN_LAYERS_MAX_LON)),
    colMinGSD (subject->getColumnIndex (COLUMN_LAYERS_MIN_GSD)),
    colMinLat (subject->getColumnIndex (COLUMN_LAYERS_MIN_LAT)),
    colMinLon (subject->getColumnIndex (COLUMN_LAYERS_MIN_LON)),
    colName (subject->getColumnIndex (COLUMN_LAYERS_NAME)),
    colPath (subject->getColumnIndex (COLUMN_LAYERS_PATH)),
    colProvider (subject->getColumnIndex (COLUMN_LAYERS_PROVIDER)),
    colSRID (subject->getColumnIndex (COLUMN_LAYERS_SRID)),
    colType (subject->getColumnIndex (COLUMN_LAYERS_DATASET_TYPE)),
    rowDesc(nullptr)
  { }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.
