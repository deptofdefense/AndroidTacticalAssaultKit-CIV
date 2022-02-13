////============================================================================
////
////    FILE:           ServiceProviderRegistryDetails.h
////
////    DESCRIPTION:    Definition of implementation details for
////                    ServiceProviderRegistry class templates.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jun 18, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_SRC_SPI_SERVICE_PROVIDER_REGISTRY_DETAILS_H_INCLUDED
#define ATAKMAP_SRC_SPI_SERVICE_PROVIDER_REGISTRY_DETAILS_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <cstddef>
#include <functional>
#include <set>
#include <utility>

#include "port/String.h"
#include "spi/ServiceProvider.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "thread/RWMutex.h"


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
namespace detail                        // Open implementation detail namespace.
{


///=============================================================================
///
///  class template detail::Wrapper<ProviderT>
///
///     Helper class that associates a 1-up number with a registered
///     ServiceProvider.  Used by detail::ProviderRegistry, et. al..
///
///=============================================================================


template <class ProviderT>
struct Wrapper
  {
    typedef ProviderT                   ProviderType;

    Wrapper (const ProviderT* provider,
             std::size_t order)
      : provider (provider),
        order (order)
      { }

    template <class DerivedProviderT>
    Wrapper (const Wrapper<DerivedProviderT>& rhs)
      : provider (rhs.provider),
        order (rhs.order)
      { }

    const ProviderT* const provider;
    const std::size_t order;
  };


///=============================================================================
///
///  class template detail::Comparator<ProviderT>
///
///     Binary functor for ordering wrapped ServiceProviders.  Default ordering
///     is LIFO by registration order.  Used by detail::ProviderRegistry, et. al..
///
///=============================================================================


template <class ProviderT>
struct Comparator
  : std::binary_function<Wrapper<ProviderT>, Wrapper<ProviderT>, bool>
  {
    bool
    operator() (const Wrapper<ProviderT>& lhs,
                const Wrapper<ProviderT>& rhs)
        const
      { return lhs.order > rhs.order; } // LIFO ordering.
  };


///=============================================================================
///
///  class template detail::Comparator<InteractiveServiceProvider<ProviderT,
///                                                               StrategyT> >
///
///     Partial specialization of Comparator for InteractiveServiceProviders.
///     Redirects to Comparator for the next wrapped ProviderType.
///
///=============================================================================


template <class ProviderT,
          class StrategyT>
struct Comparator<InteractiveServiceProvider<ProviderT, StrategyT> >
  : std::binary_function<Wrapper<InteractiveServiceProvider
                                     <ProviderT, StrategyT> >,
                         Wrapper<InteractiveServiceProvider
                                     <ProviderT, StrategyT> >,
                         bool>
  {
    typedef Wrapper<InteractiveServiceProvider<ProviderT, StrategyT> >
            WrapperType;

    bool
    operator() (const WrapperType& lhs,
                const WrapperType& rhs)
        const
      { return Comparator<typename ProviderT::ProviderType> () (lhs, rhs); }
  };


///=============================================================================
///
///  class template detail::Comparator<PriorityServiceProvider<ProviderT> >
///
///     Partial specialization of Comparator for PriorityServiceProviders.
///     Orders by priority (higher values before lower values), using
///     Comparator for the next wrapped ProviderType to break priority ties.
///
///=============================================================================


template <class ProviderT>
struct Comparator<PriorityServiceProvider<ProviderT> >
  : std::binary_function<Wrapper<PriorityServiceProvider<ProviderT> >,
                         Wrapper<PriorityServiceProvider<ProviderT> >,
                         bool>
  {
    typedef Wrapper<PriorityServiceProvider<ProviderT> >
            WrapperType;

    bool
    operator() (const WrapperType& lhs,
                const WrapperType& rhs)
        const
      {
        unsigned int lhsPriority (lhs.provider->getPriority ());
        unsigned int rhsPriority (rhs.provider->getPriority ());

        return lhsPriority > rhsPriority
            || (lhsPriority == rhsPriority
                && Comparator<typename ProviderT::ProviderType> () (lhs, rhs));
      }
  };


///=============================================================================
///
///  class template detail::Comparator<StrategyServiceProvider<ProviderT,
///                                                            StrategyT,
///                                                            CallbackT> >
///
///     Partial specialization of Comparator for StrategyServiceProviders.
///     Orders by StrategyT (using natural ordering), using Comparator for the
///     next wrapped ProviderType to break priority ties.
///
///=============================================================================


template <class ProviderT,
          class StrategyT,
          class CallbackT>
struct Comparator<StrategyServiceProvider<ProviderT, StrategyT, CallbackT> >
  : std::binary_function<Wrapper<StrategyServiceProvider
                                     <ProviderT, StrategyT, CallbackT> >,
                         Wrapper<StrategyServiceProvider
                                     <ProviderT, StrategyT, CallbackT> >,
                         bool>
  {
    typedef Wrapper<StrategyServiceProvider<ProviderT, StrategyT, CallbackT> >
            WrapperType;

    bool
    operator() (const WrapperType& lhs,
                const WrapperType& rhs)
        const
      {
        StrategyT lhsType (lhs.provider->getStrategy ());
        StrategyT rhsType (rhs.provider->getStrategy ());

        return lhsType < rhsType
            || (lhsType == rhsType
                && Comparator<typename ProviderT::ProviderType> () (lhs, rhs));
      }
  };


///=============================================================================
///
///  class template detail::Comparator<StrategyServiceProvider<ProviderT,
///                                                            const char*> >
///
///     Partial specialization of Comparator for StrategyServiceProviders that
///     have a const char* strategy type.  Orders by strcmp, using Comparator
///     for the next wrapped ProviderType to break priority ties.
///
///=============================================================================


template <class ProviderT>
struct Comparator<StrategyServiceProvider<ProviderT, const char*> >
  : std::binary_function<Wrapper<StrategyServiceProvider<ProviderT,
                                                         const char*> >,
                         Wrapper<StrategyServiceProvider<ProviderT,
                                                         const char*> >,
                         bool>
  {
    typedef Wrapper<StrategyServiceProvider<ProviderT, const char*> >
            WrapperType;

    bool
    operator() (const WrapperType& lhs,
                const WrapperType& rhs)
        const
      {
        int cmpResult (TAK::Engine::Port::String_strcmp (lhs.provider->getStrategy (),
                                             rhs.provider->getStrategy ()));

        return cmpResult < 0
            || (cmpResult == 0
                && Comparator<typename ProviderT::ProviderType> () (lhs, rhs));
      }
  };


///=============================================================================
///
///  class template detail::ProviderRegistry<ProviderT>
///
///     Base registry for ServiceProviders and factory for ServiceProvider
///     results.
///
///=============================================================================


template <class ProviderT>
class ProviderRegistry
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef typename ProviderT::ProviderType    ProviderType;
    typedef typename ProviderType::ResultType   ResultType;
    typedef typename ProviderType::InputType    InputType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ProviderRegistry ()
      : order_ (0)
      { }

