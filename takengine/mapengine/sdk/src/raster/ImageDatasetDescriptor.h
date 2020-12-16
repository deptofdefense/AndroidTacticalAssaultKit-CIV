////============================================================================
////
////    FILE:           ImageDatasetDescriptor.h
////
////    DESCRIPTION:    A dataset descriptor for image data.
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


#ifndef ATAKMAP_RASTER_IMAGE_DATASET_DESCRIPTOR_H_INCLUDED
#define ATAKMAP_RASTER_IMAGE_DATASET_DESCRIPTOR_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>

#include "core/GeoPoint.h"
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
///  class atakmap::raster::ImageDatasetDescriptor
///
///     Dataset descriptor for image data.
///
///=============================================================================


class ENGINE_API ImageDatasetDescriptor
    : public DatasetDescriptor
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Creates a new ImageDatasetDescriptor.
    //
    //  name            The name of the dataset.
    //  URI             The URI for the dataset.
    //  provider        The name of the service provider responsible for
    //                  creation.
    //  datasetType     The dataset type.
    //  imageryType     The imagery type for the dataset.
    //  width           Image width.
    //  height          Image height.
    //  resolutionCount The number of resolutions levels in the dataset.
    //  ul              The coordinates of the upper left corner of the imagery.
    //  ur              The coordinates of the upper right corner of the imagery.
    //  lr              The coordinates of the lower right corner of the imagery.
    //  ll              The coordinates of the lower left corner of the imagery.
    //  referenceID     The Spatial Reference ID for the dataset or -1 if not
    //                  known or not well-defined.
    //  isRemote        A flag indicating whether the dataset content is local
    //                  or remote.
    //  workingDir      The (possibly NULL) working directory for the dataset.
    //  extraData       Opaque application specified data associated with the
    //                  dataset.
    //
    // Throws std::invalid_argument if name, URI, provider, datasetType, or
    // imageryType is NULL.
    //
    ImageDatasetDescriptor (const char* name,
                            const char* URI,
                            const char* provider,
                            const char* datasetType,
                            const char* imageryType,
                            std::size_t width,
                            std::size_t height,
                            std::size_t resolutionCount,
                            const core::GeoPoint& ul,
                            const core::GeoPoint& ur,
                            const core::GeoPoint& lr,
                            const core::GeoPoint& ll,
                            int referenceID,
                            bool isRemote,
                            const char* workingDir,
                            const StringMap& extraData);

    //
    // Creates a new ImageDatasetDescriptor.
    //
    //  name            The name of the dataset.
    //  URI             The URI for the dataset.
    //  provider        The name of the service provider responsible for
    //                  creation.
    //  datasetType     The dataset type.
    //  imageryType     The imagery type for the dataset.
    //  width           Image width.
    //  height          Image height.
    //  resolution      The GSD of the minimum resolution of the imagery.
    //  resolutionCount The number of resolutions levels in the dataset.
    //  ul              The coordinates of the upper left corner of the imagery.
    //  ur              The coordinates of the upper right corner of the imagery.
    //  lr              The coordinates of the lower right corner of the imagery.
    //  ll              The coordinates of the lower left corner of the imagery.
    //  referenceID     The Spatial Reference ID for the dataset or -1 if not
    //                  known or not well-defined.
    //  isRemote        A flag indicating whether the dataset content is local
    //                  or remote.
    //  workingDir      The (possibly NULL) working directory for the dataset.
    //  extraData       Opaque application specified data associated with the
    //                  dataset.
    //
    // Throws std::invalid_argument if name, URI, provider, datasetType, or
    // imageryType is NULL.
    //
    ImageDatasetDescriptor (const char* name,
                            const char* URI,
                            const char* provider,
                            const char* datasetType,
                            const char* imageryType,
                            std::size_t width,
                            std::size_t height,
                            double resolution,
                            std::size_t resolutionCount,
                            const core::GeoPoint& ul,
                            const core::GeoPoint& ur,
                            const core::GeoPoint& lr,
                            const core::GeoPoint& ll,
                            int referenceID,
                            bool isRemote,
                            const char* workingDir,
                            const StringMap& extraData);

	//
	// Creates a new ImageDatasetDescriptor.
	//
	//  name            The name of the dataset.
	//  URI             The URI for the dataset.
	//  provider        The name of the service provider responsible for
	//                  creation.
	//  datasetType     The dataset type.
	//  imageryType     The imagery type for the dataset.
	//  width           Image width.
	//  height          Image height.
	//  resolution      The GSD of the minimum resolution of the imagery.
	//  resolutionCount The number of resolutions levels in the dataset.
	//  ul              The coordinates of the upper left corner of the imagery.
	//  ur              The coordinates of the upper right corner of the imagery.
	//  lr              The coordinates of the lower right corner of the imagery.
	//  ll              The coordinates of the lower left corner of the imagery.
	//  referenceID     The Spatial Reference ID for the dataset or -1 if not
	//                  known or not well-defined.
	//  isRemote        A flag indicating whether the dataset content is local
	//                  or remote.
    //  precisionImagery
	//  workingDir      The (possibly NULL) working directory for the dataset.
	//  extraData       Opaque application specified data associated with the
	//                  dataset.
	//
	// Throws std::invalid_argument if name, URI, provider, datasetType, or
	// imageryType is NULL.
	//
	ImageDatasetDescriptor(const char* name,
                           const char* URI,
		                   const char* provider,
		                   const char* datasetType,
		                   const char* imageryType,
		                   std::size_t width,
		                   std::size_t height,
		                   double resolution,
		                   std::size_t resolutionCount,
		                   const core::GeoPoint& ul,
		                   const core::GeoPoint& ur,
		                   const core::GeoPoint& lr,
		                   const core::GeoPoint& ll,
		                   int referenceID,
		                   bool isRemote,
		                   bool precisionImagery,
		                   const char* workingDir,
		                   const StringMap& extraData);

    ImageDatasetDescriptor(const ImageDatasetDescriptor &) = default;

    virtual
    ~ImageDatasetDescriptor ()
        NOTHROWS override
    { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    std::size_t
    getHeight ()
        const
        NOTHROWS
      { return height; }

    const char*
    getImageryType ()
        const
        NOTHROWS
      { return imageryType; }

    inline bool getIsPrecisionImagery()
        const
        NOTHROWS
    { return isPrecisionImagery; }

    std::size_t
    getLevelCount ()
        const
        NOTHROWS
      { return levelCount; }

    core::GeoPoint
    getLowerLeft ()
        const
        NOTHROWS
      { return ll; }

    core::GeoPoint
    getLowerRight ()
        const
        NOTHROWS
      { return lr; }

    core::GeoPoint
    getUpperLeft ()
        const
        NOTHROWS
      { return ul; }

    core::GeoPoint
    getUpperRight ()
        const
        NOTHROWS
      { return ur; }

    std::size_t
    getWidth ()
        const
        NOTHROWS
      { return width; }
      
      
    virtual TAK::Engine::Util::TAKErr clone(DatasetDescriptorUniquePtr &value) const NOTHROWS override;

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

      
                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    friend class DatasetDescriptor;

    //
    // Creates a new ImageDatasetDescriptor.
    //
    //  layerID         The ID of the dataset.
    //  name            The name of the dataset.
    //  URI             The URI for the dataset.
    //  provider        The name of the service provider responsible for
    //                  creation.
    //  datasetType     The dataset type.
    //  imageryType     The imagery type for the dataset.
    //  width           Image width.
    //  height          Image height.
    //  minResolution   The GSD of the minimum resolution of the imagery.
    //  maxResolution   The GSD of the maximum resolution of the imagery.
    //  coverage        The coverage of the imagery.
    //  referenceID     The Spatial Reference ID for the dataset or -1 if not
    //                  known or not well-defined.
    //  isRemote        A flag indicating whether the dataset content is local
    //                  or remote.
    //  precisionImagery
    //  workingDir      The (possibly NULL) working directory for the dataset.
    //  extraData       Opaque application specified data associated with the
    //                  dataset.
    //
    // The supplied Geometry object is copied by the ImageDatasetDescriptor.
    // Deletion of the supplied Geometry object remains the responsibility of
    // the caller.
    //
    // Throws std::invalid_argument if name, URI, provider, datasetType, or
    // imageryType is NULL.
    //
    ImageDatasetDescriptor (int64_t layerID,
                            const char* name,
                            const char* URI,
                            const char* provider,
                            const char* datasetType,
                            const char* imageryType,
                            std::size_t width,
                            std::size_t height,
                            double minResolution,
                            double maxResolution,
                            const feature::Geometry* coverage,  // Never NULL.
                            int referenceID,
                            bool isRemote,
                            bool precisionImagery,
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


    TAK::Engine::Port::String imageryType;
    std::size_t width;
    std::size_t height;
    core::GeoPoint ul;
    core::GeoPoint ur;
    core::GeoPoint lr;
    core::GeoPoint ll;
    std::size_t levelCount;
	bool isPrecisionImagery;
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

#endif  // #ifndef ATAKMAP_RASTER_IMAGE_DATASET_DESCRIPTOR_H_INCLUDED
