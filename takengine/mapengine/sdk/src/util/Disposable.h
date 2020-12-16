////============================================================================
////
////    FILE:           Disposable.h
////
////    DESCRIPTION:    Interface supporting object disposal.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 23, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_UTIL_DISPOSABLE_H_INCLUDED
#define ATAKMAP_UTIL_DISPOSABLE_H_INCLUDED


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
namespace util                          // Open util namespace.
{


///=============================================================================
///
///  class atakmap::util::Disposable
///
///     Interface supporting object disposal.  Disposal should realease all
///     resources associated with the object.  Use of the object following
///     disposal is undefined.
///
///=============================================================================


class Disposable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~Disposable ()
        NOTHROWS
        = 0;

    //
    // Releases all resources associated with the object.  The object should not
    // be used following an invocation of this member functions.  Any usage will
    // result in undefined (including application terminating) behavior.
    //
    virtual
    void
    dispose ()
        = 0;
  };


}                                       // Close util namespace.
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


#endif  // #ifndef ATAKMAP_UTIL_DISPOSABLE_H_INCLUDED
