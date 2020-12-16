////============================================================================
////
////    FILE:           DatasetRasterLayerImpl.h
////
////    DESCRIPTION:    Definition of concrete DataStoreRasterLayer class.
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


#ifndef ATAKMAP_RASTER_DATASET_RASTER_LAYER_IMPL_H_INCLUDED
#define ATAKMAP_RASTER_DATASET_RASTER_LAYER_IMPL_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>

#include "raster/AbstractDataStoreRasterLayer.h"
#include "raster/RasterDataStore.h"
#include "port/Iterator.h"


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
///  class atakmap::raster::DatasetRasterLayerImpl
///
///     Concrete implementation of DataStoreRasterLayer.
///
///=============================================================================


class DatasetRasterLayerImpl
  : public AbstractDataStoreRasterLayer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Constructs a DatasetRasterLayerImpl with the supplied name and
    // RasterDataStore.
    //
    // Throws std::invalid_argument on NULL name or RasterDataStore.
    //
    DatasetRasterLayerImpl (const char* name, // Must be non-NULL.
                            RasterDataStore*, // Must be non-NULL.
                            std::size_t datasetLimit);

    ~DatasetRasterLayerImpl ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    //
    // RasterLayer member functions.
    //

    feature::UniqueGeometryPtr
    getGeometry (const char* selection)
        const
      { return getDataStore()->getCoverage (selection, NULL); }

      atakmap::port::Iterator<const char*> *
    getSelectionOptions ()
      const;
      
    std::size_t
    getDatasetLimit()
      const
    { return datasetLimit; }

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // Private representation.
    //

    std::size_t datasetLimit;
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

#endif  // #ifndef ATAKMAP_RASTER_DATASET_RASTER_LAYER_H_INCLUDED
