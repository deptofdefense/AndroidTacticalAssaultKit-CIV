////============================================================================
////
////    FILE:           AbstractDataStoreRasterLayer.h
////
////    DESCRIPTION:    Definition of abstract base class for
////                    DataStoreRasterLayer implementations.
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


#ifndef ATAKMAP_RASTER_ABSTRACT_DATA_STORE_RASTER_LAYER_H_INCLUDED
#define ATAKMAP_RASTER_ABSTRACT_DATA_STORE_RASTER_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/AbstractRasterLayer.h"
#include "raster/DataStoreRasterLayer.h"
#include "raster/RasterDataStore.h"

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4250)
#endif

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
///  class atakmap::raster::AbstractDataStoreRasterLayer
///
///     Abstract base class for DataStoreRasterLayer implementations.
///
///     Concrete derived classes must implement the following RasterLayer member
///     functions:
///
///     getGeometry
///     getSelectionOptions
///
///=============================================================================


class AbstractDataStoreRasterLayer
    : public DataStoreRasterLayer,
      public AbstractRasterLayer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//
      
    ~AbstractDataStoreRasterLayer ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //

    //
    // DataStoreRasterLayer member functions.
    //

    RasterDataStore*
    getDataStore ()
        const
      { return dataStore; }

    void
    filterQueryParams(RasterDataStore::DatasetQueryParameters &params)
        const;

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

    //
    // Constructs an AbstractDataStoreRasterLayer with the supplied name and
    // RasterDataStore.
    //
    // Throws std::invalid_argument on NULL name or RasterDataStore.
    //
    AbstractDataStoreRasterLayer (const char* name,  // Must be non-NULL.
                                  RasterDataStore*,  // Must be non-NULL.
                                  RasterDataStore::DatasetQueryParameters);

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    //
    // Private representation.
    //

    RasterDataStore* dataStore;
    RasterDataStore::DatasetQueryParameters filterParams;
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
#ifdef _MSC_VER
#pragma warning(pop)
#endif
#endif  // #ifndef ATAKMAP_RASTER_ABSTRACT_DATA_STORE_RASTER_LAYER_H_INCLUDED
