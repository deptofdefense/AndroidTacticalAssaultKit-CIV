////============================================================================
////
////    FILE:           LocalRasterDataStore.h
////
////    DESCRIPTION:    A RasterDataStore on the local file system.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 18, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_LOCAL_RASTER_DATA_STORE_H_INCLUDED
#define ATAKMAP_RASTER_LOCAL_RASTER_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "port/Platform.h"
#include "port/String.h"
#include "raster/DatasetDescriptor.h"
#include "raster/RasterDataStore.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace db                            // Open db namespace.
{


class ENGINE_API FileCursor;


}                                       // Close db namspace.
namespace raster                        // Open raster namespace.
{


class ENGINE_API DatasetDescriptor;


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
///  class atakmap::raster::LocalRasterDataStore
///
///     A RasterDataStore on the local file system.  If mutable, it supports
///     add, remove, and update operations making use of
///     DatasetDescriptor::Factory.  Implementations are responsible for the
///     data structure managing the datasets.
///
///=============================================================================


class ENGINE_API LocalRasterDataStore
  : public RasterDataStore
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~LocalRasterDataStore ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //

    //
    // Adds the layers for the specified filePath to the data store.  A non-NULL
    // typeHint selects a registered Factory by type.  When NULL, any Factory
    // that is compatible with the supplied filePath will be used. Returns true
    // if layers for the file were added, false if no layers could be derived
    // from the file.
    //
    // Throws std::invalid_argument if filePath is NULL or the data store
    // already contains layers for the specified filePath.
    // Throws IO_Error.
    //
    bool
    addFile (const char* filePath,
             const char* typeHint = nullptr);

    bool
    addFile2 (const char* filePath,
              const char* typeHint = nullptr,
              DatasetDescriptor::CreationCallback *callback = nullptr);

    //
    // Begins a batch operation on the data store.  Dispatch of ContentListener
    // notifications will be deferred until the batch is signaled to be complete
    // via a call to endBatch.
    //
    void
    beginBatch ();

    //
    // Removes all layers from the data store.  Returns false if the data store
    // is not mutable.
    //
    bool
    clear ();

    //
    // Returns true if the layers for the supplied filePath are in the data
    // store.
    //
    // Throws std::invalid_argument if filePath is NULL.
    //
    bool
    containsFile (const char* filePath)
        const;

    //
    // Ends a batch operation on the data store.  Deferred ContentListener
    // notifications will occur.
    //
    void
    endBatch ();

    //
    // Returns the path to the file associated with the supplied descriptor in
    // the data store.  Returns NULL if the data store does not contain the
    // layer.
    //
    // Throws std::invalid_argument if the supplied DatasetDescriptor is invalid.
    //
    const char*
    getFile (const DatasetDescriptor&)
        const;

    //
    // Returns all of the files with content currently managed by the data store.
    //
    virtual
    db::FileCursor*
    queryFiles ()
        const
        = 0;

    //
    // Removes all layers derived from the specified filePath from the data
    // store.  Returns false if the data store is not mutable.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    bool
    removeFile (const char* filePath);

    //
    // Updates all layers derived from the specified filePath from the data
    // store.  Returns true if the data store was updated.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    // Throws util::IO_Error.
    //
    bool
    updateFile (const char* filePath);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED NESTED TYPES
    //==================================


    typedef DatasetDescriptor::DescriptorSet    DescriptorSet;


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    //
    // Creates a new instance.  The specified working directory will contain the
    // working directories for all layers created and managed by the data store.
    // Consistent with the general contract for DatasetDescriptor, any content
    // created in the working directory must be guaranteed to persist so long as
    // the associated DatasetDescriptor object persists.
    //
    LocalRasterDataStore (const char* workingDir);

    const char*
    getWorkingDir ()
        const
        NOTHROWS
      { return working_dir_; }


    //==================================
    //  util::DataStore INTERFACE
    //==================================


    void
    notifyContentListeners ()           // Balks when notifications are deferred.
        const;


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // Adds the supplied set of layers to the data store.  Returns true if the
    // layers were successfully added to the data store.  Called with the
    // RasterDataStore's mutex locked.
    //
    virtual
    bool
    addFileImpl (const char* filePath,          // Not NULL.
                 const DescriptorSet&,
                 const char* workingDir)
        = 0;

    bool
    addFileInternal (const char* filePath,      // Not NULL.
                     const char* typeHint,      // May be NULL
                     DatasetDescriptor::CreationCallback *callback);

    //
    // Removes all layers from the data store.  Returns true if the data store
    // contents changed.  Called with the RasterDataStore's mutex locked.
    //
    virtual
    bool
    clearImpl ()
        = 0;

    //
    // Returns true if the layers for the supplied filePath are in the data
    // store.  Called with the RasterDataStore's mutex locked.
    //
    virtual
    bool
    containsFileImpl (const char* filePath)     // Not NULL.
        const
        = 0;

    //
    // Returns the path to the file associated with the supplied descriptor in
    // the data store.  Returns NULL if the data store does not contain the
    // layer.  Called with the RasterDataStore's mutex locked.
    //
    virtual
    const char*
    getFileImpl (const DatasetDescriptor&)
        const
        = 0;

    //
    // Returns true if the data store is mutable.
    //
    virtual
    bool
    isMutable ()
        const
        NOTHROWS
        = 0;

    //
    // Removes the layers for the supplied filePath from the data store.
    // Returns true if the data store contents changed.  Called with the
    // RasterDataStore's mutex locked.
    //
    virtual
    bool
    removeFileImpl (const char* filePath)       // Not NULL.
        = 0;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    const TAK::Engine::Port::String working_dir_;
    bool defer_notifications_;
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

#endif  // #ifndef ATAKMAP_RASTER_LOCAL_RASTER_DATA_STORE_H_INCLUDED
