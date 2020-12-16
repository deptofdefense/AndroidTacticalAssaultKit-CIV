////============================================================================
////
////    FILE:           MosaicDatasetDescriptor.h
////
////    DESCRIPTION:    A dataset descriptor for a mosaic database of image data.
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


#ifndef ATAKMAP_RASTER_MOSAIC_DATASET_DESCRIPTOR_H_INCLUDED
#define ATAKMAP_RASTER_MOSAIC_DATASET_DESCRIPTOR_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "port/Platform.h"
#include "port/String.h"
#include "raster/DatasetDescriptor.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////

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
///  class atakmap::raster::MosaicDatasetDescriptor
///
///     Dataset descriptor for a mosaic database of image data.
///
///=============================================================================


class ENGINE_API MosaicDatasetDescriptor
    : public DatasetDescriptor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using DatasetDescriptor::CoverageMap;
    using DatasetDescriptor::ResolutionMap;
    using DatasetDescriptor::StringMap;
    using DatasetDescriptor::StringVector;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    //
    // Creates a new MosaicDatasetDescriptor.
    //
    //  name            The name of the dataset.
    //  URI             The URI for the dataset.
    //  provider        The name of the service provider responsible for
    //                  creation.
    //  datasetType     The dataset type.
    //  mosaicPath      Path to the mosaic DB.
    //  mosaicProvider  Service provider for mosaic DB.
    //  imageryTypes    The (non-empty) imagery types available for the dataset.
    //  resolutions     The resolutions for each imagery type in the dataset.
    //  coverages       The coverage for each imagery type in the dataset; the
    //                  descriptor will create and manage its own copies of the
    //                  provided Geometry objects.
    //  referenceID     The Spatial Reference ID for the dataset or -1 if not
    //                  known or not well-defined.
    //  isRemote        A flag indicating whether the dataset content is local
    //                  or remote.
    //  workingDir      The (possibly NULL) working directory for the dataset.
    //  extraData       Opaque application specified data associated with the
    //                  dataset.
    //
    // The Geometry objects in the supplied CoverageMap are copied by the
    // MosaicDatasetDescriptor.  Deletion of the Geometry objects in the
    // supplied CoverageMap remains the responsibility of the caller.
    //
    // Throws std::invalid_argument if name, URI, provider, datasetType, or
    // imageryType is NULL.
    //
    MosaicDatasetDescriptor (const char* name,
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
                             const StringMap& extraData);

    MosaicDatasetDescriptor(const MosaicDatasetDescriptor &) = default;

    virtual
    ~MosaicDatasetDescriptor ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    const char*
    getMosaicPath ()
        const
        NOTHROWS
      { return mosaicPath; }

    const char*
    getMosaicProvider ()
        const
        NOTHROWS
      { return mosaicProvider; }
      
    virtual TAK::Engine::Util::TAKErr clone(DatasetDescriptorUniquePtr &value) const NOTHROWS override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//
      
                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    friend class DatasetDescriptor;

    //
    // Creates a new MosaicDatasetDescriptor.  Used by DatasetDescriptor.
    //
    //  layerID         The ID of the dataset.
    //  name            The name of the dataset.
    //  URI             The URI for the dataset.
    //  provider        The name of the service provider responsible for
    //                  creation.
    //  datasetType     The dataset type.
    //  mosaicPath      Path to the mosaic DB.
    //  mosaicProvider  Service provider for mosaic DB.
    //  imageryTypes    The (non-empty) imagery types available for the dataset.
    //  resolutions     The resolutions for each imagery type in the dataset.
    //  coverages       The coverage for each imagery type in the dataset.
    //  referenceID     The Spatial Reference ID for the dataset or -1 if not
    //                  known or not well-defined.
    //  isRemote        A flag indicating whether the dataset content is local
    //                  or remote.
    //  workingDir      The (possibly NULL) working directory for the dataset.
    //  extraData       Opaque application specified data associated with the
    //                  dataset.
    //
    // The Geometry objects in the supplied CoverageMap are copied by the
    // MosaicDatasetDescriptor.  Deletion of the Geometry objects in the
    // supplied CoverageMap remains the responsibility of the caller.
    //
    // Throws std::invalid_argument if name, URI, provider, datasetType, or
    // imageryType is NULL.
    //
    MosaicDatasetDescriptor (int64_t layerID,
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
                             const StringMap& extraData);


    //==================================
    //  DatasetDescriptor IMPLEMENTATION
    //==================================


    void
    encodeDetails (std::ostream&)       // Called by encode.
        throw (util::IO_Error) override;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    TAK::Engine::Port::String mosaicPath;
    TAK::Engine::Port::String mosaicProvider;
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

#endif  // #ifndef ATAKMAP_RASTER_MOSAIC_DATASET_DESCRIPTOR_H_INCLUDED
