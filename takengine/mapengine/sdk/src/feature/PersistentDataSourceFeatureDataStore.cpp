////============================================================================
////
////    FILE:           PersistentDataSourceFeatureDataStore.cpp
////
////    DESCRIPTION:    Implementation of the
////                    PersistentDataSourceFeatureDataStore class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 27, 2015  scott           Created.
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


#include "feature/PersistentDataSourceFeatureDataStore.h"

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "db/CatalogDatabase.h"
#include "db/Cursor.h"
#include "db/Statement.h"
#include "db/WhereClauseBuilder.h"
#include "feature/Feature.h"
#include "feature/FeatureCatalogDatabase.h"
#include "feature/FeatureSet.h"
#include "feature/Geometry.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"
#include "thread/Lock.h"
#include "util/IO.h"
#include "util/Memory.h"


#define MEM_FN( fn ) \
        "atakmap::feature::PersistentDataSourceFeatureDataStore::" fn ": "


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


enum
  {
    CURRENCY_VERSION    = 2
  };


typedef db::DataStore::QueryParameters::Order   Order;
typedef std::shared_ptr<Order>    OrderPtr;


///=============================================================================
///
///  class FeatureCursorImpl
///
///     Implementation of FeatureDataStore::FeatureCursor.
///
///=============================================================================


class FeatureCursorImpl
  : public feature::FeatureDataStore::FeatureCursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef void (*ID_Setter) (feature::Feature&, int64_t, int64_t);


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    FeatureCursorImpl (const std::shared_ptr<db::Cursor> &subject,
                       std::size_t colID,
                       std::size_t colGroupID,
                       std::size_t colName,
                       std::size_t colGeom,
                       std::size_t colStyle,
                       std::size_t colAttribs,
                       ID_Setter setIDs)
      : FeatureCursor (subject),
        colID (colID),
        colGroupID (colGroupID),
        colName (colName),
        colGeom (colGeom),
        colStyle (colStyle),
        colAttribs (colAttribs),
        setIDs (setIDs)
      { }


    //==================================
    //  FeatureDataStore::FeatureCursor INTERFACE
    //==================================


    feature::Feature*
    get ()
        const
        throw (CursorError) override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::size_t colID;
    std::size_t colGroupID;
    std::size_t colName;
    std::size_t colGeom;
    std::size_t colStyle;
    std::size_t colAttribs;
    ID_Setter setIDs;
  };


///=============================================================================
///
///  class FeatureSetCursorImpl
///
///     Implementation of FeatureDataStore::FeatureSetCursor.
///
///=============================================================================


class FeatureSetCursorImpl
  : public feature::FeatureDataStore::FeatureSetCursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef void (feature::FeatureDataStore::*Adopter)
                 (feature::FeatureSet&, int64_t) const;

    //==================================
    //  PUBLIC INTERFACE
    //==================================


    FeatureSetCursorImpl (std::unique_ptr<db::Cursor> &&subject,
                          std::size_t colID,
                          std::size_t colName,
                          std::size_t colProvider,
                          std::size_t colType,
                          std::size_t colMinGSD,
                          std::size_t colMaxGSD,
                          const feature::FeatureDataStore& owner,
                          Adopter adopt)
      : FeatureSetCursor (std::move(subject)),
        colID (colID),
        colName (colName),
        colProvider (colProvider),
        colType (colType),
        colMinGSD (colMinGSD),
        colMaxGSD (colMaxGSD),
        owner (owner),
        adopt (adopt)
      { }


    //==================================
    //  FeatureDataStore::FeatureSetCursor INTERFACE
    //==================================


    feature::FeatureSet*
    get ()
        const
        throw (CursorError) override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::size_t colID;
    std::size_t colName;
    std::size_t colProvider;
    std::size_t colType;
    std::size_t colMinGSD;
    std::size_t colMaxGSD;
    const feature::FeatureDataStore& owner;
    Adopter adopt;
  };


///=============================================================================
///
///  class FeatureOrderAppender
///
///     Unary functor that appends an ORDER BY expression for a
///     QueryParameters::Order supported by FeatureQueryParameters to a
///     WhereClauseBuilder.
///
///=============================================================================


class FeatureOrderAppender
  : public std::unary_function<void, OrderPtr>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    FeatureOrderAppender (db::WhereClauseBuilder& wheres)
      : wheres (wheres),
        first (true)
      { }

    void
    operator() (const OrderPtr& order)
      { appendOrder (order.get ()); }

    void
    appendOrder (const Order* order);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef feature::FeatureDataStore::FeatureQueryParameters::Distance
            OrderByDistance;
    typedef feature::FeatureDataStore::FeatureQueryParameters::FeatureSetID
            OrderByFeatureSetID;
    typedef feature::FeatureDataStore::FeatureQueryParameters::GeometryType
            OrderByGeometry;
    typedef db::DataStore::QueryParameters::ID
            OrderByID;
    typedef db::DataStore::QueryParameters::Name
            OrderByName;
    typedef db::DataStore::QueryParameters::Resolution
            OrderByResolution;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    db::WhereClauseBuilder& wheres;
    bool first;
  };


///=============================================================================
///
///  class FeatureSetOrderAppender
///
///     Unary functor that appends an ORDER BY expression for a
///     QueryParameters::Order supported by FeatureSetQueryParameters to a
///     WhereClauseBuilder.
///
///=============================================================================


