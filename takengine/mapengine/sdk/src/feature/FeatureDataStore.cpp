////============================================================================
////
////    FILE:           FeatureDataStore.cpp
////
////    DESCRIPTION:    Implementation of base class for FeatureDataStore
////                    concrete implementations.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 21, 2015  scott           Created.
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


#include "feature/FeatureDataStore.h"

#include <stdexcept>

#include "feature/Feature.h"
#include "feature/FeatureSet.h"
#include "feature/Geometry.h"
#include "feature/Style.h"

#define MEM_FN( fn )    "atakmap::core::FeatureDataStore::" fn ": "


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


FeatureDataStore::FeatureDataStore(unsigned int modificationFlags,
        unsigned int visibilityFlags)
    : modificationFlags(modificationFlags),
    visibilityFlags(visibilityFlags)
{ }


void
FeatureDataStore::adopt(Feature& feature,
    int64_t featureSetID,
    int64_t featureID)
    NOTHROWS
{
    adopt(feature, featureSetID, featureID, VERSION_NONE);
}


void
FeatureDataStore::adopt(FeatureSet& featureSet,
    int64_t featureSetID)
const
    NOTHROWS
{
    adopt(featureSet, featureSetID, VERSION_NONE);
}

void
FeatureDataStore::beginBulkModification()
{
    checkModificationFlags(MODIFY_BULK_MODIFICATIONS);
    beginBulkModificationImpl();
}


void
FeatureDataStore::deleteAllFeatures(int64_t featureSetID)
{
    checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);
    deleteAllFeaturesImpl(featureSetID);
}


void
FeatureDataStore::deleteAllFeatureSets()
{
    checkModificationFlags(MODIFY_FEATURESET_DELETE);
    deleteAllFeatureSetsImpl();
}


void
FeatureDataStore::deleteFeature(int64_t featureID)
{
    checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);
    deleteFeatureImpl(featureID);
}


void
FeatureDataStore::deleteFeatureSet(int64_t featureSetID)
{
    checkModificationFlags(MODIFY_FEATURESET_DELETE);
    deleteFeatureSetImpl(featureSetID);
}


FeatureDataStore::FeatureCursor*
FeatureDataStore::queryFeatures()
    const
{
    return queryFeatures(FeatureQueryParameters());
}


std::size_t
FeatureDataStore::queryFeaturesCount()
    const
{
    return queryFeaturesCount(FeatureQueryParameters());
}


FeatureDataStore::FeatureSetCursor*
FeatureDataStore::queryFeatureSets()
    const
{
    return queryFeatureSets(FeatureSetQueryParameters());
}


std::size_t
FeatureDataStore::queryFeatureSetsCount()
    const
{
    return queryFeatureSetsCount(FeatureSetQueryParameters());
}


void
FeatureDataStore::setFeatureSetVisible(int64_t featureSetID,
    bool visible)
{
    checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURESET);
    setFeatureSetVisibleImpl(featureSetID, visible);
}


void
FeatureDataStore::setFeatureVisible(int64_t featureID,
    bool visible)
{
    checkVisibilityFlags(VISIBILITY_SETTINGS_FEATURE);
    setFeatureVisibleImpl(featureID, visible);
}


void
FeatureDataStore::updateFeature(int64_t featureID,
    const util::AttributeSet& attributes)
{
    checkModificationFlags(MODIFY_FEATURE_ATTRIBUTES);
    updateFeatureImpl(featureID, attributes);
}


void
FeatureDataStore::updateFeature(int64_t featureID,
    const Geometry& geometry)
{
    checkModificationFlags(MODIFY_FEATURE_GEOMETRY);
    updateFeatureImpl(featureID, geometry);
}


void
FeatureDataStore::updateFeature(int64_t featureID,
    const Style& style)
{
    checkModificationFlags(MODIFY_FEATURE_STYLE);
    updateFeatureImpl(featureID, style);
}


FeatureDataStore::BulkTransaction::BulkTransaction(FeatureDataStore& dataStore)
    : dataStore(dataStore),
    balked(dataStore.isInBulkModification()),
    success(false)
{
    if (!balked)
    {
        dataStore.beginBulkModification();
    }
}


FeatureDataStore::BulkTransaction::~BulkTransaction()
    NOTHROWS
{
    if (!balked)
    {
        try
        {
            dataStore.endBulkModification(success);
        }
        catch (...)
        {
        }
    }
}

void
FeatureDataStore::endBulkModification (bool successful)
  {
    checkModificationFlags (MODIFY_BULK_MODIFICATIONS);
    if (!isInBulkModification ())
      {
        throw std::logic_error (MEM_FN ("endBulkModification")
                                "No bulk modification in effect");
      }
    endBulkModificationImpl (successful);
  }


Feature*
FeatureDataStore::insertFeature (int64_t featureSetID,
                                 const char* featureName,
                                 Geometry* geometry,
                                 Style* style,
                                 const util::AttributeSet& attributes,
                                 bool returnInstance)
  {
    checkModificationFlags (MODIFY_FEATURESET_FEATURE_INSERT);
    if (!featureName)
      {
        throw std::invalid_argument (MEM_FN ("insertFeature")
                                     "Received NULL Feature name");
      }
    if (!geometry)
      {
        throw std::invalid_argument (MEM_FN ("insertFeature")
                                     "Received NULL Geometry");
      }

    return insertFeatureImpl (featureSetID, featureName, geometry, style,
                              attributes, returnInstance);
  }


