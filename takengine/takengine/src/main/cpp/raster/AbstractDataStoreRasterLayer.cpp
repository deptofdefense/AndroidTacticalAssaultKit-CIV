////============================================================================
////
////    FILE:           AbstractDataStoreRasterLayer.cpp
////
////    DESCRIPTION:    Implementation for AbstractDataStoreRasterLayer class.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "raster/AbstractDataStoreRasterLayer.h"

#include <stdexcept>


#define MEM_FN( fn ) \
        "atakmap::raster::AbstractDataStoreRasterLayer::" fn ": "


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

namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{

void
AbstractDataStoreRasterLayer::filterQueryParams(RasterDataStore::DatasetQueryParameters &params)
    const
  {
      params.intersect(filterParams);
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


AbstractDataStoreRasterLayer::AbstractDataStoreRasterLayer
    (const char* name,
     RasterDataStore* dataStore,
     RasterDataStore::DatasetQueryParameters params)
  : AbstractRasterLayer (name),
    dataStore (dataStore),
    filterParams(params)
  {
    if (!dataStore)
      {
        throw std::invalid_argument (MEM_FN ("AbstractDataStoreRasterLayer")
                                     "Received NULL RasterDataStore");
      }
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