    virtual
    ~ProviderRegistry ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator, due to a non-copyable data member.  This is acceptable.
    //

    ResultType*
    create (const InputType& input)
        const
      {
        ResultType* result (NULL);
        TAK::Engine::Thread::ReadLockPtr lock(NULL, NULL);
        TAK::Engine::Thread::ReadLock_create(lock, mutex_);
        Range range (getProviderRange ());

        while (!result && range.first != range.second)
          {
            result = range.first++->provider->create (input);
          }

        return result;
      }

    void
    registerProvider (const ProviderT* provider)
      {
        if (provider)
          {
              TAK::Engine::Thread::WriteLockPtr lock(NULL, NULL);
              TAK::Engine::Thread::WriteLock_create(lock, mutex_);

            if (findProvider (provider) == providers.end ())
              {
                registerProviderImpl (provider);
              }
          }
      }

    void
    unregisterProvider (const ProviderT* provider)
      {
        if (provider)
          {
            TAK::Engine::Thread::WriteLock lock(mutex_);
            typename ProviderSet::const_iterator iter (findProvider (provider));

            if (iter != providers.end ())
              {
                unregisterProviderImpl (iter);
              }
          }
      }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED NESTED TYPES
    //==================================


    typedef Wrapper<ProviderT>          WrapperType;
    typedef std::set<WrapperType, Comparator<ProviderType> >
            ProviderSet;
    typedef std::pair<typename ProviderSet::const_iterator,
                      typename ProviderSet::const_iterator>
            Range;


    //==================================
    //  PROTECTED INTERFACE
    //==================================

    //
    // Returns an iterator to the supplied ServiceProvider (or ProviderSet::end).
    // Must be called with the RW_Mutex either ReadLocked or WriteLocked.
    //
    typename ProviderSet::const_iterator
    findProvider (const ProviderT* provider)
        const
      {
        return std::find_if (providers.begin (), providers.end (),
                             WrapsProvider (provider));
      }