FeatureSet*
FeatureDataStore::insertFeatureSet (const char* provider,
                                    const char* type,
                                    const char* featureSetName,
                                    double minResolution,
                                    double maxResolution,
                                    bool returnInstance)
  {
    checkModificationFlags (MODIFY_FEATURESET_INSERT);
    if (!provider)
      {
        throw std::invalid_argument (MEM_FN ("insertFeatureSet")
                                     "Received NULL provider");
      }
    if (!type)
      {
        throw std::invalid_argument (MEM_FN ("insertFeatureSet")
                                     "Received NULL type");
      }
    if (!featureSetName)
      {
        throw std::invalid_argument (MEM_FN ("insertFeatureSet")
                                     "Received NULL FeatureSet name");
      }
    if (minResolution < 0.0)
      {
        throw std::invalid_argument (MEM_FN ("insertFeatureSet")
                                     "Received negative minimum resolution");
      }
    if (maxResolution < 0.0)
      {
        throw std::invalid_argument (MEM_FN ("insertFeatureSet")
                                     "Received negative maximum resolution");
      }

    return insertFeatureSetImpl (provider, type, featureSetName,
                                 minResolution, maxResolution,
                                 returnInstance);
  }


void
FeatureDataStore::updateFeature (int64_t featureID,
                                 const char* featureName)
  {
    checkModificationFlags (MODIFY_FEATURE_NAME);
    if (!featureName)
      {
        throw std::invalid_argument (MEM_FN ("updateFeature")
                                     "Received NULL Feature name");
      }
    updateFeatureImpl (featureID, featureName);
  }


void
FeatureDataStore::updateFeature (int64_t featureID,
                                 const char* featureName,
                                 const Geometry& geometry,
                                 const Style& style,
                                 const util::AttributeSet& attributes)
  {
    checkModificationFlags (MODIFY_FEATURE_NAME
                            | MODIFY_FEATURE_GEOMETRY
                            | MODIFY_FEATURE_STYLE
                            | MODIFY_FEATURE_ATTRIBUTES);
    if (!featureName)
      {
        throw std::invalid_argument (MEM_FN ("updateFeature")
                                     "Received NULL Feature name");
      }
     updateFeatureImpl(featureID, featureName, geometry, style, attributes);
  }


void
FeatureDataStore::updateFeatureSet (int64_t featureSetID,
                                    const char* featureSetName)
  {
    checkModificationFlags (MODIFY_FEATURESET_UPDATE | MODIFY_FEATURESET_NAME);
    if (!featureSetName)
      {
        throw std::invalid_argument (MEM_FN ("updateFeatureSet")
                                     "Received NULL FeatureSet name");
      }
    updateFeatureSetImpl (featureSetID, featureSetName);
  }


void
FeatureDataStore::updateFeatureSet (int64_t featureSetID,
                                    double minResolution,
                                    double maxResolution)
  {
    checkModificationFlags (MODIFY_FEATURESET_UPDATE
                            | MODIFY_FEATURESET_DISPLAY_THRESHOLDS);
    if (minResolution < 0.0)
      {
        throw std::invalid_argument (MEM_FN ("updateFeatureSet")
                                     "Received negative minimum resolution");
      }
    if (maxResolution < 0.0)
      {
        throw std::invalid_argument (MEM_FN ("updateFeatureSet")
                                     "Received negative maximum resolution");
      }

    updateFeatureSetImpl (featureSetID, minResolution, maxResolution);
  }


void
FeatureDataStore::updateFeatureSet (int64_t featureSetID,
                                    const char* featureSetName,
                                    double minResolution,
                                    double maxResolution)
  {
    checkModificationFlags (MODIFY_FEATURESET_UPDATE
                            | MODIFY_FEATURESET_NAME
                            | MODIFY_FEATURESET_DISPLAY_THRESHOLDS);
    if (!featureSetName)
      {
        throw std::invalid_argument (MEM_FN ("updateFeatureSet")
                                     "Received NULL FeatureSet name");
      }
    if (minResolution < 0.0)
      {
        throw std::invalid_argument (MEM_FN ("updateFeatureSet")
                                     "Received negative minimum resolution");
      }
    if (maxResolution < 0.0)
      {
        throw std::invalid_argument (MEM_FN ("updateFeatureSet")
                                     "Received negative maximum resolution");
      }

    updateFeatureSetImpl (featureSetID, featureSetName,
                          minResolution, maxResolution);
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


void
FeatureDataStore::adopt (Feature& feature,
                         int64_t featureSetID,
                         int64_t featureID,
                         unsigned long featureVersion)
    NOTHROWS
  {
    feature.setID = featureSetID;
    feature.ID = featureID;
    feature.version = featureVersion;
  }


void
FeatureDataStore::adopt (FeatureSet& featureSet,
                         int64_t featureSetID,
                         unsigned long featureSetVersion)
    const
    NOTHROWS
  {
    featureSet.ID = featureSetID;
    featureSet.owner = this;
    featureSet.version = featureSetVersion;
  }


void
FeatureDataStore::orphan (Feature& feature)
    NOTHROWS
  {
    feature.setID = ID_NONE;
    feature.ID = ID_NONE;
  }


void
FeatureDataStore::orphan (FeatureSet& featureSet)
    NOTHROWS
  {
    featureSet.ID = ID_NONE;
    featureSet.owner = nullptr;
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


void
FeatureDataStore::checkModificationFlags (unsigned int requiredFlags)
    throw (std::domain_error)
  {
    if ((modificationFlags & requiredFlags) != requiredFlags)
      {
        throw std::domain_error (MEM_FN ("checkModificationFlags")
                                 "Required modification flag not set");
      }
  }


void
FeatureDataStore::checkVisibilityFlags (unsigned int requiredFlags)
    throw (std::domain_error)
  {
    if ((visibilityFlags & requiredFlags) != requiredFlags)
      {
        throw std::domain_error (MEM_FN ("checkVisibilityFlags")
                                 "Required visibility flag not set");
      }
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.

