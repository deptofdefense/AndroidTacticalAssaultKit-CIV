////============================================================================
////
////    FILE:           Envelope.h
////
////    DESCRIPTION:    Three-dimensional bounding box.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 3, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_ENVELOPE_H_INCLUDED
#define ATAKMAP_FEATURE_ENVELOPE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////

#include "port/Platform.h"

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
namespace feature                       // Open feature namespace.
{


struct Envelope
  {
    Envelope()
      NOTHROWS
      : minX (0),
        minY (0),
        minZ (0),
        maxX (0),
        maxY (0),
        maxZ (0)
      { }
      
    Envelope (double minX,
              double minY,
              double minZ,
              double maxX,
              double maxY,
              double maxZ)
        NOTHROWS
      : minX (minX),
        minY (minY),
        minZ (minZ),
        maxX (maxX),
        maxY (maxY),
        maxZ (maxZ)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    //
    // Public representation.
    //

    double minX;
    double minY;
    double minZ;
    double maxX;
    double maxY;
    double maxZ;
  };


}                                       // Close feature namespace.
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

#endif  // #ifndef ATAKMAP_FEATURE_ENVELOPE_H_INCLUDED
