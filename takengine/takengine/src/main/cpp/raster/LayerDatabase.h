////============================================================================
////
////    FILE:           LayerDatabase.h
////
////    DESCRIPTION:    Concrete class for database of layers.
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


#ifndef ATAKMAP_RASTER_LAYER_DATABASE_H_INCLUDED
#define ATAKMAP_RASTER_LAYER_DATABASE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <limits>
#include <memory>
#include <vector>

#include "db/CatalogDatabase.h"
#include "db/Database.h"
#include "port/Collection.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


class GeoPoint;


}                                       // Close core namespace.
namespace raster                        // Open raster namespace.
{


class DatasetDescriptor;


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


///=============================================================================
///
///  class atakmap::raster::LayerDatabase
///
///     Concrete class for database of layers.
///
///=============================================================================


class LayerDatabase
  : public db::CatalogDatabase
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Cursor;
    typedef std::vector<DatasetDescriptor*>     DescriptorVector;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~LayerDatabase ()
        NOTHROWS
      { }

    //
    // A private constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a non-copyable
    // base class).  This is acceptable.
    //

    //
    // Adds a layer for the supplied DatasetDescriptor to the database.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    // Throws std::runtime_error if a layer for the supplied filePath is already
    // in the database.
    //
    void
    addLayer (const char* filePath,
              DatasetDescriptor& descriptor,
              const char* workingDir,
              Currency&);

    void
    addLayers (const char* filePath,
               const DescriptorVector& descriptors,
               const char* workingDir,
               Currency&);

    static
    LayerDatabase*
    createDatabase (const char* filePath);

    static
    unsigned long
    getFileCount (const char* filePath);

    static
    unsigned long
    getFileSize (const char* filePath);

    static
    unsigned long
    getLastModified (const char* filePath);

    //
    // Returns the (possibly NULL) layer with the supplied layerID.
    // Throws IO_Error.
    //
    DatasetDescriptor*
    getLayer (int64_t layerID);

    //
    // Returns a (possibly empty) vector of the layers derived from the
    // filePath.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    DescriptorVector
    getLayers (const char* filePath);

    TAK::Engine::Util::TAKErr
    getLayers(TAK::Engine::Port::Collection<std::shared_ptr<const DatasetDescriptor>> &value, const char *filePath) NOTHROWS;

    using DatabaseWrapper::query;

    db::Cursor*
    query (const char* sql,
           const std::vector<const char*>& args)
      { return getDatabase ().query (sql, args); }

    //
    // Performs a query for layers.
    //
    // If columns is empty, selects "*".
    // If where is NULL, whereArgs are ignored.
    //
    Cursor
    queryLayers (const std::vector<const char*>& columns,
                 const char* where,
                 const std::vector<const char*>& whereArgs,
                 const char* groupBy = nullptr,
                 const char* having = nullptr,
                 const char* orderBy = nullptr,
                 const char* limit = nullptr);

    //
    // Performs a query for layers within a region of interest.
    //
    Cursor
    queryLayers (const core::GeoPoint& ul,
                 const core::GeoPoint& lr,
                 const char* datasetType = nullptr,
                 double minGSD = std::numeric_limits<double>::quiet_NaN (),
                 double maxGSD = std::numeric_limits<double>::quiet_NaN (),
                 int spatialReferenceID = -1);

    //
    // Performs a query for layers with the supplied spatial reference ID.
    //
    Cursor
    queryLayers (int spatialReferenceID);

    //
    // Performs a query for layers with the supplied name.
    //
    // Throws std::invalid_argument if layerName is NULL.
    //
    Cursor
    queryLayers (const char* layerName);


    //==================================
    //  CatalogDatabase INTERFACE
    //==================================


    void
    beginSync ();


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED CONSTANTS
    //==================================


    static const char* const TABLE_LAYERS;
    static const char* const COLUMN_LAYERS_ID;
    static const char* const COLUMN_LAYERS_PATH;
    static const char* const COLUMN_LAYERS_CATALOG_LINK;
    static const char* const COLUMN_LAYERS_INFO;
    static const char* const COLUMN_LAYERS_NAME;
    static const char* const COLUMN_LAYERS_DATASET_TYPE;
    static const char* const COLUMN_LAYERS_PROVIDER;
    static const char* const COLUMN_LAYERS_SRID;
    static const char* const COLUMN_LAYERS_MAX_GSD;
    static const char* const COLUMN_LAYERS_MAX_LAT;
    static const char* const COLUMN_LAYERS_MAX_LON;
    static const char* const COLUMN_LAYERS_MIN_GSD;
    static const char* const COLUMN_LAYERS_MIN_LAT;
    static const char* const COLUMN_LAYERS_MIN_LON;
    static const char* const COLUMN_LAYERS_REMOTE;

