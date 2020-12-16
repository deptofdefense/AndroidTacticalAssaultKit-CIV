////============================================================================
////
////    FILE:           DataSourceFeatureDataStore.cpp
////
////    DESCRIPTION:    Implementation of abstract base class for managing
////                    FeatureSets parsed from files.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/DataSourceFeatureDataStore.h"

#include <memory>
#include <stdexcept>

#include "feature/FeatureSet.h"
#include "thread/Lock.h"


#define MEM_FN( fn )    "atakmap::feature::DataSourceFeatureDataStore::" fn ": "


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


bool
DataSourceFeatureDataStore::addFile (const char* filePath,
                                     const char* providerHint)
  {
    Lock lock(getMutex());

    return addFileInternal (filePath, providerHint, true);
  }


bool
DataSourceFeatureDataStore::containsFile (const char* filePath)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("containsFile")
                                     "Received NULL filePath");
      }

    Lock lock(getMutex());

    return containsFileImpl (filePath);
  }


const char*
DataSourceFeatureDataStore::getFile (const FeatureSet& featureSet)
    const
  {
    Lock lock(getMutex());

    return getFileInternal (featureSet.getID ());
  }


void
DataSourceFeatureDataStore::removeFile (const char* filePath)
  {
    Lock lock(getMutex());

    removeFileInternal (filePath, true);
  }


void
DataSourceFeatureDataStore::removeFiles ()
  {
    Lock lock(getMutex());
    bool filesRemoved (false);
    std::unique_ptr<db::FileCursor> cursor (queryFiles ());

    while (cursor->moveToNext ())
      {
        removeFileImpl (cursor->getFile ());
        filesRemoved = true;
      }
    if (filesRemoved)
      {
        notifyContentListeners ();
      }
  }


bool
DataSourceFeatureDataStore::updateFile (const char* filePath)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("updateFile")
                                     "Received NULL filePath");
      }

    Lock lock(getMutex());

    return containsFileImpl (filePath)
        ? updateFileInternal (filePath)
        : false;
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

bool
DataSourceFeatureDataStore::updateFileInternal(const char* filePath)
{
    removeFileInternal(filePath, false);
    return addFileInternal(filePath, nullptr, true);
}

bool
DataSourceFeatureDataStore::addFileInternal (const char* filePath,
                                             const char* providerHint,
                                             bool notify)
  {
    bool success (false);               // Begin with all due optimism.
    std::unique_ptr<FeatureDataSource::Content> content
        (FeatureDataSource::parse (filePath, providerHint));

    if (content.get ())
      {
        if (containsFileImpl (filePath))
          {
            throw std::invalid_argument (MEM_FN ("addFileInternal")
                                         "An entry for the supplied filePath "
                                         "already exists");
          }
        addFileImpl (filePath, *content);
        if (notify)
          {
            notifyContentListeners ();
          }
        success = true;
      }

    return success;
  }


void
DataSourceFeatureDataStore::removeFileInternal (const char* filePath,
                                                bool notify)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("removeFileInternal")
                                     "Received NULL filePath");
      }

    if (containsFileImpl (filePath))
      {
        removeFileImpl (filePath);
        if (notify)
          {
            notifyContentListeners ();
          }
      }
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