class FeatureSetOrderAppender
  : public std::unary_function<void, OrderPtr>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    FeatureSetOrderAppender (db::WhereClauseBuilder& wheres)
      : wheres (wheres),
        first (true)
      { }

    void
    operator() (const OrderPtr& order)
      { appendOrder (order.get ()); }

    void
    appendOrder (const Order* order);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    typedef db::DataStore::QueryParameters::ID
            OrderByID;
    typedef db::DataStore::QueryParameters::Name
            OrderByName;
    typedef db::DataStore::QueryParameters::Provider
            OrderByProvider;
    typedef db::DataStore::QueryParameters::Resolution
            OrderByResolution;
    typedef db::DataStore::QueryParameters::Type
            OrderByType;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    db::WhereClauseBuilder& wheres;
    bool first;
  };


class GenerateCurrency
  : public db::CatalogDatabase::Currency
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const CURRENCY_NAME;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    GenerateCurrency (const feature::FeatureDataSource::Content& content)
      : dataSource (feature::FeatureDataSource::getProvider
                        (content.getProvider ()))
      {
        if (!dataSource)
          {
            throw std::runtime_error
                      (MEM_FN ("GenerateCurrency::GenerateCurrency")
                       "No FeatureDataSource found for content");
          }
      }


    //==================================
    //  db::CatalogDatabase::Currency INTERFACE
    //==================================


    atakmap::util::BlobImpl
    getData (const char* filePath)
        const override;

    const char*
    getName ()
        const
        NOTHROWS override
      { return CURRENCY_NAME; }

    unsigned long
    getVersion ()
        const
        NOTHROWS override
      { return CURRENCY_VERSION; }

    bool
    isValid (const char* filePath,
             unsigned long version,
             const atakmap::util::BlobImpl &data)
        const override
      { return false; }                 // Not a Currency validator.


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    const feature::FeatureDataSource* dataSource;
  };


struct GroupVisibility
  {
    GroupVisibility (bool visible,
                     bool checkVersion,
                     unsigned int version)
      : visible (visible),
        checkVersion (checkVersion),
        version (version)
      { }

    bool visible;
    bool checkVersion;
    unsigned int version;
  };


typedef std::map<int64_t, GroupVisibility>      GroupVisibilityMap;


class ValidateCurrency
  : public db::CatalogDatabase::Currency
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  db::CatalogDatabase::Currency INTERFACE
    //==================================


    atakmap::util::BlobImpl
    getData (const char* filePath)      // Not a Currency generator.
        const override
      {
        return std::pair<const unsigned char*,
                         const unsigned char*> (NULL, NULL);
      }

    const char*
    getName ()
        const
        NOTHROWS override
      { return GenerateCurrency::CURRENCY_NAME; }

    unsigned long
    getVersion ()
        const
        NOTHROWS override
      { return CURRENCY_VERSION; }

    bool
    isValid (const char* filePath,
             unsigned long version,
             const atakmap::util::BlobImpl& data)
        const override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    ~ValidateCurrency ()                // Private per PGSC::RefCountable.
        NOTHROWS
      { }
  };


///=============================================================================
///
///  class VisibleOnlyFilter
///
///     Filtering proxy for a Cursor that only accepts
///
///=============================================================================


class VisibleOnlyFilter
  : public db::FilteredCursor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    VisibleOnlyFilter (std::shared_ptr<db::Cursor> &&,
                       std::size_t colGroupID,
                       std::size_t colVisible,
                       std::size_t colVisibleVersion,
                       const GroupVisibilityMap&)
        throw (CursorError);            // Thrown if subject is NULL.

    ~VisibleOnlyFilter ()
        NOTHROWS override
      { }

    //
    // The compiler-generated copy constructor and assignment are acceptable.
    //


    //==================================
    //  db::FilteredCursor INTERFACE
    //==================================


    bool
    accept ()
        const override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::size_t colGroupID;
    std::size_t colVisible;
    std::size_t colVisibleVersion;
    GroupVisibilityMap visibilityMap;
  };


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


const char* const
GenerateCurrency::CURRENCY_NAME ("PersistentDataSourceFeatureDataStore.Currency");


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


void
appendSpatialFilter (const db::DataStore::QueryParameters::SpatialFilter* filter,
                     db::WhereClauseBuilder& wheres)
  {
    typedef db::DataStore::QueryParameters::RadiusFilter        RadiusFilter;
    typedef db::DataStore::QueryParameters::RegionFilter        RegionFilter;

    const RadiusFilter *radiusFilter(nullptr);
    const RegionFilter *regionFilter(nullptr);

    if ((radiusFilter = dynamic_cast<const RadiusFilter*> (filter)), radiusFilter != nullptr)
      {
        //
        // A PointFilter is a RadiusFilter with a 0 radius.  Use a simpler test
        // for the PointFilter.
        //
        const core::GeoPoint& point (radiusFilter->point);
        std::ostringstream ptStrm;

        ptStrm << "MakePoint(" << std::setprecision (16)
               << point.longitude << ", " << point.latitude << ", 4326)";

        if (radiusFilter->radius)
          {
            wheres.newConjunct ()
                  .append ("Distance(")
                  .append (ptStrm.str ().c_str ())
                  .append (", Geometry.geom, 1) <= ?")
                  .addArg (radiusFilter->radius);
          }
        else
          {
            wheres.newConjunct ()
                  .append ("Within(")
                  .append (ptStrm.str ().c_str ())
                  .append (", Geometry.geom) = 1");
          }
      }
    else if ((regionFilter = dynamic_cast<const RegionFilter*> (filter)), regionFilter != nullptr)
      {
        const core::GeoPoint& ul (regionFilter->upperLeft);
        const core::GeoPoint& lr (regionFilter->lowerRight);
        std::ostringstream mbrStrm;

        mbrStrm << "BuildMbr(" << std::setprecision (16)
                << ul.longitude << ", " << ul.latitude << ", "
                << lr.longitude << ", " << lr.latitude << ", 4326)";

        if (feature::FeatureDatabase::SPATIAL_INDEX_ENABLED)
          {
            wheres.newConjunct ()
                  .append ("Geometry.ROWID IN (SELECT ROWID FROM SpatialIndex "
                           "WHERE f_table_name = \'Geometry\' "
                           "AND search_frame = ")
                  .append (mbrStrm.str ().c_str ())
                  .append (")");
          }
        else
          {
            wheres.newConjunct ()
                  .append ("Intersects(")
                  .append (mbrStrm.str ().c_str ())
                  .append (", Geometry.geom) = 1");
          }
      }
    else
      {
        throw std::invalid_argument (MEM_FN ("appendSpatialFilter")
                                     "Received unrecognized SpatialFilter type");
      }
  }


