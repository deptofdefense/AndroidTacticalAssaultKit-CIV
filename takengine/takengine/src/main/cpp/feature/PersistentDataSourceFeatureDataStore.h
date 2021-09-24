////============================================================================
////
////    FILE:           PersistentDataSourceFeatureDataStore.h
////
////    DESCRIPTION:    TODO description.
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


#ifndef ATAKMAP_FEATURE_PERSISTENT_DATA_SOURCE_FEATURE_DATA_STORE_H_INCLUDED
#define ATAKMAP_FEATURE_PERSISTENT_DATA_SOURCE_FEATURE_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <memory>

#include "feature/DataSourceFeatureDataStore.h"
#include "feature/FeatureDataStore.h"
#include "port/String.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


class FeatureCatalogDatabase;


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
///  class atakmap::feature::PersistentDataSourceFeatureDataStore
///
///     TODO class description
///
///=============================================================================


class PersistentDataSourceFeatureDataStore
  : public FeatureDataStore,
    public DataSourceFeatureDataStore
    {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Creates a temporary database if the supplied filePath is NULL.
    //
    // Throws std::runtime_error if a FeatureCatalogDatabase could not be
    // created.
    //
    PersistentDataSourceFeatureDataStore (const char* filePath);

    ~PersistentDataSourceFeatureDataStore ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    FeatureCatalogDatabase&
    getFeatureDatabase ()
      { return *featureDB; }


    //==================================
    //  DataSourceFeatureDataStore INTERFACE
    //==================================


    db::FileCursor*
    queryFiles ()
        const;


    //==================================
    //  FeatureDataStore INTERFACE
    //==================================


    Feature*
    getFeature (int64_t featureID);

    FeatureSet*
    getFeatureSet (int64_t featureSetID);

    const char*
    getURI ()
        const
      { return URI; }

    bool
    isFeatureSetVisible (int64_t featureSetID)
        const;

    bool
    isFeatureVisible (int64_t featureID)
        const;

    bool
    isInBulkModification ()
        const
      { return inTransaction; }

    FeatureCursor*
    queryFeatures (const FeatureQueryParameters&)
        const;

    std::size_t
    queryFeaturesCount (const FeatureQueryParameters&)
        const;

    FeatureSetCursor*
    queryFeatureSets (const FeatureSetQueryParameters&)
        const;

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
    refresh ();


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


    //==================================
    //  DataSourceFeatureDataStore INTERFACE
    //==================================


    bool
    addFileImpl (const char* filePath,                  // Not NULL.
                 FeatureDataSource::Content&);

    bool
    containsFileImpl (const char* filePath)             // Not NULL.
        const;

    const char*
    getFileImpl (int64_t featureSetID)
        const;

    TAK::Engine::Thread::Mutex&
    getMutex ()
        const
        NOTHROWS
      { return FeatureDataStore::getMutex (); }

    void
    notifyContentListeners ()
        const
      { FeatureDataStore::notifyContentListeners (); }

    void
    removeFileImpl (const char* filePath);


    //==================================
    //  FeatureDataStore INTERFACE
    //==================================


    void
    beginBulkModificationImpl ();

    void
    deleteAllFeatureSetsImpl ();

    void
    deleteFeatureSetImpl (int64_t featureSetID);

    void
    endBulkModificationImpl (bool successful);

    void
    setFeatureSetVisibleImpl (int64_t featureSetID,
                              bool visible);

    void
    setFeatureVisibleImpl (int64_t featureID,
                           bool visible);



    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    TAK::Engine::Port::String URI;
    std::auto_ptr<FeatureCatalogDatabase> featureDB;
    bool inTransaction;
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


#endif  // #ifndef ATAKMAP_FEATURE_PERSISTENT_DATA_SOURCE_FEATURE_DATA_STORE_H_INCLUDED
