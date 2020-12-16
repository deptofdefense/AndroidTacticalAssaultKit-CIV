
//NOTE: This is a modified version of MutableFeatureDataStore.cpp intended to add temporary
//      support for storing AttributeSets until a more perminent solution arrives.

////============================================================================
////
////    FILE:           PersistentFeatureDataStore.cpp
////
////    DESCRIPTION:    Implementation of the PersistentFeatureDataStore class
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Mar 23, 2015  scott           Created.
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


#include "feature/PersistentFeatureDataStore.h"

#include <algorithm>
#include <iostream>
#include <stdexcept>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "db/Statement.h"
#include "db/WhereClauseBuilder.h"
#include "feature/Feature.h"
#include "feature/AttributedFeatureDatabase.h"
#include "feature/FeatureSet.h"
#include "feature/Geometry.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"
#include "util/IO.h"


#define MEM_FN( fn ) "atakmap::feature::PersistentFeatureDataStore::" fn ": "


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
    
    
    typedef db::DataStore::QueryParameters::Order   Order;
    typedef PGSC::RefCountableIndirectPtr<Order>    OrderPtr;
    
    
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
        
        
        typedef void (*Adopter) (feature::Feature&, int64_t, int64_t, unsigned long);
        
        
        //==================================
        //  PUBLIC INTERFACE
        //==================================
        
        
        FeatureCursorImpl (db::Cursor* subject,
                           std::size_t colID,
                           std::size_t colGroupID,
                           std::size_t colVersion,
                           std::size_t colName,
                           std::size_t colGeom,
                           std::size_t colStyle,
                           std::size_t colAttribs,
                           Adopter adopt)
        : FeatureCursor (subject),
        colID (colID),
        colGroupID (colGroupID),
        colVersion (colVersion),
        colName (colName),
        colGeom (colGeom),
        colStyle (colStyle),
        colAttribs (colAttribs),
        adopt (adopt)
        { }
        
        
        //==================================
        //  FeatureDataStore::FeatureCursor INTERFACE
        //==================================
        
        
        feature::Feature*
        get ()
        const
        throw (CursorError);
        
        
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
        std::size_t colVersion;
        std::size_t colName;
        std::size_t colGeom;
        std::size_t colStyle;
        std::size_t colAttribs;
        Adopter adopt;
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
        (feature::FeatureSet&, int64_t, unsigned long) const;
        
        //==================================
        //  PUBLIC INTERFACE
        //==================================
        
        
        FeatureSetCursorImpl (db::Cursor* subject,
                              std::size_t colID,
                              std::size_t colVersion,
                              std::size_t colName,
                              std::size_t colProvider,
                              std::size_t colType,
                              std::size_t colMinGSD,
                              std::size_t colMaxGSD,
                              const feature::FeatureDataStore& owner,
                              Adopter adopt)
        : FeatureSetCursor (subject),
        colID (colID),
        colVersion (colVersion),
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
        throw (CursorError);
        
        
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
        std::size_t colVersion;
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
        
        
        VisibleOnlyFilter (db::Cursor*,
                           std::size_t colGroupID,
                           std::size_t colVisible,
                           std::size_t colVisibleVersion,
                           const GroupVisibilityMap&)
        throw (CursorError);            // Thrown if subject is NULL.
        
        ~VisibleOnlyFilter ()
        throw ()
        { }
        
        //
        // The compiler-generated copy constructor and assignment are acceptable.
        //
        
        
        //==================================
        //  db::FilteredCursor INTERFACE
        //==================================
        
        
        bool
        accept ()
        const;
        
        
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
        typedef db::DataStore::QueryParameters::RadiusFilter      RadiusFilter;
        typedef db::DataStore::QueryParameters::RegionFilter      RegionFilter;
        
        const RadiusFilter* radiusFilter (NULL);
        const RegionFilter* regionFilter (NULL);
        
        if ((radiusFilter = dynamic_cast<const RadiusFilter*> (filter)))
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
                wheres.append ("Distance(")
                .append (ptStrm.str ().c_str ())
                .append (", Geometry.geom, 1) <= ?")
                .addArg (radiusFilter->radius);
            }
            else
            {
                wheres.append ("Within(")
                .append (ptStrm.str ().c_str ())
                .append (", Geometry.geom) = 1");
            }
        }
        else if ((regionFilter = dynamic_cast<const RegionFilter*> (filter)))
        {
            const core::GeoPoint& ul (regionFilter->upperLeft);
            const core::GeoPoint& lr (regionFilter->lowerRight);
            std::ostringstream mbrStrm;
            
            mbrStrm << "BuildMbr(" << std::setprecision (16)
            << ul.longitude << ", " << ul.latitude << ", "
            << lr.longitude << ", " << lr.latitude << ", 4326)";
            
            if (feature::AttributedFeatureDatabase::SPATIAL_INDEX_ENABLED)
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
        if (params.minResolution && !isnan(params.minResolution))
        {
            wheres.newConjunct ()
            .append ("Geometry.min_gsd >= ?").addArg (params.minResolution);
        }
        if (params.maxResolution && !isnan(params.maxResolution))
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
        
        std::auto_ptr<feature::Geometry> geometry;
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
        
        std::auto_ptr<feature::Style> style;
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
        
        util::AttributeSet attributes;
        Blob attribsBlob (getBlob (colAttribs));
        
        if (attribsBlob.first && attribsBlob.second)
        {
            //
            // Parse the blob into an AttributeSet.
            //
            // attributes.reset (...);
            atakmap::feature::AttributedFeatureDatabase::deserializeAttributeSet(attributes, attribsBlob);
        }
        
        std::auto_ptr<feature::Feature> result
        (new feature::Feature (getString (colName),
                               geometry.release (),
                               style.release (),
                               attributes));
        
        adopt (*result, getLong (colGroupID), featureID, getLong (colVersion));
        
        return result.release ();
    }
    
    
    ///=====================================
    ///  FeatureOrderAppender MEMBER FUNCTIONS
    ///=====================================
    
    
    void
    FeatureOrderAppender::appendOrder (const Order* order)
    {
        const OrderByID* byID (NULL);
        const OrderByName* byName (NULL);
        const OrderByResolution* byResolution (NULL);
        const OrderByDistance* byDistance (NULL);
        const OrderByFeatureSetID* byFeatureSetID (NULL);
        const OrderByGeometry* byGeometryType (NULL);
        
        if ((byID = dynamic_cast<const OrderByID*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.id");
        }
        else if ((byName = dynamic_cast<const OrderByName*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.name");
        }
        else if ((byFeatureSetID = dynamic_cast<const OrderByFeatureSetID*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.group_id");
        }
        else if ((byResolution = dynamic_cast<const OrderByResolution*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("Geometry.max_gsd");
        }
        else if ((byDistance = dynamic_cast<const OrderByDistance*> (order)))
        {
            const core::GeoPoint& point (byDistance->point);
            std::ostringstream strm;
            
            strm << (first ? " ORDER BY " : ", ")
            << "Distance(Geometry.geom, MakePoint("
            << std::setprecision (16)
            << point.longitude << ", " << point.latitude << ", 4326), 1)";
            wheres.append (strm.str ().c_str ());
        }
        else if ((byGeometryType = dynamic_cast<const OrderByGeometry*> (order)))
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
        std::auto_ptr<feature::FeatureSet> result
        (new feature::FeatureSet (getString (colProvider),
                                  getString (colType),
                                  getString (colName),
                                  getDouble (colMinGSD),
                                  getDouble (colMaxGSD)));
        
        (owner.*adopt) (*result, getLong (colID), getLong (colVersion));
        return result.release ();
    }
    
    
    ///=====================================
    ///  FeatureSetOrderAppender MEMBER FUNCTIONS
    ///=====================================
    
    
    void
    FeatureSetOrderAppender::appendOrder (const Order* order)
    {
        const OrderByID* byID (NULL);
        const OrderByName* byName (NULL);
        const OrderByProvider* byProvider (NULL);
        const OrderByResolution* byResolution (NULL);
        const OrderByType* byType (NULL);
        
        if ((byID = dynamic_cast<const OrderByID*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("id");
        }
        else if ((byName = dynamic_cast<const OrderByName*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("name");
        }
        else if ((byProvider = dynamic_cast<const OrderByProvider*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("provider");
        }
        else if ((byResolution = dynamic_cast<const OrderByResolution*> (order)))
        {
            wheres.append (first ? " ORDER BY " : ", ").append ("max_gsd");
        }
        else if ((byType = dynamic_cast<const OrderByType*> (order)))
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
    ///  VisibleOnlyFilter MEMBER FUNCTIONS
    ///=====================================
    
    
    inline
    VisibleOnlyFilter::VisibleOnlyFilter (db::Cursor* subject,
                                          std::size_t colGroupID,
                                          std::size_t colVisible,
                                          std::size_t colVisibleVersion,
                                          const GroupVisibilityMap& visibilityMap)
    throw (CursorError)
    : FilteredCursor (subject),
    colGroupID (colGroupID),
    colVisible (colVisible),
    colVisibleVersion (colVisibleVersion),
    visibilityMap (visibilityMap)
    { }
    
    
    bool
    VisibleOnlyFilter::accept ()
    const
    {
        GroupVisibilityMap::const_iterator iter
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
        
        
        PersistentFeatureDataStore::PersistentFeatureDataStore
        (const char* filePath)
        : FeatureDataStore (MODIFY_FEATURESET_INSERT
                            | MODIFY_FEATURESET_UPDATE
                            | MODIFY_FEATURESET_DELETE
                            | MODIFY_BULK_MODIFICATIONS
                            | MODIFY_FEATURESET_FEATURE_INSERT
                            | MODIFY_FEATURESET_FEATURE_UPDATE
                            | MODIFY_FEATURESET_FEATURE_DELETE
                            | MODIFY_FEATURESET_NAME
                            | MODIFY_FEATURESET_DISPLAY_THRESHOLDS
                            | MODIFY_FEATURE_NAME
                            | MODIFY_FEATURE_GEOMETRY
                            | MODIFY_FEATURE_STYLE
                            | MODIFY_FEATURE_ATTRIBUTES,
                            VISIBILITY_SETTINGS_FEATURE
                            | VISIBILITY_SETTINGS_FEATURESET),
        URI (filePath),
        featureDB (AttributedFeatureDatabase::createDatabase (filePath)),
        inTransaction (false),
        contentChanged (false)
        {
            //    if (!filePath)
            //      {
            //        throw std::invalid_argument
            //                  (MEM_FN ("PersistentFeatureDataStore")
            //                   "Received NULL filePath");
            //      }
            if (!featureDB.get ())
            {
                throw std::runtime_error
                (MEM_FN ("PersistentFeatureDataStore")
                 "Failed to create AttributedFeatureDatabase");
            }
        }
        
        
        void
        PersistentFeatureDataStore::dispose ()
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            featureDB.reset (NULL);
        }
        
        
        Feature*
        PersistentFeatureDataStore::getFeature (int64_t featureID)
        {
            FeatureQueryParameters params;
            
            params.IDs.push_back (featureID);
            
            std::auto_ptr<FeatureCursor> cursor (queryFeatures (params));
            
            return cursor->moveToNext () ? cursor->get () : NULL;
        }
        
        
        FeatureSet*
        PersistentFeatureDataStore::getFeatureSet (int64_t featureSetID)
        {
            FeatureSetQueryParameters params;
            
            params.IDs.push_back (featureSetID);
            
            std::auto_ptr<FeatureSetCursor> cursor (queryFeatureSets (params));
            
            return cursor->moveToNext () ? cursor->get () : NULL;
        }
        
        
        bool
        PersistentFeatureDataStore::isAvailable ()
        const
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            return featureDB.get ();
        }
        
        
        bool
        PersistentFeatureDataStore::isFeatureSetVisible (int64_t featureSetID)
        const
        {
            bool visible (false);
            std::vector<const char*> queryArgs;
            std::ostringstream strm;
            
            strm << featureSetID;
            queryArgs.push_back (strm.str ().c_str ());
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query ("SELECT visible, visible_check, visible_version "
                               "FROM groups WHERE id = ?",
                               queryArgs));
            
            if (cursor->moveToNext ())
            {
                visible = cursor->getInt (0);
                
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
        PersistentFeatureDataStore::isFeatureVisible (int64_t featureID)
        const
        {
            std::vector<const char*> queryArgs;
            std::ostringstream strm;
            
            strm << featureID;
            queryArgs.push_back (strm.str ().c_str ());
            
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query ("SELECT groups.visible, "
                               "groups.visible_check, "
                               "groups.visible_version, "
                               "Geometry.visible, "
                               "Geometry.group_visible_version "
                               "FROM Geometry LEFT JOIN groups "
                               "ON Geometry.group_id = groups.id "
                               "WHERE Geometry.id = ?",
                               queryArgs));
            
            //
            // If the FeatureSet's visible_check is true and the FeatureSet's
            // visible_version matches the Feature's, the Feature's visibility is used.
            // Otherwise, the Feature assumes the FeatureSet's visibility.
            //
            
            return cursor->moveToNext ()
            && (cursor->getInt (1) && cursor->getInt (2) == cursor->getInt (4)
                ? cursor->getInt (3)
                : cursor->getInt (0));
        }
        
        
        FeatureDataStore::FeatureCursor*
        PersistentFeatureDataStore::queryFeatures (const FeatureQueryParameters& params)
        const
        {
            enum
            {
                colID,
                colGroupID,
                colVersion,
                colName,
                colGeom,
                colStyle,
                colAttribs,
                colVisibleVersion,
                colVisible
            };
            static const char* const featureSelect
            ("SELECT Geometry.id, Geometry.group_id, Geometry.version, "
             "Geometry.name, Geometry.geom, Style.style_rep, Geometry.meta");
            static const char* const visibilityColumns
            (", Geometry.group_visible_version, Geometry.visible");
            
            std::ostringstream sqlStrm;
            
            sqlStrm << featureSelect;
            
            GroupVisibilityMap visibilityMap;
            db::WhereClauseBuilder wheres;
            
            if (!params.visibleOnly)
            {
                buildWheres (params, wheres);
            }
            else
            {
                sqlStrm << visibilityColumns;   // Add the visibility columns.
                
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
                
                std::auto_ptr<db::Cursor> cursor
                (featureDB->query
                 (PGSC::String
                  (PGSC::catStrings
                   ("SELECT id, visible, visible_check, visible_version "
                    "FROM groups WHERE ",
                    groupWheres.getClause ())),
                  groupWheres.getArgs ()));
                
                while (cursor->moveToNext ())
                {
                    int64_t featureSetID (cursor->getLong (0));
                    
                    visParams.featureSetIDs.push_back (featureSetID);
                    visibilityMap.insert (std::make_pair
                                          (featureSetID,
                                           GroupVisibility (cursor->getInt (1),
                                                            cursor->getInt (2),
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
                PGSC::String clause = wheres.getClause();
                //XXX-- hack
                if (strcmp(clause, " LIMIT ? OFFSET ?") != 0) {
                    sqlStrm << " WHERE " << clause;
                } else {
                    sqlStrm << clause;
                }
            }
            
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query (sqlStrm.str ().c_str (), wheres.getArgs ()));
            
            if (params.visibleOnly)
            {
                cursor.reset (new VisibleOnlyFilter (cursor.release (),
                                                     colGroupID,
                                                     colVisible,
                                                     colVisibleVersion,
                                                     visibilityMap));
            }
            
            return new FeatureCursorImpl (cursor.release (),
                                          colID, colGroupID, colVersion, colName,
                                          colGeom, colStyle, colAttribs,
                                          adopt);
        }
        
        
        std::size_t
        PersistentFeatureDataStore::queryFeaturesCount
        (const FeatureQueryParameters& params)
        const
        {
            std::size_t resultCount (0);
            
            if (params.visibleOnly)
            {
                std::auto_ptr<FeatureCursor> cursor (queryFeatures (params));
                
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
                
                std::auto_ptr<db::Cursor> cursor
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
        PersistentFeatureDataStore::queryFeatureSets
        (const FeatureSetQueryParameters& params)
        const
        {
            enum
            {
                colID,
                colVersion,
                colName,
                colProvider,
                colType,
                colMinGSD,
                colMaxGSD
            };
            static const char* const groupSelect
            ("SELECT id, version, name, provider, type, min_gsd, max_gsd "
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
            
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query (sqlStrm.str ().c_str (), wheres.getArgs ()));
            
            return new FeatureSetCursorImpl (cursor.release (),
                                             colID, colVersion, colName, colProvider,
                                             colType, colMinGSD, colMaxGSD,
                                             *this, &PersistentFeatureDataStore::adopt);
        }
        
        
        std::size_t
        PersistentFeatureDataStore::queryFeatureSetsCount
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
            std::auto_ptr<db::Cursor> cursor
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
        
        
        void
        PersistentFeatureDataStore::beginBulkModificationImpl ()
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            if (inTransaction)
            {
                throw std::runtime_error (MEM_FN ("beginBulkModificationImpl")
                                          "Already within bulk modification");
            }
            featureDB->beginTransaction ();
            inTransaction = true;
        }
        
        bool
        PersistentFeatureDataStore::isInBulkModification() const {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            return inTransaction;
        }
        
        
        void
        PersistentFeatureDataStore::deleteAllFeatureSetsImpl ()
        {
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("DELETE FROM Geometry"));
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            //
            // Create a block as a Transaction scope.  The transaction must commit
            // before notifying ContentListeners.
            //
            {
                AttributedFeatureDatabase::Transaction trans (*featureDB);
                
                stmt->execute ();
                contentChanged |= db::lastChangeCount (*featureDB);
                stmt.reset (featureDB->compileStatement ("DELETE FROM groups"));
                stmt->execute ();
                featureDB->setTransactionSuccessful ();
            }
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::deleteAllFeaturesImpl (int64_t featureSetID)
        {
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("DELETE FROM Geometry WHERE group_id = ?"));
            
            stmt->bind (1, featureSetID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            stmt->execute ();
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::deleteFeatureImpl (int64_t featureID)
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            featureDB->deleteFeature (featureID);
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::deleteFeatureSetImpl (int64_t featureSetID)
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            featureDB->deleteGroup (featureSetID);
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::endBulkModificationImpl (bool successful)
        {
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
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
            notifyContentListeners ();
        }
        
        
        Feature*
        PersistentFeatureDataStore::insertFeatureImpl (int64_t featureSetID,
                                                    const char* name,
                                                    Geometry* geometry,
                                                    Style* style,
                                                    const util::AttributeSet& attrs,
                                                    bool returnInstance)
        {
            Feature* result (NULL);
            FeatureDataSource::FeatureDefinition featureDef (name, attrs);
            int64_t styleID (0);
            
            if (style)
            {
                styleID = featureDB->addStyle (style->toOGR ());
                
                // XXX--featureDef manages style's memory
                Style *clonedStyle = style->clone();
                featureDef.setStyle (clonedStyle);
            }
            
            // XXX-- featureDef manages geometry's memory
            Geometry *clonedGeometry = geometry->clone();
            featureDef.setGeometry (clonedGeometry);
            
            int64_t featureID (featureDB->addFeature (featureSetID, featureDef, styleID));
            
            if (returnInstance)
            {
                result = featureDef.getFeature ();
                adopt (*result, featureSetID, featureID, 1);
            }
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            contentChanged = true;
            notifyContentListeners ();
            
            return result;
        }
        
        
        FeatureSet*
        PersistentFeatureDataStore::insertFeatureSetImpl (const char* provider,
                                                       const char* type,
                                                       const char* name,
                                                       double minRes,
                                                       double maxRes,
                                                       bool returnInstance)
        {
            FeatureSet* result (NULL);
            int64_t featureSetID (featureDB->addGroup (provider, type, name, minRes, maxRes));
            
            if (returnInstance)
            {
                result = new FeatureSet (provider, type, name, minRes, maxRes);
                adopt (*result, featureSetID, 1);
            }
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            contentChanged = true;
            notifyContentListeners ();
            
            return result;
        }
        
        
        void
        PersistentFeatureDataStore::notifyContentListeners ()
        const
        {
            //
            // Balk if within a transaction.
            //
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            if (!inTransaction && contentChanged)
            {
                FeatureDataStore::notifyContentListeners ();
                contentChanged = false;
            }
        }
        
        
        void
        PersistentFeatureDataStore::setFeatureSetVisibleImpl (int64_t featureSetID,
                                                           bool visible)
        {
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE groups SET visible = ?, "
                                          "visible_version = visible_version+1, "
                                          "visible_check = 0 WHERE id = ?"));
            
            stmt->bind (1, visible ? 1 : 0);
            stmt->bind (2, featureSetID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            stmt->execute ();
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::setFeatureVisibleImpl (int64_t featureID,
                                                        bool visible)
        {
            std::ostringstream strm;
            
            strm << featureID;
            
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query ("SELECT Geometry.group_id, groups.visible_version "
                               "FROM Geometry LEFT JOIN groups "
                               "ON Geometry.id = groups.id WHERE Geometry.id = ?",
                               std::vector<const char*> (1, strm.str ().c_str ())));
            
            if (cursor->moveToNext ())
            {
                std::auto_ptr<db::Statement> stmt
                (featureDB->compileStatement ("UPDATE Geometry SET visible = ?, "
                                              "group_visible_version = ? "
                                              "WHERE id = ?"));
                
                stmt->bind (1, visible ? 1 : 0);
                stmt->bind (2, cursor->getInt (1));
                stmt->bind (3, featureID);
                
                TAKErr code(TE_Ok);
                LockPtr lock(NULL, NULL);
                code = Lock_create(lock, getMutex());
                if (code != TE_Ok)
                    throw std::runtime_error
                    (MEM_FN("PersistentFeatureDataStore")
                    "Failed to acquire mutex");
                
                //
                // Create a block as a Transaction scope.  The transaction must commit
                // before notifying ContentListeners.
                //
                {
                    AttributedFeatureDatabase::Transaction trans (*featureDB);
                    
                    stmt->execute ();
                    contentChanged |= db::lastChangeCount (*featureDB);
                    stmt.reset (featureDB->compileStatement ("UPDATE groups "
                                                             "SET visible_check = ? "
                                                             "WHERE id = ?"));
                    stmt->bind (1, 1);
                    stmt->bind (2, cursor->getLong (0));
                    stmt->execute ();
                    featureDB->setTransactionSuccessful ();
                }
                contentChanged |= db::lastChangeCount (*featureDB);
                notifyContentListeners ();
            }
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureImpl (int64_t featureID,
                                                    const util::AttributeSet& attrs)
        {
            std::vector<uint8_t> buffer;
            AttributedFeatureDatabase::serializeAttributeSet(buffer, attrs);
            
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE Geometry "
                                          "SET version = version + 1, "
                                          "meta = ?"
                                          "WHERE id = ?"));
            
            util::BlobImpl metaBlob = util::makeBlob(&buffer.front(), &buffer.back());
            stmt->bind(1, metaBlob);
            stmt->bind (2, featureID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            stmt->execute ();
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureImpl (int64_t featureID,
                                                    const Geometry& geometry)
        {
            std::ostringstream strm;
            
            geometry.toBlob (strm);
            
            std::string blobString (strm.str ());
            const unsigned char* blob
            (reinterpret_cast<const unsigned char*> (blobString.data ()));
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE Geometry "
                                          "SET version = version + 1, "
                                          "geom = ? "
                                          "WHERE id = ?"));
            
            stmt->bind (1, std::make_pair (blob, blob + blobString.size ()));
            stmt->bind (2, featureID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            stmt->execute ();
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureImpl (int64_t featureID,
                                                    const char* featureName)
        {
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE Geometry "
                                          "SET version = version + 1, "
                                          "name = ? "
                                          "WHERE id = ?"));
            
            stmt->bind (1, featureName);
            stmt->bind (2, featureID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            stmt->execute ();
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureImpl (int64_t featureID,
                                                    const Style& style)
        {
            std::ostringstream strm;
            
            strm << featureID;
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query ("SELECT style_id FROM Geometry WHERE id = ?",
                               std::vector<const char*> (1, strm.str ().c_str ())));
            
            if (cursor->moveToNext ())
            {
                int64_t styleID (cursor->getLong (0));
                PGSC::String styleRep (style.toOGR ());
                
                if (styleID)
                {
                    //
                    // Styles are not shared between Features.  Update the style's
                    // representation.
                    //
                    
                    AttributedFeatureDatabase::Transaction trans (*featureDB);
                    std::auto_ptr<db::Statement> stmt
                    (featureDB->compileStatement ("UPDATE style SET style_rep = ? "
                                                  "WHERE id = ?"));
                    
                    stmt->bind (1, styleRep);
                    stmt->bind (2, styleID);
                    stmt->execute ();
                    contentChanged |= db::lastChangeCount (*featureDB);
                    stmt.reset (featureDB->compileStatement ("UPDATE Geometry "
                                                             "SET version = version + 1 "
                                                             "WHERE id = ?"));
                    stmt->bind (1, featureID);
                    stmt->execute ();
                    featureDB->setTransactionSuccessful ();
                }
                else
                {
                    //
                    // Add a new Style and set it for the Feature.
                    //
                    
                    std::auto_ptr<db::Statement> stmt
                    (featureDB->compileStatement ("UPDATE Geometry "
                                                  "SET version = version + 1, "
                                                  "style_id = ? "
                                                  "WHERE id = ?"));
                    
                    stmt->bind (1, featureDB->addStyle (styleRep));
                    stmt->bind (2, featureID);
                    stmt->execute ();
                }
                
                contentChanged |= db::lastChangeCount (*featureDB);
                notifyContentListeners ();
            }
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureImpl (int64_t featureID,
                                                    const char* featureName,
                                                    const Geometry& geometry,
                                                    const Style& style,
                                                    const util::AttributeSet &attrs)
        {
            //
            // TBD: Add attributes to database.
            //
            
            std::ostringstream strm;
            
            strm << featureID;
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            std::auto_ptr<db::Cursor> cursor
            (featureDB->query ("SELECT style_id FROM Geometry WHERE id = ?",
                               std::vector<const char*> (1, strm.str ().c_str ())));
            
            if (cursor->moveToNext ())
            {
                int64_t styleID (cursor->getLong (0));
                PGSC::String styleRep (style.toOGR ());
                std::ostringstream blobStrm;
                
                std::vector<uint8_t> buffer;
                AttributedFeatureDatabase::serializeAttributeSet(buffer, attrs);
                
                geometry.toBlob (blobStrm);
                
                std::string blobString (blobStrm.str ());
                const unsigned char* blob
                (reinterpret_cast<const unsigned char*> (blobString.data ()));
                
                if (styleID)
                {
                    //
                    // Styles are not shared between Features.  Update the style's
                    // representation.
                    //
                    
                    AttributedFeatureDatabase::Transaction trans (*featureDB);
                    std::auto_ptr<db::Statement> stmt
                    (featureDB->compileStatement ("UPDATE style SET style_rep = ? "
                                                  "WHERE id = ?"));
                    
                    stmt->bind (1, styleRep);
                    stmt->bind (2, styleID);
                    stmt->execute ();
                    contentChanged |= db::lastChangeCount (*featureDB);
                    stmt.reset (featureDB->compileStatement ("UPDATE Geometry "
                                                             "SET version = version + 1, "
                                                             "name = ?, "
                                                             "geom = ?, "
                                                             "meta = ? "
                                                             "WHERE id = ?"));
                    stmt->bind (1, featureName);
                    stmt->bind (2, std::make_pair (blob, blob + blobString.size ()));
                    stmt->bind(3, util::makeBlob(&buffer.front(), &buffer.back() + 1));
                    stmt->bind (4, featureID);
                    stmt->execute ();
                    featureDB->setTransactionSuccessful ();
                }
                else
                {
                    //
                    // Add a new Style and set it for the Feature.
                    //
                    
                    std::auto_ptr<db::Statement> stmt
                    (featureDB->compileStatement ("UPDATE Geometry "
                                                  "SET version = version + 1, "
                                                  "name = ?, "
                                                  "geom = ?, "
                                                  "meta = ?, "
                                                  "style_id = ? "
                                                  "WHERE id = ?"));
                    
                    stmt->bind (1, featureName);
                    stmt->bind (2, std::make_pair (blob, blob + blobString.size ()));
                    stmt->bind(3, util::makeBlob(&buffer.front(), &buffer.back() + 1));
                    stmt->bind (4, featureDB->addStyle (styleRep));
                    stmt->bind (5, featureID);
                    stmt->execute ();
                }
                
                contentChanged |= db::lastChangeCount (*featureDB);
                notifyContentListeners ();
            }
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureSetImpl (int64_t featureSetID,
                                                       const char* featureSetName)
        {
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE groups "
                                          "SET version = version + 1, "
                                          "name = ? "
                                          "WHERE id = ?"));
            
            stmt->bind (1, featureSetName);
            stmt->bind (2, featureSetID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            stmt->execute ();
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureSetImpl (int64_t featureSetID,
                                                       double minResolution,
                                                       double maxResolution)
        {
            AttributedFeatureDatabase::Transaction trans (*featureDB);
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE groups "
                                          "SET version = version + 1, "
                                          "min_gsd = ?, "
                                          "max_gsd = ? "
                                          "WHERE id = ?"));
            
            stmt->bind (1, minResolution);
            stmt->bind (2, maxResolution);
            stmt->bind (3, featureSetID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            //
            // Create a block as a Transaction scope.  The transaction must commit
            // before notifying ContentListeners.
            //
            {
                AttributedFeatureDatabase::Transaction trans (*featureDB);
                
                stmt->execute ();
                contentChanged |= db::lastChangeCount (*featureDB);
                stmt.reset (featureDB->compileStatement ("UPDATE Geometry "
                                                         "SET version = version + 1, "
                                                         "min_gsd = ?, "
                                                         "max_gsd = ? "
                                                         "WHERE group_id = ?"));
                stmt->bind (1, minResolution);
                stmt->bind (2, maxResolution);
                stmt->bind (3, featureSetID);
                stmt->execute ();
                featureDB->setTransactionSuccessful ();
            }
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
        void
        PersistentFeatureDataStore::updateFeatureSetImpl (int64_t featureSetID,
                                                       const char* featureSetName,
                                                       double minResolution,
                                                       double maxResolution)
        {
            AttributedFeatureDatabase::Transaction trans (*featureDB);
            std::auto_ptr<db::Statement> stmt
            (featureDB->compileStatement ("UPDATE groups "
                                          "SET version = version + 1, "
                                          "name = ?, "
                                          "min_gsd = ?, "
                                          "max_gsd = ? "
                                          "WHERE id = ?"));
            
            stmt->bind (1, featureSetName);
            stmt->bind (2, minResolution);
            stmt->bind (3, maxResolution);
            stmt->bind (4, featureSetID);
            
            TAKErr code(TE_Ok);
            LockPtr lock(NULL, NULL);
            code = Lock_create(lock, getMutex());
            if (code != TE_Ok)
                throw std::runtime_error
                (MEM_FN("PersistentFeatureDataStore")
                "Failed to acquire mutex");
            
            //
            // Create a block as a Transaction scope.  The transaction must commit
            // before notifying ContentListeners.
            //
            {
                AttributedFeatureDatabase::Transaction trans (*featureDB);
                
                stmt->execute ();
                contentChanged |= db::lastChangeCount (*featureDB);
                stmt.reset (featureDB->compileStatement ("UPDATE Geometry "
                                                         "SET version = version + 1, "
                                                         "min_gsd = ?, "
                                                         "max_gsd = ? "
                                                         "WHERE group_id = ?"));
                stmt->bind (1, minResolution);
                stmt->bind (2, maxResolution);
                stmt->bind (3, featureSetID);
                stmt->execute ();
                featureDB->setTransactionSuccessful ();
            }
            contentChanged |= db::lastChangeCount (*featureDB);
            notifyContentListeners ();
        }
        
        
    }                                       // Close feature namespace.
}                                       // Close atakmap namespace.

