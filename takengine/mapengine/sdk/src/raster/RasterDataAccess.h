////============================================================================
////
////    FILE:           RasterDataAccess.h
////
////    DESCRIPTION:    Abstract base class for providing access to the
////                    underlying raster data in RasterLayer.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 21, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_RASTER_DATA_ACCESS_H_INCLUDED
#define ATAKMAP_RASTER_RASTER_DATA_ACCESS_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/GeoPoint.h"
#include "math/Point.h"


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
///  class atakmap::raster::RasterDataAccess
///
///     Provides access to the underlying raster data in the RasterLayer.
///
///     The image-to-ground and ground-to-image functions provide conversion
///     between pixel coordinates in the image and geodetic coordinates.  This
///     coordinate transformation is based on the projection of the imagery as
///     well as the registration of the imagery on the map and should be
///     considered an interpolative transformation.  These functions are not
///     sufficient for imagery with robust image-to-ground and ground-to-image
///     functions.  Data with robust functions should use the
///     PrecisionRasterDataAccess class.
///
///=============================================================================


class RasterDataAccess
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~RasterDataAccess ()
        NOTHROWS
        = 0;

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor, and assignment operator are acceptable.
    //

    //
    // Returns the spatial reference ID for the raster data (or -1 if the
    // spatial reference is not well-defined).
    //
    virtual
    int
    getSpatialReferenceID ()
        const
        NOTHROWS
        = 0;

    //
    // Returns the type of the raster data.
    //
    virtual
    const char*
    getType ()
        const
        NOTHROWS
        = 0;

    //
    // Returns the URI for the raster data.
    //
    virtual
    const char*
    getURI ()
        const
        NOTHROWS
        = 0;

    //
    // Performs the ground-to-image function for the supplied geodetic
    // coordinate.
    //
    virtual
    math::Point<double>
    groundToImage (const core::GeoPoint& ground)
        const
        = 0;

    //
    // Performs the ground-to-image function for the supplied geodetic
    // coordinate.  Updates the supplied image point.
    //
    virtual
    math::Point<double>
    groundToImage (const core::GeoPoint& ground,
                   math::Point<double>& image)
        const
        = 0;

    //
    // Performs the image-to-ground function for the supplied pixel in the
    // image.
    //
    virtual
    core::GeoPoint
    imageToGround (const math::Point<double>& image)
        const
        = 0;

    //
    // Performs the image-to-ground function for the supplied pixel in the
    // image.  Updates the supplied geodetic coordinate.
    //
    virtual
    core::GeoPoint
    imageToGround (const math::Point<double>& image,
                   core::GeoPoint& ground)
        const
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    RasterDataAccess ()
      { }
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

#endif  // #ifndef ATAKMAP_RASTER_RASTER_DATA_ACCESS_H_INCLUDED