    TAK::Engine::Thread::RWMutex&
    getMutex ()
        const
        NOTHROWS
      { return mutex_; }

    std::size_t
    getOrder ()
        const
        NOTHROWS
      { return order_; }

    //
    // Must be called with the RW_Mutex either ReadLocked or WriteLocked.
    //
    Range
    getProviderRange ()
        const
      { return Range (providers.begin (), providers.end ()); }

    const ProviderSet&
    getProviders ()
        const
        NOTHROWS
      { return providers; }

    ProviderSet&
    getProviders ()
        NOTHROWS
      { return providers; }

    //
    // Registers the supplied (non-NULL) ServiceProvider (which is known not to
    // be currently registered).  Must be called with the RW_Mutex WriteLocked.
    //
    virtual
    void
    registerProviderImpl (const ProviderT* provider)    // Never NULL.
      { providers.insert (WrapperType (provider, order_++)); }

    //
    // Unregisters the wrapped ServiceProvider referred to by the supplied
    // (dereferenceable) iterator.  Must be called with the RW_Mutex WriteLocked.
    //
    virtual
    void
    unregisterProviderImpl (typename ProviderSet::const_iterator iter)
      { providers.erase (iter); }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================


    struct WrapsProvider
      : std::unary_function<WrapperType, bool>
      {
        WrapsProvider (const ProviderT* provider)
          : provider (provider)
          { }

        bool
        operator() (const WrapperType& wrapper)
            const
          { return wrapper.provider == provider; }

        const ProviderT* const provider;
      };


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    mutable TAK::Engine::Thread::RWMutex mutex_;
    std::size_t order_;                  // One-up number for registrations.
    ProviderSet providers;
  };


///=============================================================================
///
///  class template detail::StrategyProviderImpl<ProviderT, StrategyT, CallbackT>
///
///     Internal StrategyServiceProvider implementation used to find the subset
///     of registered StrategyServiceProviders for a given strategy in a
///     detail::StrategyProviderRegistry.  The default implementation handles
///     StrategyServiceProviders that are also InteractiveServiceProviders
///     (i.e., CallbackT is not void).
///
///=============================================================================


template <class ProviderT,
          class StrategyT = typename ProviderT::StrategyType,
          class CallbackT = typename ProviderT::CallbackType>
class StrategyProviderImpl
  : public ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    StrategyProviderImpl (typename StrategyTraits<StrategyT>::ArgType strategy,
                          unsigned int priority)
      : strategy (strategy),
        priority (priority)
      { }

    ~StrategyProviderImpl ()
        NOTHROWS
      { }

    using ProviderT::create;


    typename ProviderT::ResultType*
    create (const typename ProviderT::InputType&,
            typename ProviderT::StrategyConstPtr,
            CallbackT*)
        const
      { return NULL; }


    //==================================
    //  InteractiveServiceProvider INTERFACE
    //==================================


    typename ProviderT::ResultType*
    create (const typename ProviderT::InputType&,
            CallbackT*)
        const
      { return NULL; }


    //==================================
    //  PriorityServiceProvider INTERFACE
    //==================================


    unsigned int
    getPriority ()
        const
        NOTHROWS
      { return priority; }


    //==================================
    //  StrategyServiceProvider INTERFACE
    //==================================


    typename ProviderT::ResultType*
    create (const typename ProviderT::InputType&,
            typename ProviderT::StrategyConstPtr)
        const
      { return NULL; }

    StrategyT
    getStrategy ()
        const
        NOTHROWS
      { return strategy; }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    StrategyT strategy;
    unsigned int priority;
  };


///=============================================================================
///
///  class template detail::StrategyProviderImpl<ProviderT, StrategyT, void>
///
///     Partial specialization of detail::StrategyProviderImpl for
///     StrategyServiceProviders that are not also InteractiveServiceProviders
///     (i.e., CallbackT == void).
///
///=============================================================================


template <class ProviderT,
          class StrategyT>
class StrategyProviderImpl<ProviderT, StrategyT, void>
  : public ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    StrategyProviderImpl (typename StrategyTraits<StrategyT>::ArgType strategy,
                          unsigned int priority)
      : strategy (strategy),
        priority (priority)
      { }

    ~StrategyProviderImpl ()
        NOTHROWS
      { }

    using ProviderT::create;


    //==================================
    //  PriorityServiceProvider INTERFACE
    //==================================


    unsigned int
    getPriority ()
        const
        NOTHROWS
      { return priority; }


    //==================================
    //  StrategyServiceProvider INTERFACE
    //==================================


    typename ProviderT::ResultType*
    create (const typename ProviderT::InputType&,
            typename ProviderT::StrategyConstPtr)
        const
      { return NULL; }

    StrategyT
    getStrategy ()
        const
        NOTHROWS
      { return strategy; }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    StrategyT strategy;
    unsigned int priority;
  };


