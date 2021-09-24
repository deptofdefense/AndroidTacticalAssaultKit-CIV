////============================================================================
////
////    FILE:           LocalRasterDataStore.cpp
////
////    DESCRIPTION:    Implementation of LocalRasterDataStore class.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/LocalRasterDataStore.h"

#include "port/String.h"
#include "thread/Lock.h"
#include "util/IO.h"
#include "util/Memory.h"


#define MEM_FN( fn )    "atakmap::raster::LocalRasterDataStore::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


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
namespace raster                        // Open raster namespace.
{

bool
LocalRasterDataStore::addFile (const char* filePath,
                               const char* typeHint)
{
    return addFile2(filePath, typeHint);
}

bool
LocalRasterDataStore::addFile2 (const char* filePath,
                                const char* typeHint,
                                DatasetDescriptor::CreationCallback *callback)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("addFile")
                                     "Received NULL filePath");
      }

    return isMutable () && addFileInternal (filePath, typeHint, callback);
  }


void
LocalRasterDataStore::beginBatch ()
  {
      Lock lock(getMutex());

    defer_notifications_ = true;
  }


bool
LocalRasterDataStore::clear ()
  {
    bool clearable (isMutable ());

    if (clearable)
      {
        Lock lock(getMutex());

        clearImpl ();
        notifyContentListeners ();
      }

    return clearable;
  }


bool
LocalRasterDataStore::containsFile (const char* filePath)
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


void
LocalRasterDataStore::endBatch ()
  {
    Lock lock(getMutex());

    defer_notifications_ = false;
    notifyContentListeners ();
  }


const char*
LocalRasterDataStore::getFile (const DatasetDescriptor& descriptor)
    const
  {
    Lock lock(getMutex());

    return getFileImpl (descriptor);
  }


bool
LocalRasterDataStore::removeFile (const char* filePath)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("removeFile")
                                     "Received NULL filePath");
      }

    bool removable (isMutable ());

    if (removable)
      {
        Lock lock(getMutex());

        if (containsFileImpl (filePath))
          {
            removeFileImpl (filePath);
            notifyContentListeners ();
          }
      }

    return removable;
  }


bool
LocalRasterDataStore::updateFile (const char* filePath)
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("updateFile")
                                     "Received NULL filePath");
      }

    bool success (isMutable ());

    if (success)
      {
        Lock lock(getMutex());

        if (containsFileImpl (filePath))
          {
            success = removeFileImpl (filePath) && addFileInternal (filePath, nullptr, nullptr);
            notifyContentListeners ();
          }
      }

    return success;
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{

LocalRasterDataStore::LocalRasterDataStore (const char* workingDir)
  : working_dir_ (workingDir),
    defer_notifications_ (false)
  { }

void
LocalRasterDataStore::notifyContentListeners ()
    const
  {
    //
    // Balk if notifications are deferred.
    //

    Lock lock(getMutex());

    if (!defer_notifications_)
      {
        RasterDataStore::notifyContentListeners ();
      }
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


bool
LocalRasterDataStore::addFileInternal (const char* filePath,
                                        const char* typeHint,
                                        DatasetDescriptor::CreationCallback *callback)
  {
    bool success (false);               // Be appropriately optimistic.
    TAK::Engine::Util::array_ptr<const char> layerDir (util::createTempDir (getWorkingDir (),
                                                "layer", "priv"));

    if (layerDir.get())
      {
        class DirDeleter
        {
        public :
            DirDeleter(const char *d_) : d(d_) {}
            ~DirDeleter() { if(d) util::removeDir(d); }
            void dismiss() { d = nullptr; }
        private :
            const char *d;
        };
        DirDeleter dirDeleter(layerDir.get());
        std::unique_ptr<DescriptorSet> layers
            (DatasetDescriptor::create (filePath, layerDir.get(), typeHint, callback));

        if (layers.get ())
          {
            Lock lock(getMutex());

            if (containsFileImpl (filePath))
              {
                std::ostringstream errString;
                errString << MEM_FN ("addFileInternal") <<
                                       "Data store already contains entry for " <<
                                       filePath;

                throw std::invalid_argument (errString.str());
              }
            success = addFileImpl (filePath, *layers, layerDir.get());
            notifyContentListeners ();
          
              //XXX- fix memory leak
              for (auto it = layers->begin(); it != layers->end(); ++it) {
                  delete *it;
              }
          }
        if (success)
          {
            dirDeleter.dismiss ();      // Call off the dogs.
          }
      }

    return success;
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.
