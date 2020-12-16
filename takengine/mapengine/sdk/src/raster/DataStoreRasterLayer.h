////============================================================================
////
////    FILE:           DataStoreRasterLayer.h
////
////    DESCRIPTION:    Declaration of abstract base class for raster data
////                    layers backed by RasterDataStores.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 9, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_DATA_STORE_RASTER_LAYER_H_INCLUDED
#define ATAKMAP_RASTER_DATA_STORE_RASTER_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/RasterLayer.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


class RasterDataStore;


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
///  class atakmap::raster::DataStoreRasterLayer
///
///     Abstract base class for raster data layers backed by a raster data store.
///
///=============================================================================


class DataStoreRasterLayer
  : public virtual RasterLayer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // The compiler-generated constructor, copy constructor, destructor, and
    // assignment operator are acceptable.
    //

    //
    // Returns the data store associated with this layer.
    //
    virtual
    RasterDataStore*
    getDataStore ()
        const
        = 0;
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

#endif  // #ifndef ATAKMAP_RASTER_DATA_STORE_RASTER_LAYER_H_INCLUDED
