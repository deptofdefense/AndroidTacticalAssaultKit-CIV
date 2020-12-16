////============================================================================
////
////    FILE:           AutoSelectService.h
////
////    DESCRIPTION:    Definition of abstract base class for a content
////                    auto-select service for a raster layer.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 8, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_AUTO_SELECT_SERVICE_H_INCLUDED
#define ATAKMAP_RASTER_AUTO_SELECT_SERVICE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/Service.h"
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
namespace raster                        // Open raster namespace.
{


///=============================================================================
///
///  class atakmap::raster::AutoSelectService
///
///     Content auto-select service for a RasterLayer.  Provides information
///     about the auto-select value and callback notification when the value
///     value changes.
///
///     It is recommended that RasterLayer implementations that support
///     auto-select expose this service.
///
///=============================================================================


class AutoSelectService
  : public core::Service
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class ValueListener;


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const SERVICE_TYPE;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~AutoSelectService ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Registers the supplied ValueListener for notifications when the
    // auto-select value changes.
    //
    virtual
    void
    addValueListener (ValueListener*)
        = 0;

    virtual
    const char*
    getAutoSelectValue ()
        const
        = 0;

    //
    // Returns the type of service, as used for registration with the layer.
    //
    virtual
    const char*
    getType ()
        const
        NOTHROWS
      { return SERVICE_TYPE; }

    //
    // Unregisters the supplied ValueListener from notifications of auto-select
    // value changes.
    //
    virtual
    void
    removeValueListener (ValueListener*)
        = 0;
  };


///=========================================================================
///
///  class atakmap::core::Layer::VisibilityListener
///
///     Abstract base class for layer visibility change callbacks.
///
///=========================================================================


class AutoSelectService::ValueListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~ValueListener ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the auto-select value of the supplied AutoSelectService has
    // changed.
    //
    virtual
    void
    autoSelectValueChanged (AutoSelectService&)
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

#endif  // #ifndef ATAKMAP_RASTER_AUTO_SELECT_SERVICE_H_INCLUDED
