////============================================================================
////
////    FILE:           Distance.h
////
////    DESCRIPTION:    Distance-related utility functions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 15, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_UTIL_DISTANCE_H_INCLUDED
#define ATAKMAP_UTIL_DISTANCE_H_INCLUDED

#include "port/Platform.h"

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


class GeoPoint;


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace util                          // Open util namespace.
{
namespace distance                      // Open distance namespace.
{


///
///  Uses the Vincenty algorithm to calculate the distance between two GeoPoints
///  in meters.  This distance is an approximation due to the nature of
///  geodesics.
///
ENGINE_API double
calculateRange (const core::GeoPoint& from,
                const core::GeoPoint& to);

///
/// Computes the GeoPoint at the given range and bearing from the source
/// GeoPoint using the Vincenty forward algorithm. Range is specified in
/// meters; azimuth in degrees.
///

ENGINE_API void
pointAtRange (const core::GeoPoint& from,
              const double range,
              const double azimuth,
              core::GeoPoint& to);

ENGINE_API bool
computeDirection(const core::GeoPoint &point1,
                 const core::GeoPoint &point2,
                 double distAzimutOut[]);

}                                       // Close distance namespace.
}                                       // Close util namespace.
}                                       // Close atakmap namespace.


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


#endif  // #ifndef ATAKMAP_UTIL_DISTANCE_H_INCLUDED
