////============================================================================
////
////    FILE:           MosaicDatasetDescriptor.cpp
////
////    DESCRIPTION:    Implementation of MosaicDatasetDescriptor class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 16, 2014  scott           Created.
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


#include "raster/MosaicDatasetDescriptor.h"


#include "feature/Geometry.h"
#include "util/IO.h"

////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////

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

namespace atakmap {
namespace raster {

MosaicDatasetDescriptor::MosaicDatasetDescriptor
    (const char* name,
     const char* URI,
     const char* provider,
     const char* datasetType,
     const char* mosaicPath,
     const char* mosaicProvider,
     const StringVector& imageryTypes,
     const ResolutionMap& resolutions,
     const CoverageMap& coverages,
     int referenceID,
     bool isRemote,
     const char* workingDir,
     const StringMap& extraData)
  : DatasetDescriptor (MOSAIC,
                       0,
                       name,
                       URI,
                       provider,
                       datasetType,
                       imageryTypes,
                       resolutions,
                       cloneCoverageMap(coverages),
                       referenceID,
                       isRemote,
                       workingDir,
                       extraData),
    mosaicPath (mosaicPath),
    mosaicProvider (mosaicProvider)
  { }

}
}
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
namespace raster                        // Open raster namespace.
{


MosaicDatasetDescriptor::MosaicDatasetDescriptor
    (int64_t layerID,
     const char* name,
     const char* URI,
     const char* provider,
     const char* datasetType,
     const char* mosaicPath,
     const char* mosaicProvider,
     const StringVector& imageryTypes,
     const ResolutionMap& resolutions,
     const CoverageMap& coverages,
     int referenceID,
     bool isRemote,
     const char* workingDir,
     const StringMap& extraData)
  : DatasetDescriptor (MOSAIC,
                       layerID,
                       name,
                       URI,
                       provider,
                       datasetType,
                       imageryTypes,
                       resolutions,
                       cloneCoverageMap(coverages),
                       referenceID,
                       isRemote,
                       workingDir,
                       extraData),
    mosaicPath (mosaicPath),
    mosaicProvider (mosaicProvider)
  { }


///
///  atakmap::raster::DatasetDescriptor member functions.
///


void
MosaicDatasetDescriptor::encodeDetails (std::ostream& strm)
    throw (util::IO_Error)
  {
    util::write<bool> (strm, mosaicPath.get() != nullptr);
    if (mosaicPath)
      {
        util::writeUTF (strm, mosaicPath);
      }
    util::write<bool> (strm, mosaicProvider.get() != nullptr);
    if (mosaicProvider)
      {
        util::writeUTF (strm, mosaicProvider);
      }
  }

TAK::Engine::Util::TAKErr MosaicDatasetDescriptor::clone(DatasetDescriptorUniquePtr &value) const NOTHROWS {
    value = DatasetDescriptorUniquePtr(new MosaicDatasetDescriptor(*this), DatasetDescriptor::deleteDatasetDescriptor);
    return TAK::Engine::Util::TE_Ok;
}

}                                       // Close raster namespace.
}                                       // Close atakmap namespace.
