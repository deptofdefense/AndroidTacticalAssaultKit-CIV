////============================================================================
////
////    FILE:           FeatureDataStore.h
////
////    DESCRIPTION:    Abstract base class for feature data stores.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 14, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_FEATURE_DATA_STORE_H_INCLUDED
#define ATAKMAP_FEATURE_FEATURE_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <map>
#include <stdexcept>
#include <stdint.h>
#include <vector>

#include "core/GeoPoint.h"
#include "db/Cursor.h"
#include "db/DataStore.h"
#ifdef MSVC
#include "feature/Geometry.h"
#endif
#include "port/String.h"
#include "port/Platform.h"
#include "util/AttributeSet.h"
#include "util/NonCopyable.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


class ENGINE_API Feature;
class ENGINE_API FeatureSet;
#ifndef MSVC
class ENGINE_API Geometry;
#endif
class ENGINE_API Style;


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::FeatureDataStore
///
///     Abstract base class for feature data stores.
///
///=============================================================================


class ENGINE_API FeatureDataStore
  : public db::DataStoreImpl<FeatureDataStore>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

      
    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class ENGINE_API BulkTransaction;
    class ENGINE_API FeatureCursor;
    class ENGINE_API FeatureSetCursor;
    struct ENGINE_API FeatureQueryParameters;
    struct ENGINE_API FeatureSetQueryParameters;

    enum
      {
        ID_NONE         = 0,
        VERSION_NONE    = 0,

        VISIBILITY_SETTINGS_FEATURE     = 0x01,
        VISIBILITY_SETTINGS_FEATURESET  = 0x02,

        MODIFY_FEATURESET_INSERT                = 0x000001,
        MODIFY_FEATURESET_UPDATE                = 0x000002,
        MODIFY_FEATURESET_DELETE                = 0x000004,
        MODIFY_BULK_MODIFICATIONS               = 0x000008,

        MODIFY_FEATURESET_FEATURE_INSERT        = 0x010001,
        MODIFY_FEATURESET_FEATURE_UPDATE        = 0x010002,
        MODIFY_FEATURESET_FEATURE_DELETE        = 0x010004,
        MODIFY_FEATURESET_NAME                  = 0x010008,
        MODIFY_FEATURESET_DISPLAY_THRESHOLDS    = 0x010010,

        MODIFY_FEATURE_NAME                     = 0x020001,
        MODIFY_FEATURE_GEOMETRY                 = 0x020002,
        MODIFY_FEATURE_STYLE                    = 0x020004,
        MODIFY_FEATURE_ATTRIBUTES               = 0x020008
      };


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~FeatureDataStore ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //

    //
    // Notifies the data store that a bulk modification will be occurring.  The
    // data store may opt not to commit or notify on content changes until the
    // bulk modification is complete.  An invocation of this method should
    // always be paired with an invocation of endBulkModification once the bulk
    // modification is completed, successfully or otherwise.
    //
    // Nested bulk modifications are considered undefined; the user should
    // always check isInBulkModification before invoking this method.
    //
    // Unless otherwise specified by the implementation, it should be assumed
    // that modifications to the data store during a bulk modification may only
    // be made on the thread that began the bulk modification.
    //
    // Throws std::domain_error if the MODIFY_BULK_MODIFICATIONS modification
    // flag is not supported for this data store.
    //
    void
    beginBulkModification ();

    //
    // Deletes all features for the supplied feature set ID.
    //
    // Throws std::domain_error if the MODIFY_FEATURESET_FEATURE_DELETE
    // modification flag is not supported for this data store.
    //
    void
    deleteAllFeatures (int64_t featureSetID);

    //
    // Deletes all feature sets from the data store.
    //
    // Throws std::domain_error if the MODIFY_FEATURESET_DELETE modification
    // flag is not supported for this data store.
    //
    void
    deleteAllFeatureSets ();

    //
    // Deletes the feature for the supplied feature ID.
    //
    // Throws std::domain_error if the MODIFY_FEATURESET_FEATURE_DELETE
    // modification flag is not supported for this data store.
    //
    void
    deleteFeature (int64_t featureID);

    //
    // Deletes the feature set for the supplied feature set ID.
    //
    // Throws std::domain_error if the MODIFY_FEATURESET_DELETE modification
    // flag is not supported for this data store.
    //
    void
    deleteFeatureSet (int64_t featureSetID);

    //
    // Notifies the data store that the bulk modification is complete.  If
    // unsuccessful, the data store is expected to undo all modifications issued
    // during the bulk modification.
    //
    // Throws std::logic_error if isInBulkModification() would return false.
    // Throws std::domain_error if the MODIFY_BULK_MODIFICATIONS modification
    // flag is not supported for this data store.
    //
    void
    endBulkModification (bool successful);

    //
    // Returns the (possibly NULL) Feature with the supplied ID in the data
    // store.  The returned feature should be considered immutable; behavior is
    // undefined if the feature is actively modified.
    //
    virtual
    Feature*
    getFeature (int64_t featureID)
        = 0;

    //
    // Returns the (possibly NULL) FeatureSet with the supplied ID in the data
    // store.  The returned FeatureSet should be considered immutable; behavior
    // is undefined if the FeatureSet is actively modified.
    //
    virtual
    FeatureSet*
    getFeatureSet (int64_t featureSetID)
        = 0;

    //
    // Returns the bit-wise OR of modification flags for the data store.
    // Methods that modify the data store may only be successfully invoked if
    // the associated bit flag is present.
    //
    unsigned int
    getModificationFlags ()
        const
      { return modificationFlags; }

    virtual
    const char*
    getURI ()
        const
        = 0;

    //
    // Returns the visibility settings flags for the data store.  These flags
    // reflect what visibility settings are available over the feature sets and
    // features.
    //
    unsigned int
    getVisibilitySettingsFlags ()
        const
      { return visibilityFlags; }

    //
    // Inserts a Feature with the supplied feature set ID, name, geometry, style,
    // and attributes to the data store.  If the supplied value of
    // returnInstance is true, a Feature instance will be returned; otherwise
    // NULL is returned (to reduce overhead in the event that the Feature is not
    // going to be immediately used).  The returned Feature instance should be
    // considered immutable.
    //
    // Throws std::invalid_argument if the supplied name or geometry is NULL.
    // Throws std::domain_error if the MODIFY_FEATURESET_FEATURE_INSERT
    // modification flag is not set for this data store.
    //
    Feature*
    insertFeature (int64_t featureSetID,
                   const char* name,            // Must not be NULL.
                   Geometry*,                   // Must not be NULL.
                   Style*,                      // May be NULL.
                   const util::AttributeSet&,
                   bool returnInstance = false);

    //
    // Inserts a FeatureSet with the supplied provider, type, name, and minimum
    // and maximum display resolutions to the data store.  If the supplied value
    // of returnInstance is true, a FeatureSet instance will be returned;
    // otherwise NULL is returned (to reduce overhead in the event that the
    // FeatureSet is not going to be immediately used).  The returned FeatureSet
    // instance should be considered immutable.
    //
    // The optional values for minResolution and maxResolution are ground sample
    // distances (in meters/pixel) of the lowest and highest resolutions at
    // which the features should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // Throws std::invalid_argument if the supplied provider, type, or name is
    // NULL or if the minResolution or maxResolution is negative.
    // Throws std::domain_error if the MODIFY_FEATURESET_INSERT modification
    // flag is not set for this data store.
    //
    FeatureSet*
    insertFeatureSet (const char* provider,             // Must not be NULL.
                      const char* type,                 // Must not be NULL.
                      const char* name,                 // Must not be NULL.
                      double minResolution = 0.0,       // No minimum.
                      double maxResolution = 0.0,       // No maximum.
                      bool returnInstance = false);

    //
    // Returns true if the FeatureSet with supplied ID is visible.  A FeatureSet
    // is considered visible if one or more of its child features are visible.
    //
    virtual
    bool
    isFeatureSetVisible (int64_t featureSetID)
        const
        = 0;

    //
    // Returns true if the Feature with the supplied ID is visible.  If
    // visibility settings are not supported, this method should always return
    // true.  If visibility is only supported at the FeatureSet level, this
    // method should return true if the parent FeatureSet is visible.
    //
    virtual
    bool
    isFeatureVisible (int64_t featureID)
        const
        = 0;

    //
    // Returns true if the data store is currently in a bulk modification.
    //
    virtual
    bool
    isInBulkModification ()
        const
        = 0;

    //
    // Returns a cursor over all Features.  The returned Features should be
    // considered immutable; behavior is undefined if the Features are actively
    // modified.
    //
    FeatureCursor*
    queryFeatures ()
        const;

    //
    // Returns a cursor over all Features satisfying the supplied
    // FeatureQueryParameters.  The returned Features should be considered
    // immutable; behavior is undefined if the Features are actively modified.
    //
    virtual
    FeatureCursor*
    queryFeatures (const FeatureQueryParameters&)
        const
        = 0;

    //
    // Returns the number of Features that would be returned by queryFeatures().
    //
    std::size_t
    queryFeaturesCount ()
        const;

    //
    // Returns the number of Features that satisfy the supplied
    // FeatureQueryParameters.
    //
    virtual
    std::size_t
    queryFeaturesCount (const FeatureQueryParameters&)
        const
        = 0;

    //
    // Returns a cursor over all FeatureSets.  The returned FeatureSets should
    // be considered immutable; behavior is undefined if the FeatureSets are
    // actively modified.
    //
    FeatureSetCursor*
    queryFeatureSets ()
        const;

    //
    // Returns a cursor over all FeatureSets satisfying the supplied
    // FeatureSetQueryParameters.  The returned FeatureSets should be considered
    // immutable; behavior is undefined if the FeatureSets are actively modified.
    //
    virtual
    FeatureSetCursor*
    queryFeatureSets (const FeatureSetQueryParameters&)
        const
        = 0;

    //
    // Returns the number of FeatureSets that would be returned by
    // queryFeatureSets().
    //
    std::size_t
    queryFeatureSetsCount ()
        const;

    //
    // Returns the number of FeatureSets that satisfy the supplied
    // FeatureSetQueryParameters.
    //
    virtual
    std::size_t
    queryFeatureSetsCount (const FeatureSetQueryParameters&)
        const
        = 0;

    //
    // Throws std::domain_error if the result of getVisibilitySettingsFlags()
    // does not have the VISIBILITY_SETTINGS_FEATURESET bit set.
    //
    // Throws std::invalid_argument if no FeatureSet has the supplied ID.
    //
    void
    setFeatureSetVisible (int64_t featureSetID,
                          bool visible);

    //
    // Throws std::domain_error if the result of getVisibilitySettingsFlags()
    // does not have the VISIBILITY_SETTINGS_FEATURE bit set.
    //
    // Throws std::invalid_argument if no Feature has the supplied ID.
    //
    void
    setFeatureVisible (int64_t featureID,
                       bool visible);

    //
    // Updates the Feature with the supplied ID to have the supplied attributes.
    //
    // Throws std::invalid_argument if no Feature has the supplied ID.
    // Throws std::domain_error if the MODIFY_FEATURE_ATTRIBUTES modification
    // flag is not set for this data store.
    //
    void
    updateFeature (int64_t featureID,
                   const util::AttributeSet&);

    //
    // Updates the Feature with the supplied ID to have the supplied Geometry.
    //
    // Throws std::invalid_argument if no Feature has the supplied ID.
    // Throws std::domain_error if the MODIFY_FEATURE_GEOMETRY modification flag
    // is not set for this data store.
    //
    void
    updateFeature (int64_t featureID,
                   const Geometry&);

    //
    // Updates the Feature with the supplied ID to have the supplied name.
    //
    // Throws std::invalid_argument if no Feature has the supplied ID or the
    // supplied name is NULL.
    // Throws std::domain_error if the MODIFY_FEATURE_NAME modification flag is
    // not set for this data store.
    //
    void
    updateFeature (int64_t featureID,
                   const char* featureName);

    //
    // Updates the Feature with the supplied ID to have the supplied Style.
    //
    // Throws std::invalid_argument if no Feature has the supplied ID.
    // Throws std::domain_error if the MODIFY_FEATURE_STYLE modification flag is
    // not set for this data store.
    //
    void
    updateFeature (int64_t featureID,
                   const Style&);

    //
    // Updates the Feature with the supplied ID to have the supplied name,
    // Geometry, Style, and attributes.
    //
    // Throws std::invalid_argument if no Feature has the supplied ID or if the
    // supplied name is NULL.
    // Throws std::domain_error if the MODIFY_FEATURE_NAME,
    // MODIFY_FEATURE_GEOMETRY, MODIFY_FEATURE_STYLE, and
    // MODIFY_FEATURE_ATTRIBUTES modification flag are not set for this data
    // store.
    //
    void
    updateFeature (int64_t featureID,
                   const char* featureName,
                   const Geometry&,
                   const Style&,
                   const util::AttributeSet&);

    //
    // Updates the FeatureSet with the supplied ID to have the supplied name.
    //
    // Throws std::invalid_argument if no FeatureSet has the supplied ID or if
    // the supplied name is NULL.
    // Throws std::domain_error if the MODIFY_FEATURESET_UPDATE and
    // MODIFY_FEATURESET_NAME modification flags are not set for this data store.
    //
    void
    updateFeatureSet (int64_t featureSetID,
                      const char* featureSetName);

    //
    // Updates the FeatureSet with the supplied ID to have the supplied minimum
    // and maximum display resolutions.
    //
    // The values for minResolution and maxResolution are ground sample
    // distances (in meters/pixel) of the lowest and highest resolutions at
    // which the features should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // A minResolution (or maxResolution) of 0.0 indicates that the set's
    // Features have no minimum (or maximum) resolution threshold.
    //
    // Throws std::invalid_argument if no FeatureSet has the supplied ID or if
    // the supplied minResolution or maxResolution is negative.
    // Throws std::domain_error if the MODIFY_FEATURESET_UPDATE and
    // MODIFY_FEATURESET_DISPLAY_THRESHOLDS modification flags are not set for
    // this data store.
    //
    void
    updateFeatureSet (int64_t featureSetID,
                      double minResolution,
                      double maxResolution);

    //
    // Updates the FeatureSet with the supplied ID to have the supplied name and
    // minimum and maximum display resolutions.
    //
    // The values for minResolution and maxResolution are ground sample
    // distances (in meters/pixel) of the lowest and highest resolutions at
    // which the features should be displayed.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the supplied value of
    //          minResolution should be greater than or equal to the value of
    //          maxResolution.
    //
    // A minResolution (or maxResolution) of 0.0 indicates that the set's
    // Features have no minimum (or maximum) resolution threshold.
    //
    // Throws std::invalid_argument if no FeatureSet has the supplied ID or if
    // the supplied name is NULL or if the supplied minResolution or
    // maxResolution is negative.
    // Throws std::domain_error if the MODIFY_FEATURESET_UPDATE,
    // MODIFY_FEATURESET_NAME, and MODIFY_FEATURESET_DISPLAY_THRESHOLDS
    // modification flags are not set for this data store.
    //
    void
    updateFeatureSet (int64_t featureSetID,
                      const char* featureSetName,
                      double minResolution,
                      double maxResolution);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    FeatureDataStore (unsigned int modificationFlags,
                      unsigned int visibilityFlags);

  public:
      
    //
    // Adopts the supplied Feature as a member of this data store.  The Feature
    // will assume the supplied FeatureSet and Feature IDs.
    //
    static
    void
    adopt (Feature& feature,
           int64_t featureSetID,
           int64_t featureID)
        NOTHROWS;

    //
    // Adopts the supplied Feature as a member of this data store.  The Feature
    // will assume the supplied FeatureSet and Feature IDs and the supplied
    // version.
    //
    static
    void
    adopt (Feature& feature,
           int64_t featureSetID,
           int64_t featureID,
           unsigned long featureVersion)
        NOTHROWS;

    //
    // Adopts the supplied FeatureSet as a member of this data store.  The
    // FeatureSet will assume the supplied FeatureSet ID.
    //
    void
    adopt (FeatureSet& feature,
           int64_t featureSetID)
        const
        NOTHROWS;

    //
    // Adopts the supplied FeatureSet as a member of this data store.  The
    // FeatureSet will assume the supplied FeatureSet ID and version.
    //
    void
    adopt (FeatureSet& feature,
           int64_t featureSetID,
           unsigned long featureSetVersion)
        const
        NOTHROWS;

    //
    // Orphans the supplied Feature.  The Feature will have its FeatureSet and
    // Feature IDs set to ID_NONE.
    //
    static
    void
    orphan (Feature& feature)
        NOTHROWS;

    //
    // Orphans the supplied FeatureSet.  The FeatureSet will have its ID set to
    // ID_NONE.
    //
    static
    void
    orphan (FeatureSet& featureSet)
        NOTHROWS;


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    void
    checkModificationFlags (unsigned int requiredFlags)
        throw (std::domain_error);

    void
    checkVisibilityFlags (unsigned int requiredFlags)
        throw (std::domain_error);

    //
    // Derived classes may override the following vacuous implementations in
    // accordance with the modification and visibility flags they pass to the
    // protected constructor.
    //

    virtual
    void
    beginBulkModificationImpl ()
      { }

    virtual
    void
    deleteAllFeatureSetsImpl ()
      { }

    virtual
    void
    deleteAllFeaturesImpl (int64_t featureSetID)
      { }

    virtual
    void
    deleteFeatureImpl (int64_t featureID)
      { }

    virtual
    void
    deleteFeatureSetImpl (int64_t featureSetID)
      { }

    virtual
    void
    endBulkModificationImpl (bool successful)
      { }

    virtual
    Feature*
    insertFeatureImpl (int64_t featureSetID,
                       const char* name,                // Not NULL.
                       Geometry*,                       // Not NULL.
                       Style*,                          // May be NULL.
                       const util::AttributeSet&,
                       bool returnInstance)
      { return NULL; }

    virtual
    FeatureSet*
    insertFeatureSetImpl (const char* provider,         // Not NULL.
                          const char* type,             // Not NULL.
                          const char* name,             // Not NULL.
                          double minResolution,         // 0.0 means no min.
                          double maxResolution,         // 0.0 means no max.
                          bool returnInstance)
      { return NULL; }

    virtual
    void
    setFeatureSetVisibleImpl (int64_t featureSetID,
                              bool visible)
      { }

    virtual
    void
    setFeatureVisibleImpl (int64_t featureID,
                           bool visible)
      { }

    virtual
    void
    updateFeatureImpl (int64_t featureID,
                       const util::AttributeSet&)
      { }

    virtual
    void
    updateFeatureImpl (int64_t featureID,
                       const Geometry&)
      { }

    virtual
    void
    updateFeatureImpl (int64_t featureID,
                       const char* featureName)         // Not NULL.
      { }

    virtual
    void
    updateFeatureImpl (int64_t featureID,
                       const Style&)
      { }

    virtual
    void
    updateFeatureImpl (int64_t featureID,
                       const char* featureName,         // Not NULL.
                       const Geometry&,
                       const Style&,
                       const util::AttributeSet&)
      { }

    virtual
    void
    updateFeatureSetImpl (int64_t featureSetID,
                          const char* featureSetName)   // Not NULL.
      { }

    virtual
    void
    updateFeatureSetImpl (int64_t featureSetID,
                          double minResolution,         // 0.0 means no min.
                          double maxResolution)         // 0.0 means no max.
      { }

    virtual
    void
    updateFeatureSetImpl (int64_t featureSetID,
                          const char* featureSetName,   // Not NULL.
                          double minResolution,         // 0.0 means no min.
                          double maxResolution)         // 0.0 means no max.
      { }


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    unsigned int modificationFlags;
    unsigned int visibilityFlags;
  };


