
//NOTE: This is a modified version of MutableFeatureDataStore.h intended to add temporary
//      support for storing AttributeSets until a more perminent solution arrives.


////============================================================================
////
////    FILE:           PersistentFeatureDataStore.h
////
////    DESCRIPTION:    Concrete class for a mutable feature data store.
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


#ifndef ATAKMAP_FEATURE_PERSISTENT_FEATURE_DATA_STORE_H_INCLUDED
#define ATAKMAP_FEATURE_PERSISTENT_FEATURE_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <memory>

#include "feature/FeatureDataStore.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
    namespace feature                       // Open feature namespace.
    {
        
        
        class AttributedFeatureDatabase;
        
        
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
        ///  class atakmap::feature::PersistentFeatureDataStore
        ///
        ///     Abstract base class for feature data stores.
        ///
        ///=============================================================================
        
        
        class PersistentFeatureDataStore
        : public FeatureDataStore
        {
            //====================================//
        public:                               //                      PUBLIC        //
            //====================================//
            
            
            //==================================
            //  PUBLIC NESTED TYPES
            //==================================
            
            
            //==================================
            //  PUBLIC INTERFACE
            //==================================
            
            
            PersistentFeatureDataStore (const char* filePath);
            
            ~PersistentFeatureDataStore ()
            throw ()
            { }
            
            //
            // The compiler is unable to generate a copy constructor or assignment
            // operator (due to a NonCopyable base class).  This is acceptable.
            //
            
            
            //==================================
            //  FeatureDataStore INTERFACE
            //==================================
            
            
            //
            // Returns the (possibly NULL) Feature with the supplied ID in the data
            // store.  The returned feature should be considered immutable; behavior is
            // undefined if the feature is actively modified.
            //
            Feature*
            getFeature (int64_t featureID);
            
            //
            // Returns the (possibly NULL) FeatureSet with the supplied ID in the data
            // store.  The returned FeatureSet should be considered immutable; behavior
            // is undefined if the FeatureSet is actively modified.
            //
            FeatureSet*
            getFeatureSet (int64_t featureSetID);
            
            const char*
            getURI ()
            const
            { return URI; }
            
            //
            // Returns true if the FeatureSet with supplied ID is visible.  A FeatureSet
            // is considered visible if one or more of its child features are visible.
            //
            bool
            isFeatureSetVisible (int64_t featureSetID)
            const;
            
            //
            // Returns true if the Feature with the supplied ID is visible.  If
            // visibility settings are not supported, this method should always return
            // true.  If visibility is only supported at the FeatureSet level, this
            // method should return true if the parent FeatureSet is visible.
            //
            bool
            isFeatureVisible (int64_t featureID)
            const;
            
            //
            // Returns true if the data store is currently in a bulk modification.
            //
            virtual
            bool
            isInBulkModification ()
            const;
            
            //
            // Returns a cursor over all Features satisfying the supplied
            // FeatureQueryParameters.  The returned Features should be considered
            // immutable; behavior is undefined if the Features are actively modified.
            //
            FeatureCursor*
            queryFeatures (const FeatureQueryParameters&)
            const;
            
            //
            // Returns the number of Features that satisfy the supplied
            // FeatureQueryParameters.
            //
            std::size_t
            queryFeaturesCount (const FeatureQueryParameters&)
            const;
            
            //
            // Returns a cursor over all FeatureSets satisfying the supplied
            // FeatureSetQueryParameters.  The returned FeatureSets should be considered
            // immutable; behavior is undefined if the FeatureSets are actively modified.
            //
            FeatureSetCursor*
            queryFeatureSets (const FeatureSetQueryParameters&)
            const;
            
            //
            // Returns the number of FeatureSets that satisfy the supplied
            // FeatureSetQueryParameters.
            //
            std::size_t
            queryFeatureSetsCount (const FeatureSetQueryParameters&)
            const;
            
            
            //==================================
            //  util::DataStore INTERFACE
            //==================================
            
            
            //
            //
            bool
            isAvailable ()
            const;
            
            //
            //
            void
            refresh ()
            { }
            
            
            //==================================
            //  util::Disposable INTERFACE
            //==================================
            
            
            //
            //
            void
            dispose ();
            
            
            //====================================//
        protected:                            //                      PROTECTED     //
            //====================================//
            
            
            //====================================//
        private:                              //                      PRIVATE       //
            //====================================//
            
            
            void
            notifyContentListeners ()           // Balks when in a transaction.
            const;
            
            
            //==================================
            //  FeatureDataStore INTERFACE
            //==================================
            
            
            void
            beginBulkModificationImpl ();
            
            void
            deleteAllFeatureSetsImpl ();
            
            void
            deleteAllFeaturesImpl (int64_t featureSetID);
            
            void
            deleteFeatureImpl (int64_t featureID);
            
            void
            deleteFeatureSetImpl (int64_t featureSetID);
            
            void
            endBulkModificationImpl (bool successful);
            
            Feature*
            insertFeatureImpl (int64_t featureSetID,
                               const char* name,                // Not NULL.
                               Geometry*,                       // Not NULL.
                               Style*,                          // May be NULL.
                               const util::AttributeSet&,
                               bool returnInstance);
            
            FeatureSet*
            insertFeatureSetImpl (const char* provider,         // Not NULL.
                                  const char* type,             // Not NULL.
                                  const char* name,             // Not NULL.
                                  double minResolution,         // 0.0 means no min.
                                  double maxResolution,         // 0.0 means no max.
                                  bool returnInstance);
            
            void
            setFeatureSetVisibleImpl (int64_t featureSetID,
                                      bool visible);
            
            void
            setFeatureVisibleImpl (int64_t featureID,
                                   bool visible);
            
            void
            updateFeatureImpl (int64_t featureID,
                               const util::AttributeSet&);
            
            void
            updateFeatureImpl (int64_t featureID,
                               const Geometry&);
            
            void
            updateFeatureImpl (int64_t featureID,
                               const char* featureName);        // Not NULL.
            
            void
            updateFeatureImpl (int64_t featureID,
                               const Style&);
            
            void
            updateFeatureImpl (int64_t featureID,
                               const char* featureName,         // Not NULL.
                               const Geometry&,
                               const Style&,
                               const util::AttributeSet&);
            
            void
            updateFeatureSetImpl (int64_t featureSetID,
                                  const char* featureSetName);  // Not NULL.
            
            void
            updateFeatureSetImpl (int64_t featureSetID,
                                  double minResolution,         // 0.0 means no min.
                                  double maxResolution);        // 0.0 means no max.
            
            void
            updateFeatureSetImpl (int64_t featureSetID,
                                  const char* featureSetName,   // Not NULL.
                                  double minResolution,         // 0.0 means no min.
                                  double maxResolution);        // 0.0 means no max.
            
            
            //==================================
            //  PRIVATE REPRESENTATION
            //==================================
            
            
            PGSC::String URI;
            std::auto_ptr<AttributedFeatureDatabase> featureDB;
            bool inTransaction;
            mutable bool contentChanged;
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

#endif  // #ifndef ATAKMAP_FEATURE_PERSISTENT_FEATURE_DATA_STORE_H_INCLUDED

