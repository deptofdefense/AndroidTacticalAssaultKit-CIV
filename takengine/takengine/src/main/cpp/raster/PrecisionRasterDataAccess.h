////============================================================================
////
////    FILE:           PrecisionRasterDataAccess.h
////
////    DESCRIPTION:    Abstract derived RasterDataAccess class that provides
////                    precise image-to-ground and ground-to-image functions.
////                    These functions reflect the robust algorithms native to
////                    the data, without regard for the registration on the map.
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


#ifndef ATAKMAP_RASTER_PRECISION_RASTER_DATA_ACCESS_H_INCLUDED
#define ATAKMAP_RASTER_PRECISION_RASTER_DATA_ACCESS_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/RasterDataAccess.h"


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
///  class atakmap::raster::PrecisionRasterDataAccess
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


class PrecisionRasterDataAccess
  : public RasterDataAccess
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~PrecisionRasterDataAccess ()
        NOTHROWS
        = 0;

    //
    // A protected constructor is declared below.  The compiler-generated copy
    // constructor, and assignment operator are acceptable.
    //

    //
    // Performs the precise ground-to-image function for the supplied geodetic
    // coordinate.
    //
    virtual
    math::Point<double>
    preciseGroundToImage (const core::GeoPoint& ground)
        const
        = 0;

    //
    // Performs the precise ground-to-image function for the supplied geodetic
    // coordinate.  Updates the supplied image point.
    //
    virtual
    math::Point<double>
    preciseGroundToImage (const core::GeoPoint& ground,
                          math::Point<double>& image)
        const
        = 0;

    //
    // Performs the precise image-to-ground function for the supplied pixel in
    // the image.
    //
    virtual
    core::GeoPoint
    preciseImageToGround (const math::Point<double>& image)
        const
        = 0;

    //
    // Performs the precise image-to-ground function for the supplied pixel in
    // the image.  Updates the supplied geodetic coordinate.
    //
    virtual
    core::GeoPoint
    preciseImageToGround (const math::Point<double>& image,
                          core::GeoPoint& ground)
        const
        = 0;

    //
    // Returns the precise geodetic coordinate corresponding to the raster data
    // pixel registered on the map at the supplied geodetic coordinate.
    //
    static
    core::GeoPoint
    refine (const PrecisionRasterDataAccess& mapData,
            const core::GeoPoint& gPoint)
      { return mapData.preciseImageToGround (mapData.groundToImage (gPoint)); }


    //
    // Returns the geodetic coordinate on the map corresponding to the raster
    // data pixel at the supplied precise geodetic coordinate.
    //
    static
    core::GeoPoint
    unrefine (const PrecisionRasterDataAccess& mapData,
            const core::GeoPoint& gPoint)
      { return mapData.imageToGround (mapData.preciseGroundToImage (gPoint)); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    PrecisionRasterDataAccess ()
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

#endif  // #ifndef ATAKMAP_RASTER_PRECISION_RASTER_DATA_ACCESS_H_INCLUDED
