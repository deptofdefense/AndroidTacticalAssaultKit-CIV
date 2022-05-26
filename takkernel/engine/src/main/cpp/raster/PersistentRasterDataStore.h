////============================================================================
////
////    FILE:           PersistentRasterDataStore.h
////
////    DESCRIPTION:    A LocalDataStore that persists across application
////                    invocations.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      May 1, 2015   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_PERSISTENT_RASTER_DATA_STORE_H_INCLUDED
#define ATAKMAP_RASTER_PERSISTENT_RASTER_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <list>
#include <map>
#include <memory>
#include <stdint.h>

#include "feature/GeometryCollection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "raster/LocalRasterDataStore.h"
#include "thread/Thread.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


class ENGINE_API LayerDatabase;


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
///  class atakmap::raster::PersistentRasterDataStore
///
///     The content of the working directory supplied to the constructor must be
///     guaranteed to remain unmodified by the application for the lifetime of
///     the database file supplied to the constructor.  The application is free
///     to delete or modify the working directory once the database file is
///     deleted.
///
///=============================================================================


class ENGINE_API PersistentRasterDataStore
  : public LocalRasterDataStore
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Creates a new instance.  The (possibly NULL) supplied working directory
    // will contain the working directories for all layers created and managed
    // by the data store.
    //
    // Creates a temporary database if the supplied databasePath is NULL.
    //
    // Throws std::runtime_error if a LayersDatabase could not be created.
    //
    PersistentRasterDataStore (const char* databasePath,
                               const char* workingDir);

    ~PersistentRasterDataStore()
        NOTHROWS;

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //


    //==================================
    //  LocalRasterDataStore INTERFACE
    //==================================


    //
    // Returns all of the files with content currently managed by the data store.
    //
    db::FileCursor*
    queryFiles ()
        const;


    //==================================
    //  RasterDataStore INTERFACE
    //==================================


    //
    // Returns the coverage for the specified dataset and imagery type
    // combination.
    //
    // If dataset is non-NULL and imageryType is non-NULL, the returned Geometry
    // will be the coverage for that imagery type for the specified dataset.
    //
    // If dataset is non-NULL and imageryType is NULL, the returned Geometry
    // will be the union of the coverage for all imagery types for that dataset.
    //
    // If dataset is NULL and imageryType is non-NULL, the returned Geometry
    // will be the union of the coverage for that imagery type across all
    // datasets.
    //
    // Throws std::invalid_argument if both dataset and imageryType are NULL.
    //
    feature::UniqueGeometryPtr
    getCoverage (const char* dataset,   // May be NULL (if imageryType is not).
                 const char* imageryType); // May be NULL (if dataset is not).

    //
    // Returns the names of datasets available in the data store.
    //
    atakmap::port::Iterator<const char*> *
    getDatasetNames ()
        const;

    //
    // Returns the data types available across all datasets in the data store.
    //
    atakmap::port::Iterator<const char*> *
    getDatasetTypes ()
        const;

    //
    // Returns the imagery types available across all datasets in the data store.
    //
    atakmap::port::Iterator<const char*> *
    getImageryTypes ()
        const;

    double
    getMinimumResolution(const char* dataset,   // May be NULL (if type is non-NULL).
                         const char* type);     // May be NULL (if dataset is non-NULL).

    double
    getMaximumResolution(const char* dataset,   // May be NULL (if type is non-NULL).
                         const char* type);     // May be NULL (if dataset is non-NULL).

    //
    // Returns the providers available across all datasets in the data store.
    //
    atakmap::port::Iterator<const char*> *
    getProviders ()
        const;

    //
    // Queries the data store for all datasets matching the specified
    // DatasetQueryParameters.
    //
    DatasetDescriptorCursor*
    queryDatasets (const DatasetQueryParameters&)
        const;

    //
    // Returns the number of results for the specified query.
    //
    std::size_t
    queryDatasetsCount (const DatasetQueryParameters&)
        const;

    void
    destroyStringIterator(atakmap::port::Iterator<const char*> *it)
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
    //  PRIVATE NESTED TYPES
    //==================================

    struct StringPairLess {
        int operator()(const std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String> &a, const std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String> &b) const
          {
            return (a < b) ? 1 : 0;
          }
        
        /*int operator()(const std::pair<PGSC::String, PGSC::String> &a, const char *b) const
        {
            return -1;//TODO--(a < b) ? 1 : 0;
        }*/
    };

    typedef std::map<int64_t, std::shared_ptr<DatasetDescriptor>>
            DescriptorMap;

    struct QueryInfoSpec
      {
        double minResolution;
        double maxResolution;
        const atakmap::feature::Geometry *coverage;
        uint32_t count;
      };

    class BackgroundCoverageResolver 
    {
    public :
        BackgroundCoverageResolver(PersistentRasterDataStore &owner, const char *dataset, const char *type, const atakmap::feature::GeometryCollection &geom);
        ~BackgroundCoverageResolver();
    public :
        bool isCanceled();
        void cancel();
        void start();
    private:
        void run();
        static void *threadFn(void *opaque);
    public :
        const TAK::Engine::Port::String dataset;
        const TAK::Engine::Port::String type;
        TAK::Engine::Thread::ThreadPtr worker;
        bool done;
    private :
        PersistentRasterDataStore &owner;
        const atakmap::feature::GeometryCollection geom;
    private :
        bool canceled;
    };

    typedef std::map<std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String>, BackgroundCoverageResolver *, StringPairLess>
            PendingCoveragesMap;
    typedef std::map<std::pair<TAK::Engine::Port::String, TAK::Engine::Port::String>, QueryInfoSpec, StringPairLess>
            InfoCacheMap;

    //==================================
    //  LocalRasterDataStore IMPLEMENTATION
    //==================================


    //
    // Adds the supplied set of layers to the data store.  Returns true if the
    // layers were successfully added to the data store.
    //
    bool
    addFileImpl (const char* filePath,          // Not NULL.
                 const DescriptorSet&,
                 const char* workingDir);

    //
    // Removes all layers from the data store.  Returns true if the data store
    // contents changed.
    //
    bool
    clearImpl ();

    static void
    clearInfoCacheMap(InfoCacheMap &map);

    void
    clearPendingCoveragesMap();

    //
    // Returns true if the layers for the supplied filePath are in the data
    // store.
    //
    bool
    containsFileImpl (const char* filePath)     // Not NULL.
        const;

    void
    geometryResolvedNoSync(const char *dataset, const char *type, const atakmap::feature::Geometry &coverage, bool notify);

    //
    // Returns the path to the file associated with the supplied descriptor in
    // the data store.  Returns NULL if the data store does not contain the
    // layer.
    //
    const char*
    getFileImpl (const DatasetDescriptor&)
        const;

    //
    // Invalidates the info cache entries that may have represented the specified
    // datasets
    //

    void
    invalidateCacheInfo(const DescriptorSet &, const bool notify)
        NOTHROWS;

    //
    // Returns true if the data store is mutable.
    //
    bool
    isMutable ()
        const
        NOTHROWS
      { return true; }

    //
    // Removes the layers for the supplied filePath from the data store.
    // Returns true if the data store contents changed.
    //
    bool
    removeFileImpl (const char* filePath);      // Not NULL.

    QueryInfoSpec
    validateQueryInfoSpecNoSync(const char *dataset, const char *imageryType);

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    TAK::Engine::Port::String databasePath;
    std::auto_ptr<LayerDatabase> layerDB;
    mutable DescriptorMap layerDescriptors;
    mutable TAK::Engine::Thread::Mutex descriptorsMutex;

    PendingCoveragesMap pendingCoverages;
    InfoCacheMap infoCache;

    std::list<BackgroundCoverageResolver *> canceledResolvers;

//#ifdef MSVC
    std::set<std::string> availableMounts;
//#endif

    friend class BackgroundCoverageResolver;
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

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_RASTER_PERSISTENT_RASTER_DATA_STORE_H_INCLUDED
