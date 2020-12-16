////============================================================================
////
////    FILE:           ServiceManagerImpl.cpp
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/ServiceManagerImpl.h"

#include "thread/Lock.h"


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////

using namespace TAK::Engine::Thread;

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
namespace core                          // Open core namespace.
{


ServiceManagerImpl::ServiceManagerImpl(TAK::Engine::Thread::Mutex& mutex)
    : mutex(mutex)
{ }

Service*
ServiceManagerImpl::getService (const char* serviceType)
    const
  {
    Service* result = nullptr;

    if (serviceType)
      {
        Lock lock(mutex);
        auto iter
            (services.find (serviceType));

        if (iter != services.end ())
          {
            result = iter->second;
          }
      }

    return result;
  }


void
ServiceManagerImpl::registerService (Service* service)
  {
    if (service && service->getType ())
      {
        Lock lock(mutex);
    
        services[service->getType ()] = service;
      }
  }


void
ServiceManagerImpl::unregisterService (Service* service)
  {
    if (service && service->getType ())
      {
        Lock lock(mutex);


        services.erase (service->getType ());
      }
  }


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////

