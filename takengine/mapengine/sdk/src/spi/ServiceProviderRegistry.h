////============================================================================
////
////    FILE:           ServiceProviderRegistry.h
////
////    DESCRIPTION:    Definition of ServiceProviderRegistry class templates.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      May 19, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_SRC_SPI_SERVICE_PROVIDER_REGISTRY_H_INCLUDED
#define ATAKMAP_SRC_SPI_SERVICE_PROVIDER_REGISTRY_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>

#include "spi/ServiceProvider.h"
#include "spi/ServiceProviderRegistryDetails.h"

#include "thread/Lock.h"
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
namespace spi                           // Open spi namespace.
{


///=============================================================================
///
///  class template spi::ServiceProviderRegistry<ProviderT, StrategyT, CallbackT>
///
///     Concrete registry for ServiceProviders and factory for ServiceProvider
///     results.  The interface presented by the spi::ServiceProviderRegistry
///     depends upon the derivation of its ProviderT template argument.
///
///     The ProviderT template argument must be a class derived from
///     spi::ServiceProvider.
///
///     When ProviderT is not derived from spi::StrategyServiceProvider,
///     StrategyT will be void.  When StrategyT is not void, a 2-parameter
///     create member function that accepts a (possibly NULL) StrategyConstPtr
///     will be available.
///
///     When ProviderT is not derived from spi::InteractiveServiceProvider,
///     CallbackT will be void.  When CallbackT is not void, a 2-parameter
///     create member function that accepts a (possibly NULL)
///     StrategyProviderCallback will be available.  An isSupported member
///     function will also be available.
///
///     When both StrategyT and CallbackT are non-void, a 3-parameter create
///     member function that accepts a (possibly NULL) StrategyConstPtr and
///     (possibly NULL) StrategyProviderCallback will be available and the
///     isSupported member function gains an optional StrategyConstPtr parameter.
///
///     Partial specializations of spi::ServiceProviderRegistry for combinations
///     of void StrategyT and CallbackT are defined below.
///
///=============================================================================


template <class ProviderT,
          class StrategyT = typename ProviderT::StrategyType,
          class CallbackT = typename ProviderT::CallbackType>
class ServiceProviderRegistry
  : public detail::StrategyProviderRegistry<ProviderT, StrategyT>
  {
    typedef detail::StrategyProviderRegistry<ProviderT, StrategyT>
            BaseType;


                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename BaseType::InputType;
    using typename BaseType::ProviderType;
    using typename BaseType::ResultType;
    using typename BaseType::StrategyType;

    typedef CallbackT                   CallbackType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ServiceProviderRegistry (bool allowDuplicateStrategies = false)
      : BaseType (allowDuplicateStrategies)
      { }

    ~ServiceProviderRegistry ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator, due to a non-copyable base class.  This is acceptable.
    //

    using BaseType::create;

    ResultType*
    create (const InputType& input,
            typename ProviderT::StrategyConstPtr hint,  // May be NULL.
            ServiceProviderCallback* cb)                // May be NULL.
        const
      {
        ResultType* result (NULL);
        TAK::Engine::Thread::ReadLockPtr lock(NULL, NULL);
        TAK::Engine::Thread::ReadLock_create(lock, this->getMutex());
        Range range (this->getStrategyRange (hint));

        while (!result && range.first != range.second
               && !(cb && cb->isCanceled ()))
          {
            result = range.first++->provider->create (input, hint, cb);
          }

        return result;
      }

    ResultType*
    create (const InputType& input,
            ServiceProviderCallback* cb)        // May be NULL.
        const
      { return create (input, NULL, cb); }

    bool
    isSupported (const InputType& input,
                 std::size_t limit,
                 typename ProviderT::StrategyConstPtr hint = nullptr)
        const
      {
        detail::ProbeOnlyCallback probe (limit);
        TAK::Engine::Thread::ReadLockPtr lock(NULL, NULL);
        TAK::Engine::Thread::ReadLock_create(lock, this->getMutex());
        Range range (this->getStrategyRange (hint));

        while (!probe.getProbeResult () && range.first != range.second)
          {
            range.first++->provider->create (input, hint, &probe);
          }

        return probe.getProbeResult ();
      }


    //==================================
    //  detail::ProviderRegistry INTERFACE
    //==================================


    ResultType*
    create (const InputType& input)
        const
      { return create (input, NULL, NULL); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED NESTED TYPES
    //==================================


    using typename BaseType::Range;
  };


///=============================================================================
///
///  class template spi::ServiceProviderRegistry<ProviderT, StrategyT, void>
///
///     Specialization of spi::ServiceProviderRegistry for ServiceProviders
///     that aren't derived from spi::InteractiveServiceProvider.
///
///=============================================================================


template <class ProviderT,
          class StrategyT>
class ServiceProviderRegistry<ProviderT, StrategyT, void>
  : public detail::StrategyProviderRegistry<ProviderT, StrategyT>
  {
    typedef detail::StrategyProviderRegistry<ProviderT, StrategyT>
            BaseType;


                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename BaseType::InputType;
    using typename BaseType::ProviderType;
    using typename BaseType::ResultType;
    using typename BaseType::StrategyType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ServiceProviderRegistry (bool allowDuplicateStrategies = false)
      : BaseType (allowDuplicateStrategies)
      { }

    ~ServiceProviderRegistry ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator, due to a non-copyable base class.  This is acceptable.
    //

    using BaseType::create;
  };


///=============================================================================
///
///  class template spi::ServiceProviderRegistry<ProviderT, void, CallbackT>
///
///     Specialization of spi::ServiceProviderRegistry for ServiceProviders
///     that aren't derived from spi::StrategyServiceProvider.
///
///=============================================================================


template <class ProviderT,
          class CallbackT>
class ServiceProviderRegistry<ProviderT, void, CallbackT>
  : public detail::ProviderRegistry<ProviderT>
  {
    typedef detail::ProviderRegistry<ProviderT>
            BaseType;


                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename BaseType::InputType;
    using typename BaseType::ProviderType;
    using typename BaseType::ResultType;

    typedef CallbackT                   CallbackType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~ServiceProviderRegistry ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator, due to a non-copyable base class.  This is acceptable.
    //

    using BaseType::create;

    ResultType*
    create (const InputType& input,
            ServiceProviderCallback* cb)        // May be NULL.
        const
      {
        ResultType* result (NULL);
        TAK::Engine::Thread::ReadLock lock (this->getMutex ());
        Range range (this->getProviderRange ());

        while (!result && range.first != range.second
               && !(cb && cb->isCanceled ()))
          {
            result = range.first++->provider->create (input, cb);
          }

        return result;
      }

    bool
    isSupported (const InputType& input,
                 std::size_t limit)
        const
      {
        detail::ProbeOnlyCallback probe (limit);
        TAK::Engine::Thread::ReadLock lock (this->getMutex ());
        Range range (this->getProviderRange ());

        while (!probe.getProbeResult () && range.first != range.second)
          {
            range.first++->provider->create (input, &probe);
          }

        return probe.getProbeResult ();
      }


    //==================================
    //  detail::ProviderRegistry INTERFACE
    //==================================


    ResultType*
    create (const InputType& input)
        const
      { return create (input, NULL); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED NESTED TYPES
    //==================================


    using typename BaseType::Range;
  };


///=============================================================================
///
///  class template spi::ServiceProviderRegistry<ProviderT, void, void>
///
///     Specialization of spi::ServiceProviderRegistry for ServiceProviders
///     that aren't derived from either spi::InteractiveServiceProvider or
///     spi::StrategyServiceProvider.
///
///=============================================================================


template <class ProviderT>
class ServiceProviderRegistry<ProviderT, void, void>
  : public detail::ProviderRegistry<ProviderT>
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~ServiceProviderRegistry ()
        NOTHROWS
      { }
  };


}                                       // Close spi namespace.
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

#endif  // #ifndef ATAKMAP_SRC_SPI_SERVICE_PROVIDER_REGISTRY_H_INCLUDED