    static const char* const TABLE_IMAGERY_TYPES;
    static const char* const COLUMN_IMAGERY_TYPES_NAME;
    static const char* const COLUMN_IMAGERY_TYPES_LAYER_ID;
    static const char* const COLUMN_IMAGERY_TYPES_GEOM;
    static const char* const COLUMN_IMAGERY_TYPES_MAX_GSD;
    static const char* const COLUMN_IMAGERY_TYPES_MIN_GSD;


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    //==================================
    //  CatalogDatabase INTERFACE
    //==================================


#if 0
    void
    beginSyncImpl ();
#endif

    void
    completeSyncImpl ();


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    class Factory;
    friend class Factory;
#if 0
    typedef PGSC::RefCountableIndirectPtr<db::Statement>        StatementRefPtr;
#endif


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    LayerDatabase (db::Database*,
                   CurrencyRegistry*);

    bool
    checkCatalogEntryExists (const char* filePath);

    Cursor
    getLayerInternal (int64_t layerID);

    Cursor
    queryLayersInternal (const std::vector<const char*>& columns,
                         const char* where,
                         const std::vector<const char*>& whereArgs,
                         const char* groupBy = nullptr,
                         const char* having = nullptr,
                         const char* orderBy = nullptr,
                         const char* limit = nullptr);


    //==================================
    //  CatalogDatabase IMPLEMENTATION
    //==================================


    void
    catalogEntryAdded (int64_t catalogID)
      { }

    void
    catalogEntryMarkedValid (int64_t catalogID)
      { }

    void
    catalogEntryRemoved (int64_t catalogID,
                         bool automated);


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


#if 0
    StatementRefPtr insertLayerStmt;
    StatementRefPtr insertImageryStmt;
#endif
  };


///=============================================================================
///
///  class atakmap::raster::LayerDatabase::Cursor
///
///     Concrete cursor for a LayerDatabase catalog query result.
///
///=============================================================================


class LayerDatabase::Cursor
  : public db::CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//
    Cursor(const Cursor &c);

    ~Cursor ()
        NOTHROWS
      { }

    //
    // A private constructor is defined below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    const char*
    getDatasetType ()
        const
        throw (CursorError)
      { return getString (colType); }

    DatasetDescriptor*
    getLayerInfo ()
        throw (CursorError);

    double
    getMaxGSD ()
        const
        throw (CursorError)
      { return getDouble (colMaxGSD); }

    double
    getMaxLat ()
        const
        throw (CursorError)
      { return getDouble (colMaxLat); }

    double
    getMaxLon ()
        const
        throw (CursorError)
      { return getDouble (colMaxLon); }

    double
    getMinGSD ()
        const
        throw (CursorError)
      { return getDouble (colMinGSD); }

    double
    getMinLat ()
        const
        throw (CursorError)
      { return getDouble (colMinLat); }

    double
    getMinLon ()
        const
        throw (CursorError)
      { return getDouble (colMinLon); }

    const char*
    getName ()
        const
        throw (CursorError)
      { return getString (colName); }

    const char*
    getPath ()
        const
        throw (CursorError)
      { return getString (colPath); }

    const char*
    getProvider ()
        const
        throw (CursorError)
      { return getString (colProvider); }

    int
    getSpatialReferenceID ()
        const
        throw (CursorError)
      { return getInt (colSRID); }

    bool
    moveToNext()
    throw (CursorError);

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class LayerDatabase;


    Cursor (const std::shared_ptr<db::Cursor> &subject);       // Adopts subject cursor.


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::size_t colID;
    std::size_t colInfo;
    std::size_t colMaxGSD;
    std::size_t colMaxLat;
    std::size_t colMaxLon;
    std::size_t colMinGSD;
    std::size_t colMinLat;
    std::size_t colMinLon;
    std::size_t colName;
    std::size_t colPath;
    std::size_t colProvider;
    std::size_t colSRID;
    std::size_t colType;

    std::unique_ptr<DatasetDescriptor> rowDesc;
  };


}                                       // Close raster namespace.
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


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


inline
void
LayerDatabase::addLayer (const char* filePath,
                         DatasetDescriptor& descriptor,
                         const char* workingDir,
                         Currency& currency)
  {
    addLayers (filePath,
               std::vector<DatasetDescriptor*> (1, &descriptor),
               workingDir,
               currency);
  }


inline
LayerDatabase::Cursor
LayerDatabase::queryLayers (const std::vector<const char*>& columns,
                            const char* where,
                            const std::vector<const char*>& whereArgs,
                            const char* groupBy,
                            const char* having,
                            const char* orderBy,
                            const char* limit)
  {
    return queryLayersInternal (columns, where, whereArgs,
                                groupBy, having, orderBy, limit);
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


#endif  // #ifndef ATAKMAP_RASTER_LAYER_DATABASE_H_INCLUDED
