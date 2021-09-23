////============================================================================
////
////    FILE:           DataSourceFeatureDataStore.h
////
////    DESCRIPTION:    Abstract base class for managing FeatureSets parsed from
////                    files.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 22, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_DATA_SOURCE_FEATURE_DATA_STORE_H_INCLUDED
#define ATAKMAP_FEATURE_DATA_SOURCE_FEATURE_DATA_STORE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <stdint.h>

#include "feature/FeatureDataSource.h"
#include "port/Platform.h"
#include "thread/Mutex.h"


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
namespace feature                       // Open feature namespace.
{


class ENGINE_API FeatureSet;


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
///  class atakmap::feature::DataSourceFeatureDataStore
///
///     TODO class description
///
///=============================================================================


class ENGINE_API DataSourceFeatureDataStore
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~DataSourceFeatureDataStore ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //

    //
    // Adds a FeatureSet for the supplied file path to the data store.  The
    // optional providerHint is the name of a provider to be used to parse the
    // Features.  If providerHint is NULL, any compatible provider will be be
    // used.  Returns true if Features for the supplied filePath were added.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    // Throws util::IO_Error on any IO-related errors.
    //
    bool
    addFile (const char* filePath,
             const char* providerHint);

    //
    // Returns true if a FeatureSet for the supplied filePath is in the data
    // store.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    bool
    containsFile (const char* filePath)
        const;

    //
    // Returns the (possibly NULL) path to the file associated with the supplied
    // FeatureSet in the data store.
    //
    const char*
    getFile (const FeatureSet&)
        const;

    virtual
    db::FileCursor*
    queryFiles ()
        const
        = 0;

    //
    // Removes the FeatureSet derived from the supplied file from the data store.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    void
    removeFile (const char* filePath);

    //
    // Removes the FeatureSets derived from all files that have been added to
    // the data store.  This is equivalent to calling removeFile on each file
    // returned by queryFiles.
    //
    void
    removeFiles ();

    //
    // Updates the FeatureSet from the supplied file path.  Returns true if the
    // Features were successfully updated.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    // Throws util::IO_Error on any IO-related errors.
    //
    bool
    updateFile (const char* filePath);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //
    // Adds the FeatureSet for the supplied file path to the data store.  The
    // optional providerHint is the name of a provider to be used to parse the
    // Features.  If providerHint is NULL, any compatible provider will be be
    // used.  Returns true if the Features for the supplied filePath were added.
    //
    // This member function must be called with the mutex locked.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    // Throws util::IO_Error on any IO-related errors.
    //
    bool
    addFileInternal (const char* filePath,
                     const char* providerHint,
                     bool notify);

    //
    // Returns the (possibly NULL) path to the file associated with the supplied
    // FeatureSet ID in the data store.
    //
    // This member function must be called with the mutex locked.
    //
    const char*
    getFileInternal (int64_t featureSetID)
        const
      { return getFileImpl (featureSetID); }

    //
    // Removes the FeatureSet derived from the supplied file from the data store.
    //
    // This member function must be called with the mutex locked.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    void
    removeFileInternal (const char* filePath,
                        bool notify);

    //
    // Updates the FeatureSet from the supplied file path.  Returns true if the
    // Features were successfully updated.
    //
    // This member function must be called with the mutex locked.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    // Throws util::IO_Error on any IO-related errors.
    //
    bool
    updateFileInternal (const char* filePath);


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // Adds the parsed Content to the underlying data store.  Returns true if
    // the content was added to the data store.
    //
    // This member function is called with the mutex locked.
    //
    // Throws util::IO_Error on any IO-related errors.
    //
    virtual
    bool
    addFileImpl (const char* filePath,                  // Not NULL.
                 FeatureDataSource::Content&)
        = 0;

    //
    // Returns true if a FeatureSet for the supplied filePath is in the data
    // store.
    //
    // This member function is called with the mutex locked.
    //
    virtual
    bool
    containsFileImpl (const char* filePath)             // Not NULL.
        const
        = 0;

    //
    // Returns the (possibly NULL) path to the file associated with the supplied
    // FeatureSet ID in the data store.
    //
    // This member function is called with the mutex locked.
    //
    virtual
    const char*
    getFileImpl (int64_t featureSetID)
        const
        = 0;

    //
    // Returns the Mutex for synchronizing data store operations.
    //
    virtual
    TAK::Engine::Thread::Mutex&
    getMutex ()
        const
        NOTHROWS
        = 0;

    //
    // Notifies ContentListeners of the associated data store of changes.
    //
    virtual
    void
    notifyContentListeners ()
        const
        = 0;

    //
    // Removes the FeatureSet derived from the supplied file from the data store.
    //
    // This member function is called with the mutex locked.
    //
    virtual
    void
    removeFileImpl (const char* filePath)               // Not NULL.
        = 0;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

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


#endif  // #ifndef ATAKMAP_FEATURE_DATA_SOURCE_FEATURE_DATA_STORE_H_INCLUDED