///=============================================================================
///
///  class atakmap::feature::FeatureDataStore::BulkTransaction
///
///     Concrete class for scope-based management of bulk modifications.  The
///     constructor checks whether a bulk modification is in effect.  If not, it
///     calls beginBulkModification on the supplied FeatureDataStore.  The
///     destructor calls endBulkModification if beginBulkModification was called
///     by the constructor.  The call to endBulkModification is false unless the
///     commit member function is called.
///
///=============================================================================


class FeatureDataStore::BulkTransaction
  : private TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    BulkTransaction (FeatureDataStore&);        // Begins bulk mod (or balks).

    ~BulkTransaction ()                 // Ends bulk mod unless ctor balked.
        NOTHROWS;

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    //
    // Causes the destructor to provide true to endBulkModification.
    //
    void
    commit ()
        NOTHROWS
      { success = true; }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    FeatureDataStore& dataStore;
    bool balked;
    bool success;
  };


///=============================================================================
///
///  class atakmap::feature::FeatureDataStore::FeatureCursor
///
///     Abstract base class for accessing Feature query results.
///
///=============================================================================


class ENGINE_API FeatureDataStore::FeatureCursor
  : public db::CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~FeatureCursor ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    //
    // Returns the Feature corresponding to the current row.
    //
    virtual
    Feature*
    get ()
        const
        throw (CursorError)
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    FeatureCursor (const std::shared_ptr<db::Cursor> &cursor)
        throw (CursorError)
      : CursorProxy (cursor)
      { }
  };