void
buildWheres (const feature::FeatureDataStore::FeatureQueryParameters& params,
             db::WhereClauseBuilder& wheres)
  {
    if (!params.providers.empty ())
      {
        wheres.newConjunct ()
              .append ("Geometry.group_id IN (SELECT id FROM groups WHERE ")
              .appendIn ("provider", params.providers).append (")");
      }
    if (!params.types.empty ())
      {
        wheres.newConjunct ()
              .append ("Geometry.group_id IN (SELECT id FROM groups WHERE ")
              .appendIn ("type", params.types).append (")");
      }
    if (!params.featureSetNames.empty ())
      {
        wheres.newConjunct ()
              .append ("Geometry.group_id IN (SELECT id FROM groups WHERE ")
              .appendIn ("name", params.featureSetNames).append (")");
      }
    if (!params.featureSetIDs.empty ())
      {
        wheres.newConjunct ().appendIn ("Geometry.group_id",
                                        params.featureSetIDs);
      }
    if (!params.names.empty ())
      {
        wheres.newConjunct ().appendIn ("Geometry.name", params.names);
      }
    if (!params.IDs.empty ())
      {
        wheres.newConjunct ().appendIn ("Geometry.id", params.IDs);
      }
    if (params.spatialFilter.get ())
      {
        appendSpatialFilter (params.spatialFilter.get (), wheres);
      }
    if (params.minResolution)
      {
        wheres.newConjunct ()
              .append ("Geometry.min_gsd >= ?").addArg (params.minResolution);
      }
    if (params.maxResolution)
      {
        wheres.newConjunct ()
              .append ("Geometry.max_gsd <= ?").addArg (params.maxResolution);
      }
  }


void
buildWheres (const feature::FeatureDataStore::FeatureSetQueryParameters& params,
             db::WhereClauseBuilder& wheres)
  {
    if (params.visibleOnly)
      {
        wheres.newConjunct ().append ("visible = 1");
      }
    if (!params.providers.empty ())
      {
        wheres.newConjunct ().appendIn ("provider", params.providers);
      }
    if (!params.types.empty ())
      {
        wheres.newConjunct ().appendIn ("type", params.types);
      }
    if (!params.names.empty ())
      {
        wheres.newConjunct ().appendIn ("name", params.names);
      }
    if (!params.IDs.empty ())
      {
        wheres.newConjunct ().appendIn ("id", params.IDs);
      }
    if (params.minResolution)
      {
        wheres.newConjunct ()
              .append ("min_gsd >= ?").addArg (params.minResolution);
      }
    if (params.maxResolution)
      {
        wheres.newConjunct ()
              .append ("max_gsd <= ?").addArg (params.maxResolution);
      }
  }


template <typename T>
std::ostream&
copyItems (std::ostream& strm,
           const std::vector<T>& items)
  {
    if (!items.empty ())
      {
        typename std::vector<T>::const_iterator last (items.end () - 1);

        std::copy (items.begin (), last,
                   std::ostream_iterator<T> (strm, ", "));
        strm << *last;
      }
    return strm;
  }


///=====================================
///  FeatureCursorImpl MEMBER FUNCTIONS
///=====================================


