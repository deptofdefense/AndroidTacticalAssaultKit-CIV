////============================================================================
////
////    FILE:           ServiceManagerImpl.h
////
////    DESCRIPTION:    Implementation of Service::Manager interface.
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


#ifndef ATAKMAP_CORE_SERVICE_MANAGER_IMPL_H_INCLUDED
#define ATAKMAP_CORE_SERVICE_MANAGER_IMPL_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <map>

#include "core/Service.h"
#include "port/Platform.h"
#include "port/String.h"
#include "thread/Mutex.h"


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
///  class atakmap::core::ServiceMangerImpl
///
///     Abstract base implementation of manager for layer services.
///
///=========================================================================


class ENGINE_API ServiceManagerImpl
  : public virtual Service::Manager
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~ServiceManagerImpl ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler cannot generate
    // a copy constructor or assignment operator (due to a NonCopyable data
    // member.  This is acceptable.
    //


    //==================================
    //  Service::Manager INTERFACE
    //==================================


    //
    // Returns the specified Service for this layer, or NULL if no service of
    // the specified type is available.
    //
    Service*
    getService (const char* serviceType)
        const;

    //
    // Registers the specified service on the layer.  Implementors may use this
    // to install services on their layer.
    //
    void
    registerService (Service*);

    //
    // Unregisters the specified service from the layer.
    //
    void
    unregisterService (Service*);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    ServiceManagerImpl (TAK::Engine::Thread::Mutex&);


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    TAK::Engine::Thread::Mutex& mutex;
    std::map<TAK::Engine::Port::String, Service*, TAK::Engine::Port::StringLess> services;
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


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


#endif  // #ifndef ATAKMAP_CORE_SERVICE_MANAGER_IMPL_H_INCLUDED