///=============================================================================
///
///  class atakmap::feature::FeatureDataStore::FeatureSetCursor
///
///     Abstract base class for accessing FeatureSet query results.
///
///=============================================================================


class ENGINE_API FeatureDataStore::FeatureSetCursor
  : public db::CursorProxy
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~FeatureSetCursor ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor and assignment operator are acceptable.
    //

    //
    // Returns a FeatureSet corresponding to the current row.
    //
    virtual
    FeatureSet*
    get ()
        const
        throw (CursorError)
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    FeatureSetCursor (std::unique_ptr<db::Cursor> &&cursor)
        throw (CursorError)
      : CursorProxy (std::move(cursor))
      { }
  };


///=============================================================================
///
///  struct atakmap::feature::FeatureDataStore::FeatureQueryParameters
///
///     Specifies the common criteria that Features may be queried against.
///
///     Supports the following DataStore::QueryParameters::SpatialFilter types:
///
///             PointFilter
///             RadiusFilter
///             RegionFilter
///
///     Supports the following DataStore::QueryParameters::Order types:
///
///             ID              sorts by Feature ID
///             Name            sorts by Feature name
///             Resolution      sorts by GSD of maximum resolution
///
///     Also supports the Order types defined below:
///
///             Distance        sorts by distance from a specified point
///             FeatureSetID    sorts by FeatureSet ID
///             Geometry        sorts by type of geometry (see below)
///
///=============================================================================