feature::Feature*
FeatureCursorImpl::get ()
    const
    throw (CursorError)
  {
    int64_t featureID (getLong (colID));

    //
    // Geometry is required.  Style and Attributes are optional.
    //

    std::unique_ptr<feature::Geometry> geometry;
    Blob geomBlob (getBlob (colGeom));

    if (!geomBlob.first || !geomBlob.second)
      {
        std::ostringstream err;

        err << "FeatureCursor received invalid Geometry BLOB for Feature (ID="
            << featureID << ")";
        throw CursorError (err.str ().c_str ());
      }

    try
      {
        geometry.reset (feature::parseBlob (geomBlob));
      }
    catch (const util::IO_Error& ioExc)
      {
        std::ostringstream err;

        err << "FeatureCursor caught IO_Error parsing Geometry BLOB for "
            << "Feature (ID=" << featureID << "): " << ioExc.what ();
        throw CursorError (err.str ().c_str ());
      }

    std::unique_ptr<feature::Style> style;
    const char* styleOGR (getString (colStyle));

    if (styleOGR)
      {
        try
          {
            style.reset (feature::Style::parseStyle (styleOGR));
          }
        catch (const std::invalid_argument& iaExc)
          {
            std::ostringstream err;

            err << "FeatureCursor caught std::invalid_argument exception while "
                << "parsing OGR Style specification for Feature (ID="
                << featureID << "): " << iaExc.what ();
            throw CursorError (err.str ().c_str ());
          }
        catch (...)
          {
            std::ostringstream err;

            err << "FeatureCursor caught unknown exception parsing OGR Style "
                << "specification for Feature (ID=" << featureID << ")";
            throw CursorError (err.str ().c_str ());
          }
      }

    //
    // An empty Attributes BLOB is OK.
    //

    std::unique_ptr<util::AttributeSet> attributes;
    Blob attribsBlob (getBlob (colAttribs));

    if (attribsBlob.first && attribsBlob.second)
      {
        //
        // Parse the blob into an AttributeSet.
        //
        // attributes.reset (...);
      }

    std::unique_ptr<feature::Feature> result
        (new feature::Feature (getString (colName),
                               geometry.release (),
                               style.release (),
                               attributes.get ()
                                   ? *attributes
                                   : util::AttributeSet ()));

    setIDs (*result, getLong (colGroupID), featureID);

    return result.release ();
  }


///=====================================
///  FeatureOrderAppender MEMBER FUNCTIONS
///=====================================


void
FeatureOrderAppender::appendOrder (const Order* order)
  {
    const OrderByID *byID(nullptr);
    const OrderByName *byName(nullptr);
    const OrderByResolution *byResolution(nullptr);
    const OrderByDistance *byDistance(nullptr);
    const OrderByFeatureSetID *byFeatureSetID(nullptr);
    const OrderByGeometry *byGeometryType(nullptr);

    if ((byID = dynamic_cast<const OrderByID*> (order)), byID != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.id");
      }
    else if ((byName = dynamic_cast<const OrderByName*> (order)), byName != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.name");
      }
    else if ((byFeatureSetID = dynamic_cast<const OrderByFeatureSetID*> (order)), byFeatureSetID != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.group_id");
      }
    else if ((byResolution = dynamic_cast<const OrderByResolution*> (order)), byResolution != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.max_gsd");
      }
    else if ((byDistance = dynamic_cast<const OrderByDistance*> (order)), byDistance != nullptr)
      {
        const core::GeoPoint& point (byDistance->point);
        std::ostringstream strm;

        strm << (first ? " ORDER BY " : ", ")
             << "Distance(Geometry.geom, MakePoint("
             << std::setprecision (16)
             << point.longitude << ", " << point.latitude << ", 4326), 1)";
        wheres.append (strm.str ().c_str ());
      }
    else if ((byGeometryType = dynamic_cast<const OrderByGeometry*> (order)), byGeometryType != nullptr)
      {
        wheres.append (first ? " ORDER BY CASE " : ", CASE ")
              .append ("WHEN GeometryType(Geometry.geom) LIKE 'POINT%' "
                       "THEN 0 "
                       "WHEN GeometryType(Geometry.geom) LIKE 'LINESTRING%' "
                       "THEN 1 "
                       "WHEN GeometryType(Geometry.geom) LIKE 'POLYGON%' "
                       "THEN 2 "
                       "ELSE 3 END");
      }
    else
      {
        throw std::invalid_argument (MEM_FN ("FeatureOrderAppender")
                                     "Received unsupported Order type");
      }
    first = false;
  }


///=====================================
///  FeatureSetCursorImpl MEMBER FUNCTIONS
///=====================================


feature::FeatureSet*
FeatureSetCursorImpl::get ()
    const
    throw (CursorError)
  {
    std::unique_ptr<feature::FeatureSet> result
        (new feature::FeatureSet (getString (colProvider),
                                  getString (colType),
                                  getString (colName),
                                  getDouble (colMinGSD),
                                  getDouble (colMaxGSD)));

    (owner.*adopt) (*result, getLong (colID));
    return result.release ();
  }


///=====================================
///  FeatureSetOrderAppender MEMBER FUNCTIONS
///=====================================


void
FeatureSetOrderAppender::appendOrder (const Order* order)
  {
    const OrderByID *byID(nullptr);
    const OrderByName *byName(nullptr);
    const OrderByProvider *byProvider(nullptr);
    const OrderByResolution *byResolution(nullptr);
    const OrderByType *byType(nullptr);

    if ((byID = dynamic_cast<const OrderByID*> (order)), byID != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("id");
      }
    else if ((byName = dynamic_cast<const OrderByName*> (order)), byName != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("name");
      }
    else if ((byProvider = dynamic_cast<const OrderByProvider*> (order)), byProvider != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("provider");
      }
    else if ((byResolution = dynamic_cast<const OrderByResolution*> (order)), byResolution != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("max_gsd");
      }
    else if ((byType = dynamic_cast<const OrderByType*> (order)), byType != nullptr)
      {
        wheres.append (first ? " ORDER BY " : ", ").append ("type");
      }
    else
      {
        throw std::invalid_argument (MEM_FN ("FeatureSetOrderAppender")
                                     "Received unsupported Order type");
      }
    first = false;
  }


///=====================================
///  GenerateCurrency MEMBER FUNCTIONS
///=====================================