///=============================================================================
///
///  class template detail::StrategyProviderRegistry<ProviderT, StrategyT>
///
///     Internal detail::ProviderRegistry implementation for ServiceProviders
///     that are derived from spi::StrategyServiceProvider.
///
///=============================================================================


template <class ProviderT,
          class StrategyT = typename ProviderT::StrategyType>
class StrategyProviderRegistry
  : public ProviderRegistry<ProviderT>
  {
    typedef ProviderRegistry<ProviderT> BaseType;


                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename BaseType::InputType;
    using typename BaseType::ProviderType;
    using typename BaseType::ResultType;

    typedef typename ProviderT::StrategyType
            StrategyType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    StrategyProviderRegistry (bool allowDuplicateStrategies = false)
      : allowDuplicates (allowDuplicateStrategies)
      { }

    ~StrategyProviderRegistry ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator, due to a non-copyable base class.  This is acceptable.
    //

    using BaseType::create;

    ResultType*
    create (const InputType& input,
            typename ProviderT::StrategyConstPtr hint) // May be NULL.
        const
      {
        ResultType* result (NULL);
        TAK::Engine::Thread::ReadLock lock (this->getMutex ());
        Range range (getStrategyRange (hint));

        while (!result && range.first != range.second)
          {
            result = range.first++->provider->create (input, hint);
          }

        return result;
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


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    Range
    getStrategyRange (typename ProviderT::StrategyConstPtr hint)
        const
      {
        return hint
            ? getStrategyRangeImpl (StrategyTraits<StrategyType>::deref (hint))
            : this->getProviderRange ();
      }


    //==================================
    //  detail::ProviderRegistry INTERFACE
    //==================================


    void
    registerProviderImpl (const ProviderT* provider)    // Never NULL.
      {
        if (!allowDuplicates)
          {
            Range range (getStrategyRangeImpl (provider->getStrategy ()));

            this->getProviders ().erase (range.first, range.second);
          }
        BaseType::registerProviderImpl (provider);
      }


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    Range
    getStrategyRangeImpl (typename StrategyTraits<StrategyType>::ArgType hint)
        const
      {
        typedef typename ProviderT::ProviderType PType;

        StrategyProviderImpl<PType, StrategyType> lowerImpl (hint, -1);
        StrategyProviderImpl<PType, StrategyType> upperImpl (hint, 0);
        Wrapper<PType> lower (&lowerImpl, this->getOrder ());
        Wrapper<PType> upper (&upperImpl, 0);
        Comparator<PType> cmp;
        Range range (this->getProviderRange ());

        return Range (std::lower_bound (range.first, range.second, lower, cmp),
                      std::upper_bound (range.first, range.second, upper, cmp));
      }


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    bool allowDuplicates;
  };


///=============================================================================
///
///  class detail::ProbeOnlyCallback
///
///     Internal probe-only implementation of spi::ServiceProviderCallback used
///     in the implementation of the isSupported member function of
///     spi::ServiceProviderRegistry specializations that support
///     spi::InteractiveServiceProviders.
///
///=============================================================================


class ProbeOnlyCallback
  : public ServiceProviderCallback
  {
                                        //====================================//
  public:                               //                  PUBLIC            //
                                        //====================================//


    ProbeOnlyCallback (std::size_t limit)
      : limit (limit),
        success (false)
      { }

    ~ProbeOnlyCallback ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    bool
    getProbeResult ()
        const
      { return success; }


    //==================================
    //  ServiceProviderCallback INTERFACE
    //==================================


    std::size_t
    getProbeLimit ()
        const
        NOTHROWS override
      { return limit; }

    bool
    isCanceled ()
        const override
      { return false; }

    bool
    isProbeOnly ()
        const
        NOTHROWS override
      { return true; }

    void
    setError (const char* message) override
      { }

    void
    setProbeResult (bool match) override
      { success = match; }

    void
    setProgress (int progess) override
      { }


                                        //====================================//
  private:                              //                  PRIVATE           //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::size_t limit;
    bool success;
  };


}                                       // Close detail namespace.
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

#endif  // #ifndef ATAKMAP_SRC_SPI_SERVICE_PROVIDER_REGISTRY_DETAILS_H_INCLUDED