struct ENGINE_API FeatureDataStore::FeatureQueryParameters
  : db::DataStore::QueryParameters
  {
    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    struct Distance                     // Sorts by distance from GeoPoint.
      : Order
      {
        Distance (const core::GeoPoint& point)
          : point (point)
          { }

        ~Distance ()
            NOTHROWS
          { }

        bool
        isValid ()
            const
            NOTHROWS override
          { return point.isValid (); }

        core::GeoPoint point;
      };


    struct FeatureSetID                 // Sorts by FeatureSet ID.
      : Order
      {
        ~FeatureSetID ()
            NOTHROWS
          { }
      };


    //
    // Sorts results by Geometry type in the following order:
    //
    //   Points
    //   LineStrings
    //   Polygons
    //   GeometryCollections
    //
    struct GeometryType
      : Order
      {
        ~GeometryType ()
            NOTHROWS
          { }
      };


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    FeatureQueryParameters ()
      : visibleOnly (false)
      { }

    ~FeatureQueryParameters ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    bool
    isEmpty ()
        const
        NOTHROWS
      {
        return QueryParameters::isEmpty ()
            && featureSetIDs.empty ()
            && featureSetNames.empty ()
            && geometryTypes.empty ()
            && attributes.empty ()
            && !visibleOnly;
      }


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    //
    // Empty vectors indicate that all values are acceptable.
    //
    std::vector<int64_t> featureSetIDs;
    std::vector<TAK::Engine::Port::String> featureSetNames;
    std::vector<GeometryType> geometryTypes;
    std::map<TAK::Engine::Port::String, const void*, TAK::Engine::Port::StringLess> attributes;
    bool visibleOnly;
  };


///=============================================================================
///
///  struct atakmap::feature::FeatureDataStore::FeatureSetQueryParameters
///
///     Specifies the common criteria that FeatureSets may be queried against.
///
///     Does not support DataStore::QueryParameters::SpatialFilters.
///
///     Supports the following DataStore::QueryParameters::Order types:
///
///             ID              sorts by FeatureSet ID
///             Name            sorts by FeatureSet name
///             Provider        sorts by FeatureSet provider
///             Resolution      sorts by GSD of maximum resolution
///             Type            sorts by FeatureSet type
///
///=============================================================================


struct ENGINE_API FeatureDataStore::FeatureSetQueryParameters
  : db::DataStore::QueryParameters
  {
    FeatureSetQueryParameters ()
      : visibleOnly (false)
      { }

    ~FeatureSetQueryParameters ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    bool
    isEmpty ()
        const
        NOTHROWS
      { return QueryParameters::isEmpty () && !visibleOnly; }


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    //
    // Empty vectors indicate that all values are acceptable.
    //
    bool visibleOnly;
  };


}                                       // Close feature namespace.
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


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_FEATURE_DATA_STORE_H_INCLUDED