atakmap::util::BlobImpl
GenerateCurrency::getData (const char* filePath)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("GenerateCurrency::getData")
                                     "Received NULL filePath");
      }

    std::ostringstream strm (std::ios_base::out | std::ios_base::binary);
    const char* sourceName (dataSource->getName ());

    util::write<std::size_t> (strm, 1);
    util::write (strm, dataSource->parseVersion ());
    util::write (strm, std::strlen (sourceName)) << sourceName;
    util::write (strm, util::isDirectory (filePath));
    util::write (strm, util::getFileSize (filePath));
    util::write (strm, util::getLastModified (filePath));

    std::string contents (strm.str ());
    std::size_t length (contents.size ());
    auto* buff (new unsigned char[length]);

    std::memcpy (buff, contents.data (), length);
    return atakmap::util::makeBlobWithDeleteCleanup(buff, buff + length);
  }


///=====================================
///  ValidateCurrency MEMBER FUNCTIONS
///=====================================


bool
ValidateCurrency::isValid (const char* filePath,
                           unsigned long version,
                           const atakmap::util::BlobImpl& data)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("ValidateCurrency::isValid")
                                     "Received NULL filePath");
      }
    if (!data.first || !data.second)
      {
        throw std::invalid_argument (MEM_FN ("ValidateCurrency::isValid")
                                     "Received invalid Currency data");
      }

    bool valid (version == getVersion ());

    if (valid)
      {
        std::istringstream strm (std::string (data.first, data.second),
                                 std::ios_base::in | std::ios_base::binary);
        auto sourceCount (util::read<std::size_t> (strm));

        for (std::size_t i (0); valid && i < sourceCount; ++i)
          {
            auto parseVersion (util::read<unsigned int> (strm));
            auto nameLength (util::read<std::size_t> (strm));
            TAK::Engine::Util::array_ptr<char> sourceName (new char[nameLength + 1]);

            strm.get (sourceName.get (), nameLength + 1);

            const feature::FeatureDataSource* source
                (feature::FeatureDataSource::getProvider (sourceName.get()));

            valid = source && parseVersion == source->parseVersion ();
          }

        if (valid)
          {
            valid = util::isDirectory (filePath) == util::read<bool> (strm)
                && util::getFileSize (filePath) == util::read<unsigned long> (strm)
                && util::getLastModified (filePath) == util::read<unsigned long> (strm)
                && strm;
          }
      }

    return valid;
  }


///=====================================
///  VisibleOnlyFilter MEMBER FUNCTIONS
///=====================================


inline
VisibleOnlyFilter::VisibleOnlyFilter (std::shared_ptr<db::Cursor> &&subject,
                                      std::size_t colGroupID,
                                      std::size_t colVisible,
                                      std::size_t colVisibleVersion,
                                      const GroupVisibilityMap& visibilityMap)
    throw (CursorError)
  : FilteredCursor (std::move(subject)),
    colGroupID (colGroupID),
    colVisible (colVisible),
    colVisibleVersion (colVisibleVersion),
    visibilityMap (visibilityMap)
  { }


