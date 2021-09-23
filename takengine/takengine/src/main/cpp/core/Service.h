////============================================================================
////
////    FILE:           Service.h
////
////    DESCRIPTION:    Abstract base class for service pattern.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 21, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_CORE_SERVICE_H_INCLUDED
#define ATAKMAP_CORE_SERVICE_H_INCLUDED


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
namespace core                          // Open core namespace.
{


///=========================================================================
///
///  class atakmap::core::Service
///
///     Abstract base class for layer services.
///
///     Services may provide optional functions for the layer that are not
///     expressly part of the API.  The Service::Manager::getService member
///     function provides a mechanism for other users to acquire access to that
///     functionality for a layer.
///
///     The service pattern provides layer implementors with the flexibility to
///     distribute well-defined functionality outside of the model domain.
///     Specifically, it can provide a pluggable point for functionality that
///     may be within the domain of the renderer; the application would normally
///     have no means to communicate with the renderer.  It also allows for
///     delegation of model domain functionality that can be more efficiently
///     serviced by the renderer.
///
///=========================================================================


class Service
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class Manager;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~Service ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Returns the type of service, as used for registration with the layer.
    //
    virtual
    const char*
    getType ()
        const
        NOTHROWS
        = 0;
  };


///=============================================================================
///
///  class atakmap::core::Service::Manager
///
///     Abstract base class for management of Services.
///
///=============================================================================


class Service::Manager
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~Manager ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Returns the specified Service for this layer, or NULL if no service of
    // the specified type is available.
    //
    virtual
    Service*
    getService (const char* serviceType)
        const
        = 0;

    //
    // Registers the specified service on the layer.  Implementors may use this
    // to install services on their layer.
    //
    virtual
    void
    registerService (Service*)
        = 0;

    //
    // Unregisters the specified service from the layer.
    //
    virtual
    void
    unregisterService (Service*)
        = 0;
  };


}                                       // Close core namespace.
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


#endif  // #ifndef ATAKMAP_CORE_SERVICE_H_INCLUDED