bool
VisibleOnlyFilter::accept ()
    const
  {
    auto iter
        (visibilityMap.find (getLong (colGroupID)));

    //
    // If the FeatureSet's checkVersion is true and the FeatureSet's
    // visible_version matches the Feature's, the Feature's visibility is used.
    // Otherwise, the Feature assumes the FeatureSet's visibility.
    //
    return iter != visibilityMap.end ()
        && (iter->second.checkVersion
            && iter->second.version == getInt (colVisibleVersion)
            ? getInt (colVisible)
            : iter->second.visible);
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


PersistentDataSourceFeatureDataStore::PersistentDataSourceFeatureDataStore
    (const char* filePath)
  : FeatureDataStore (MODIFY_BULK_MODIFICATIONS
                      | MODIFY_FEATURESET_DELETE,
                      VISIBILITY_SETTINGS_FEATURE
                      | VISIBILITY_SETTINGS_FEATURESET),
    URI (filePath),
    featureDB (FeatureCatalogDatabase::createDatabase (filePath)),
    inTransaction (false)
  {
//    if (!filePath)
//      {
//        throw std::invalid_argument
//                  (MEM_FN ("PersistentDataSourceFeatureDataStore")
//                   "Received NULL filePath");
//      }
    if (!featureDB.get ())
      {
        throw std::runtime_error
                  (MEM_FN ("PersistentDataSourceFeatureDataStore")
                   "Failed to create FeatureCatalogDatabase");
      }
    featureDB->getCurrencyRegistry ().registerCurrency (new ValidateCurrency);
  }


void
PersistentDataSourceFeatureDataStore::dispose ()
  {
    Lock lock(getMutex());

    featureDB.reset (nullptr);
  }


Feature*
PersistentDataSourceFeatureDataStore::getFeature (int64_t featureID)
  {
    FeatureQueryParameters params;

    params.IDs.push_back (featureID);

    std::unique_ptr<FeatureCursor> cursor (queryFeatures (params));

    return cursor->moveToNext () ? cursor->get () : nullptr;
  }


FeatureSet*
PersistentDataSourceFeatureDataStore::getFeatureSet (int64_t featureSetID)
  {
    FeatureSetQueryParameters params;

    params.IDs.push_back (featureSetID);

    std::unique_ptr<FeatureSetCursor> cursor (queryFeatureSets (params));

    return cursor->moveToNext () ? cursor->get () : nullptr;
  }


bool
PersistentDataSourceFeatureDataStore::isAvailable ()
    const
  {
    Lock lock(getMutex());

    return featureDB.get () != nullptr;
  }


bool
PersistentDataSourceFeatureDataStore::isFeatureSetVisible (int64_t featureSetID)
    const
  {
    bool visible (false);
    std::vector<const char*> queryArgs;
    std::ostringstream strm;

    strm << featureSetID;
    queryArgs.push_back (strm.str ().c_str ());

    Lock lock(getMutex());
    std::unique_ptr<db::Cursor> cursor
        (featureDB->query ("SELECT visible, visible_check, visible_version "
                           "FROM groups WHERE id = ?",
                           queryArgs));

    if (cursor->moveToNext ())
      {
        visible = cursor->getInt(0) != 0;

        if (cursor->getInt (1))         // visible_check is true
          {
            queryArgs.insert (queryArgs.begin (), cursor->getString (2));

            cursor.reset (featureDB->query (visible
                                            ? "SELECT 1 FROM Geometry "
                                              "WHERE group_visible_version != ? "
                                              "AND group_id = ? LIMIT 1"
                                            : "SELECT 1 FROM Geometry "
                                              "WHERE visible = 1 "
                                              "AND group_visible_version = ? "
                                              "AND group_id = ? LIMIT 1",
                                              queryArgs));
            visible = cursor->moveToNext ();
          }
      }

    return visible;
  }


bool
PersistentDataSourceFeatureDataStore::isFeatureVisible (int64_t featureID)
    const
  {
    bool visible (false);
    std::vector<const char*> queryArgs;
    std::ostringstream strm;

    strm << featureID;
    queryArgs.push_back (strm.str ().c_str ());

    std::unique_ptr<db::Cursor> cursor
        (featureDB->query ("SELECT groups.visible, "
                                  "groups.visible_check, "
                                  "groups.visible_version, "
                                  "Geometry.visible, "
                                  "Geometry.group_visible_version "
                           "FROM Geometry LEFT JOIN groups "
                           "ON Geometry.group_id = groups.id "
                           "WHERE Geometry.id = ?",
                           queryArgs));

    if (cursor->moveToNext ())
      {
        //
        // If the FeatureSet's visible_check is true and the FeatureSet's
        // visible_version matches the Feature's, the Feature's visibility is
        // used.  Otherwise, the Feature assumes the FeatureSet's visibility.
        //
        visible = cursor->getInt (1) && cursor->getInt (2) == cursor->getInt (4)
            ? (cursor->getInt (3) != 0)
            : (cursor->getInt (0) != 0);
      }

    return visible;
  }


FeatureDataStore::FeatureCursor*
PersistentDataSourceFeatureDataStore::queryFeatures
    (const FeatureQueryParameters& params)
    const
  {
    enum
      {
        colID,
        colGroupID,
        colName,
        colGeom,
        colStyle,
        colAttribs,
        colVisibleVersion,
        colVisible
      };

    std::ostringstream sqlStrm;

    sqlStrm << "SELECT Geometry.id, Geometry.group_id, Geometry.name, "
            << "Geometry.geom, Style.style_rep, NULL";

    GroupVisibilityMap visibilityMap;
    db::WhereClauseBuilder wheres;

    if (!params.visibleOnly)
      {
        buildWheres (params, wheres);
      }
    else
      {
        sqlStrm << ", Geometry.group_visible_version, Geometry.visible";

        //
        // Restrict to groups that may be visible (i.e., groups that are
        // explicitly marked as visible or have the visible_check flag set.
        // Add in any restrictions on FeatureSet names or IDs.
        //
        db::WhereClauseBuilder groupWheres;

        groupWheres.newConjunct ().append ("(visible = 1 OR visible_check > 0)");
        if (!params.featureSetIDs.empty ())
          {
            groupWheres.newConjunct ().appendIn ("id", params.featureSetIDs);
          }
        if (!params.featureSetNames.empty ())
          {
            groupWheres.newConjunct ().appendIn ("name", params.featureSetNames);
          }

        //
        // Make a copy of the FeatureQueryParameters in which FeatureSet IDs are
        // only those that are visible and FeatureSet names are cleared.
        //
        FeatureQueryParameters visParams (params);

        visParams.featureSetIDs.clear ();
        visParams.featureSetNames.clear ();

        std::ostringstream sql;
        sql << "SELECT id, visible, visible_check, visible_version "
            << "FROM groups WHERE "
            << groupWheres.getClause();
        std::unique_ptr<db::Cursor> cursor
            (featureDB->query
                 (sql.str().c_str(),
                  groupWheres.getArgs ()));

        while (cursor->moveToNext ())
          {
            int64_t featureSetID (cursor->getLong (0));

            visParams.featureSetIDs.push_back (featureSetID);
            visibilityMap.insert (std::make_pair
                                      (featureSetID,
                                       GroupVisibility (cursor->getInt (1) != 0,
                                                        cursor->getInt (2) != 0,
                                                        cursor->getInt (3))));
          }

        buildWheres (visParams, wheres);
      }

    if (!params.orders.empty ())
      {
        std::for_each (params.orders.begin (), params.orders.end (),
                       FeatureOrderAppender (wheres));
      }
    if (params.resultLimit || params.resultOffset)
      {
        //
        // If an offset, but no limit was specified, use -1 for the limit.
        //
        wheres.append (" LIMIT ? OFFSET ?")
              .addArg (params.resultLimit
                       ? static_cast<long> (params.resultLimit) : -1)
              .addArg (params.resultOffset);
      }

    sqlStrm << " FROM Geometry LEFT JOIN Style ON Geometry.style_id = Style.id";
    if (!wheres.empty ())
      {
        sqlStrm << " WHERE " << wheres.getClause ();
      }

    std::shared_ptr<db::Cursor> cursor (featureDB->query (sqlStrm.str ().c_str (),
                                                        wheres.getArgs ()));

    if (params.visibleOnly)
      {
        cursor.reset (new VisibleOnlyFilter (std::move(cursor),
                                             colGroupID,
                                             colVisible,
                                             colVisibleVersion,
                                             visibilityMap));
      }

    return new FeatureCursorImpl (cursor,
                                  colID, colGroupID, colName,
                                  colGeom, colStyle, colAttribs,
                                  adopt);
  }


std::size_t
PersistentDataSourceFeatureDataStore::queryFeaturesCount
    (const FeatureQueryParameters& params)
    const
  {
    std::size_t resultCount (0);

    if (params.visibleOnly)
      {
        std::unique_ptr<FeatureCursor> cursor (queryFeatures (params));

        while (cursor->moveToNext ())
          {
            ++resultCount;
          }
      }
    else
      {
        std::ostringstream sqlStrm;
        db::WhereClauseBuilder wheres;

        buildWheres (params, wheres);
        sqlStrm << "SELECT Count(1) FROM Geometry";
        if (!wheres.empty ())
          {
            sqlStrm << " WHERE " << wheres.getClause ();
          }

        std::unique_ptr<db::Cursor> cursor
            (featureDB->query (sqlStrm.str ().c_str (), wheres.getArgs ()));

        if (cursor->moveToNext ())
          {
            resultCount = cursor->getInt (0);
            if (params.resultOffset && params.resultOffset < resultCount)
              {
                resultCount -= params.resultOffset;
              }
            if (params.resultLimit)
              {
                resultCount = std::min (params.resultLimit, resultCount);
              }
          }
      }

    return resultCount;
  }


FeatureDataStore::FeatureSetCursor*
PersistentDataSourceFeatureDataStore::queryFeatureSets
    (const FeatureSetQueryParameters& params)
    const
  {
    enum
      {
        colID,
        colName,
        colProvider,
        colType,
        colMinGSD,
        colMaxGSD
      };
    static const char* const groupSelect
        ("SELECT id, name, provider, type, min_gsd, max_gsd "
         "FROM groups");

    db::WhereClauseBuilder wheres;

    buildWheres (params, wheres);
    if (!params.orders.empty ())
      {
        std::for_each (params.orders.begin (), params.orders.end (),
                       FeatureSetOrderAppender (wheres));
      }
    if (params.resultLimit || params.resultOffset)
      {
        //
        // If an offset, but no limit was specified, use -1 for the limit.
        //
        wheres.append (" LIMIT ? OFFSET ?")
              .addArg (params.resultLimit
                       ? static_cast<long> (params.resultLimit) : -1)
              .addArg (params.resultOffset);
      }

    std::ostringstream sqlStrm;

    sqlStrm << groupSelect;
    if (!wheres.empty ())
      {
        sqlStrm << " WHERE " << wheres.getClause ();
      }

    std::unique_ptr<db::Cursor> cursor
        (featureDB->query (sqlStrm.str ().c_str (), wheres.getArgs ()));

    return new FeatureSetCursorImpl (std::move(cursor),
                                     colID, colName, colProvider,
                                     colType, colMinGSD, colMaxGSD,
                                     *this, &PersistentDataSourceFeatureDataStore::adopt);
  }


std::size_t
PersistentDataSourceFeatureDataStore::queryFeatureSetsCount
    (const FeatureSetQueryParameters& params)
    const
  {
    std::ostringstream sqlStrm;
    db::WhereClauseBuilder wheres;

    buildWheres (params, wheres);
    sqlStrm << "SELECT Count(1) FROM groups";
    if (!wheres.empty ())
      {
        sqlStrm << " WHERE " << wheres.getClause ();
      }

    std::size_t resultCount (0);
    std::unique_ptr<db::Cursor> cursor
        (featureDB->query (sqlStrm.str ().c_str (), wheres.getArgs ()));

    if (cursor->moveToNext ())
      {
        resultCount = cursor->getInt (0);
        if (params.resultOffset && params.resultOffset < resultCount)
          {
            resultCount -= params.resultOffset;
          }
        if (params.resultLimit)
          {
            resultCount = std::min (params.resultLimit, resultCount);
          }
      }

    return resultCount;
  }


db::FileCursor*
PersistentDataSourceFeatureDataStore::queryFiles ()
    const
  {
    std::unique_ptr<db::CatalogDatabase::Cursor> result(featureDB->queryCatalog());
    return new db::CatalogDatabase::FileCursor (std::move(result));
  }


void
PersistentDataSourceFeatureDataStore::refresh ()
  {
    Lock lock(getMutex());
    BulkTransaction transaction (*this);

    featureDB->validateCatalog ();
    notifyContentListeners ();
    transaction.commit ();
  }


}                                       // Close feature namespace.
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
namespace feature                       // Open feature namespace.
{


bool
PersistentDataSourceFeatureDataStore::addFileImpl
    (const char* filePath,
     FeatureDataSource::Content& content)
  {
    typedef std::map<TAK::Engine::Port::String, int64_t, TAK::Engine::Port::StringLess> StyleID_Map;

    std::size_t featureCount (0);
    std::shared_ptr<GenerateCurrency> currency
        (new GenerateCurrency (content));
    int64_t catalogID (featureDB->addCatalogEntry (filePath, *currency));

    while (content.moveToNextFeatureSet ())
      {
        double minResolution (content.getMinResolution ());
        double maxResolution (content.getMaxResolution ());
        int64_t groupID (featureDB->addGroup (catalogID,
                                              content.getProvider (),
                                              content.getType (),
                                              filePath,
                                              minResolution,
                                              maxResolution));
        StyleID_Map styleMap;

        while (content.moveToNextFeature())
          {
            std::unique_ptr<FeatureDataSource::FeatureDefinition> def
                (content.get ());
            int64_t styleID (0);
            const void* rawStyle (def->getRawStyle ());

            if (rawStyle)
              {
                TAK::Engine::Port::String styleString;

                if (def->getStyling () == FeatureDataSource::FeatureDefinition::OGR)
                  {
                    styleString = static_cast<const char*> (rawStyle);
                  }
                else    // Convert feature::Style to OGR representation.
                  {
                     static_cast<const Style*> (rawStyle)->toOGR (styleString);
                  }
                if (styleString)
                  {
                    auto iter (styleMap.find (styleString));

                    if (iter == styleMap.end ())
                      {
                        iter = styleMap.insert
                                   (std::make_pair
                                        (styleString,
                                         featureDB->addStyle (catalogID,
                                                              styleString))).first;
                      }
                    styleID = iter->second;
                  }
              }
            featureDB->addFeature (catalogID, groupID, *def, styleID,
                                   minResolution, maxResolution);
            ++featureCount;
          }
      }

    return featureCount != 0;
  }


void
PersistentDataSourceFeatureDataStore::beginBulkModificationImpl ()
  {
    if (inTransaction)
      {
        throw std::runtime_error (MEM_FN ("beginBulkModificationImpl")
                                  "Already within bulk modification");
      }
    featureDB->beginTransaction ();
    inTransaction = true;
  }


bool
PersistentDataSourceFeatureDataStore::containsFileImpl (const char* filePath)
    const
  {
    return std::unique_ptr<db::Cursor> (featureDB->queryCatalogPath (filePath))
               ->moveToNext ();
  }


void
PersistentDataSourceFeatureDataStore::deleteAllFeatureSetsImpl ()
  {
    Lock lock(getMutex());

    featureDB->deleteAll ();
  }


void
PersistentDataSourceFeatureDataStore::deleteFeatureSetImpl (int64_t featureSetID)
  {
    Lock lock(getMutex());

    removeFileInternal (getFileInternal (featureSetID), true);
  }


void
PersistentDataSourceFeatureDataStore::endBulkModificationImpl (bool successful)
  {
    if (!inTransaction)
      {
        throw std::runtime_error (MEM_FN ("endBulkModificationImpl")
                                  "Not within bulk modification");
      }
    try
      {
        if (successful)
          {
            featureDB->setTransactionSuccessful ();
          }
        featureDB->endTransaction ();
      }
    catch (...)
      { }
    inTransaction = false;
  }


const char*
PersistentDataSourceFeatureDataStore::getFileImpl (int64_t featureSetID)
    const
  {
    std::ostringstream strm;

    strm << featureSetID;

    std::unique_ptr<db::Cursor> cursor
        (featureDB->query ("SELECT catalog.path FROM groups LEFT JOIN catalog "
                           "ON groups.file_id = catalog.id WHERE groups.id = ?",
                           std::vector<const char*> (1, strm.str ().c_str ())));

    return cursor->moveToNext () ? cursor->getString (0) : nullptr;
  }


void
PersistentDataSourceFeatureDataStore::removeFileImpl (const char* filePath)
  { featureDB->deleteCatalogPath (filePath); }


void
PersistentDataSourceFeatureDataStore::setFeatureSetVisibleImpl (int64_t featureSetID,
                                                                bool visible)
  {
    std::unique_ptr<db::Statement> stmt
        (featureDB->compileStatement ("UPDATE groups SET visible = ?, "
                                      "visible_version = visible_version+1, "
                                      "visible_check = 0 WHERE id = ?"));

    stmt->bind (1, visible ? 1 : 0);
    stmt->bind (2, featureSetID);
    stmt->execute ();
    notifyContentListeners ();
  }


void
PersistentDataSourceFeatureDataStore::setFeatureVisibleImpl (int64_t featureID,
                                                             bool visible)
  {
    std::ostringstream strm;

    strm << featureID;

    std::unique_ptr<db::Cursor> cursor
        (featureDB->query ("SELECT Geometry.group_id, groups.visible_version "
                           "FROM Geometry LEFT JOIN groups "
                           "ON Geometry.group_id = groups.id "
                           "WHERE Geometry.id = ?",
                           std::vector<const char*> (1, strm.str ().c_str ())));

    if (cursor->moveToNext ())
      {
        std::unique_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE Geometry SET visible = ?, "
                                          "group_visible_version = ? "
                                          "WHERE id = ?"));

        stmt->bind (1, visible ? 1 : 0);
        stmt->bind (2, cursor->getInt (1));
        stmt->bind (3, featureID);
        stmt->execute ();
        stmt.reset (featureDB->compileStatement
                        ("UPDATE groups SET visible_check = ? WHERE id = ?"));
        stmt->bind (1, 1);
        stmt->bind (2, cursor->getLong (0));
        stmt->execute ();
        notifyContentListeners ();
      }
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.

