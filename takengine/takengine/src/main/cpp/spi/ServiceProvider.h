////============================================================================
////
////    FILE:           ServiceProvider.h
////
////    DESCRIPTION:    Definition of ServiceProvider class templates.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      May 18, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_SRC_SPI_SERVICE_PROVIDER_H_INCLUDED
#define ATAKMAP_SRC_SPI_SERVICE_PROVIDER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "port/Platform.h"
#include <cstddef>


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
///  class template spi::ServiceProvider<ResultT, InputT>
///
///     Abstract ServiceProvider that can produce ResultT objects from InputT
///     objects.  This is the type upon which all other spi::ServiceProviders
///     are based.
///
///=============================================================================


template <class ResultT,
          class InputT>
class ServiceProvider
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef InputT                      InputType;
    typedef ResultT                     ResultType;

    //
    // These types are used by derived spi::ServiceProvider class templates and
    // the spi::ServiceProviderRegistry class template.  They are either
    // forwarded (by using declarations) or overridden by derived abstract
    // spi::ServiceProvider class templates.
    //
    typedef void                        StrategyType;
    typedef void                        CallbackType;

    //
    // This type is used by the spi::ServiceProviderRegistry class template.  It
    // represents the most derived abstract spi::ServiceProvider class template
    // and is overridden by the derived abstract spi::ServiceProvider class
    // templates.
    //
    typedef ServiceProvider<ResultT, InputT>
            ProviderType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~ServiceProvider ()
        NOTHROWS
        = 0;

    //
    // Returns a ResultType created for the supplied InputType.  Returns NULL if
    // no result can be created for the supplied InputType.
    //
    virtual
    ResultType*
    create (const InputType&)
        const
        = 0;
  };


class ServiceProviderCallback
  {
                                    //====================================//
  public:                           //                      PUBLIC        //
                                    //====================================//


    virtual
    ~ServiceProviderCallback ()
        NOTHROWS
        = 0;

    //
    // Returns the probe limit value. The interpretation of the value is
    // specific to the class of InteractiveServiceProvider implementation.
    // Example interpretations could be the number of bytes in a file or the
    // number of files in a directory.
    //
    virtual
    std::size_t
    getProbeLimit ()
        const
        NOTHROWS
        = 0;

    //
    // Allows the client an asynchronous method to cancel processing.  Not all
    // InteractiveServiceProvider implementations may support canceling; those
    // that do may not be able to cancel immediately.  Returns true if
    // processing should be canceled, false otherwise.
    //
    virtual
    bool
    isCanceled ()
        const
        = 0;

    //
    // Returns true if the InteractiveServiceProvider should only analyze the
    // input data to see if an output *may* be produced.  The provider will
    // analyze the input and notify the callback of the result via a call to
    // setProbeResult.  Returns true if the provider should only probe the
    // input, false if it should attempt to produce an output.
    //
    virtual
    bool
    isProbeOnly ()
        const
        NOTHROWS
        = 0;

    //
    // This member function is invoked when an error occurs during processing of
    // an input.  When this method is called, the create member function will
    // return NULL.
    //
    virtual
    void
    setError (const char* message)
        = 0;

    //
    // If isProbeOnly returns true, the provider will call this member function
    // once it determines whether or not it *may* be able to process the input
    // data.  If isProbeOnly returns false, thie member function will never be
    // called.
    //
    // Returns true if the provider *may* be able to produce an output value for
    // the supplied input value.  Returns false if no output value can be
    // created from the input value.
    //
    virtual
    void
    setProbeResult (bool match)
        = 0;

    //
    // This member function is invoked during processing to indicate progress.
    // The interpretation of the progress value is specific to the class of
    // InteractiveServiceProvider implementation.
    //
    virtual
    void
    setProgress (int progess)
        = 0;
  };


namespace detail                        // Open implementation detail namespace.
{


template <class StrategyT>
struct StrategyTraits
  {
    typedef const StrategyT&    ArgType;
    typedef const StrategyT*    ConstPtrType;
    static ArgType deref (ConstPtrType p) { return *p; }
  };


template <class StrategyT>
struct StrategyTraits<StrategyT*>       // Support for const char*, et. al.
  {
    typedef const StrategyT*    ArgType;
    typedef const StrategyT*    ConstPtrType;
    static ArgType deref (ConstPtrType p) { return p; }
  };


}                                       // Close detail namespace.


///=============================================================================
///
///  class template spi::InteractiveServiceProvider<ProviderT, StrategyT>
///
///     An spi::InteractiveServiceProvider accepts an optional
///     spi::ServiceProviderCallback when creating a ResultType from an
///     InputType.
///
///     The ProviderT template argument must be a class derived from
///     spi::ServiceProvider.
///
///     When ProviderT is derived from spi::StrategyServiceProvider,
///     ProviderT::StrategyType will be non-void and any class implementing this
///     interface must implement the 3-parameter create member function.
///
///     A partial specialization of spi::InteractiveServiceProvider for a
///     ProviderT not derived from spi::StrategyServiceProvider (i.e.,
///     StrategyT == void) is defined below.
///
///=============================================================================


template <class ProviderT,
          class StrategyT = typename ProviderT::StrategyType>
class InteractiveServiceProvider
  : public virtual ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename ProviderT::InputType;
    using typename ProviderT::ResultType;
    using typename ProviderT::StrategyConstPtr;
    using typename ProviderT::StrategyType;

    typedef ServiceProviderCallback     CallbackType;
    typedef InteractiveServiceProvider<ProviderT>
            ProviderType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    using ProviderT::create;

    //
    // Returns a ResultType created for the supplied InputType.  Returns NULL if
    // no result can be created for the supplied InputType or if the (possibly
    // NULL) supplied StrategyConstPtr is not supported.
    //
    // If the supplied CallbackType is not NULL, creation progress and errors
    // are reported to the callback.  If the callback's isProbeOnly member
    // function returns true, the supplied InputType is probed, up to the limit
    // returned by the callback's getProbeLimit member function, to determine
    // whether it might be possible to create a ResultType.  The probe result is
    // reported by a call to the callback's setProbeResult member function and
    // this member function returns NULL.  (See ServiceProviderCallback.)
    //
    virtual
    ResultType*
    create (const InputType&,
            StrategyConstPtr,           // May be NULL.
            CallbackType*)              // May be NULL.
        const
        = 0;

    ResultType*
    create (const InputType& input,
            CallbackType* cb)           // May be NULL.
        const
      { return create (input, NULL, cb); }


    //==================================
    //  ServiceProvider INTERFACE
    //==================================


    ResultType*
    create (const InputType& input)
        const override
      { return create (input, NULL, NULL); }
  };


///=============================================================================
///
///  class template spi::InteractiveServiceProvider<ProviderT, void>
///
///     Partial specialization of spi::InteractiveServiceProviders for a
///     ProviderT that is not derived from spi::StrategyServiceProvider.  Any
///     class implementing this interface must implement the 2-parameter create
///     member function.
///
///=============================================================================


template <class ProviderT>
class InteractiveServiceProvider<ProviderT, void>
  : public virtual ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename ProviderT::InputType;
    using typename ProviderT::ResultType;
    using typename ProviderT::StrategyType;

    typedef ServiceProviderCallback     CallbackType;
    typedef InteractiveServiceProvider<ProviderT>
            ProviderType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    using ProviderT::create;

    //
    // Returns a ResultType created for the supplied InputType.  Returns NULL if
    // no result can be created for the supplied InputType.
    //
    // If the supplied CallbackType is not NULL, creation progress and errors
    // are reported to the callback.  If the callback's isProbeOnly member
    // function returns true, the supplied InputType is probed, up to the limit
    // returned by the callback's getProbeLimit member function, to determine
    // whether it might be possible to create a ResultType.  The probe result is
    // reported by a call to the callback's setProbeResult member function and
    // this member function returns NULL.  (See ServiceProviderCallback.)
    //
    virtual
    ResultType*
    create (const InputType&,
            CallbackType*)              // May be NULL.
        const
        = 0;


    //==================================
    //  ServiceProvider INTERFACE
    //==================================


    ResultType*
    create (const InputType& input)
        const
      { return create (input, static_cast<CallbackType*> (NULL)); }
  };


///=============================================================================
///
///  class template spi::PriorityServiceProvider<ProviderT>
///
///     Abstract spi::ServiceProvider that can can be selected by an
///     spi::ServiceProviderRegistry based on priority.  The lowest priority is
///     0.  spi::PriorityServiceProviders with higher priority values are
///     selected before those with lower priority values.  Ties in priority are
///     settled by comparing the ProviderT base type.
///
///     The ProviderT template argument must be a class derived from
///     spi::ServiceProvider.
///
///=============================================================================


template <class ProviderT>
class PriorityServiceProvider
  : public virtual ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename ProviderT::CallbackType;
    using typename ProviderT::InputType;
    using typename ProviderT::ResultType;
    using typename ProviderT::StrategyType;

    typedef PriorityServiceProvider<ProviderT>
            ProviderType;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    unsigned int
    getPriority ()                      // Higher value denotes higher priority.
        const
        NOTHROWS
        = 0;
  };


///=============================================================================
///
///  class template spi::StrategyServiceProvider<ProviderT, StrategyT>
///
///     Abstract spi::ServiceProvider that can can be selected by an
///     spi::ServiceProviderRegistry based on a strategy value.  Strategy values
///     are compared by natural ordering (i.e., std::less).
///
///     The ProviderT template argument must be a class derived from
///     spi::ServiceProvider.
///
///     When ProviderT is derived from spi::InteractiveServiceProvider,
///     ProviderT::CallbackType will be non-void and any class implementing this
///     interface must implement the 3-parameter create member function.
///
///     A partial specialization of spi::StrategyServiceProvider for a ProviderT
///     not derived from spi::InteractiveServiceProvider (i.e.,
///     CallbackT == void) is defined below.
///
///=============================================================================


template <class ProviderT,
          class StrategyT,
          class CallbackT = typename ProviderT::CallbackType>
class StrategyServiceProvider
  : public virtual ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename ProviderT::CallbackType;
    using typename ProviderT::InputType;
    using typename ProviderT::ResultType;

    typedef StrategyT                   StrategyType;
    typedef StrategyServiceProvider<ProviderT, StrategyT>
            ProviderType;

    typedef typename detail::StrategyTraits<StrategyType>::ConstPtrType
            StrategyConstPtr;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    using ProviderT::create;

    //
    // Returns a ResultType created for the supplied InputType.  Returns NULL if
    // no result can be created for the supplied InputType or if the (possibly
    // NULL) supplied StrategyConstPtr is not supported.
    //
    // If the supplied CallbackType is not NULL, creation progress and errors
    // are reported to the callback.  If the callback's isProbeOnly member
    // function returns true, the supplied InputType is probed, up to the limit
    // returned by the callback's getProbeLimit member function, to determine
    // whether it might be possible to create a ResultType.  The probe result is
    // reported by a call to the callback's setProbeResult member function and
    // this member function returns NULL.  (See ServiceProviderCallback.)
    //
    virtual
    ResultType*
    create (const InputType&,
            StrategyConstPtr,           // May be NULL.
            CallbackType*)              // May be NULL.
        const
        = 0;

    ResultType*
    create (const InputType& input,
            StrategyConstPtr strategy)  // May be NULL.
        const
      { return create (input, strategy, NULL); }

    //
    // Returns the StrategyType supported by this service provider.
    //
    virtual
    StrategyType
    getStrategy ()
        const
        NOTHROWS
        = 0;


    //==================================
    //  ServiceProvider INTERFACE
    //==================================


    ResultType*
    create (const InputType& input)
        const
      { return create (input, NULL, NULL); }
  };


///=============================================================================
///
///  class template spi::StrategyServiceProvider<ProviderT, void>
///
///     Partial specialization of spi::StrategyServiceProviders for a
///     ProviderT that is not derived from spi::InteractiveServiceProvider.  Any
///     class implementing this interface must implement the 2-parameter create
///     member function.
///
///=============================================================================


template <class ProviderT,
          class StrategyT>
class StrategyServiceProvider<ProviderT, StrategyT, void>
  : public virtual ProviderT
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    using typename ProviderT::CallbackType;
    using typename ProviderT::InputType;
    using typename ProviderT::ResultType;

    typedef StrategyT                   StrategyType;
    typedef StrategyServiceProvider<ProviderT, StrategyT>
            ProviderType;

    typedef typename detail::StrategyTraits<StrategyType>::ConstPtrType
            StrategyConstPtr;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    using ProviderT::create;

    //
    // Returns a ResultType created for the supplied InputType.  Returns NULL if
    // no result can be created for the supplied InputType or if the (possibly
    // NULL) supplied StrategyType is not supported.
    //
    virtual
    ResultType*
    create (const InputType&,
            StrategyConstPtr)           // May be NULL.
        const
        = 0;

    //
    // Returns the StrategyType supported by this service provider.
    //
    virtual
    StrategyType
    getStrategy ()
        const
        NOTHROWS
        = 0;


    //==================================
    //  ServiceProvider INTERFACE
    //==================================


    ResultType*
    create (const InputType& input)
        const override
      { return create (input, static_cast<StrategyConstPtr> (NULL)); }
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


namespace atakmap                       // Open atakmap namespace.
{
namespace spi                           // Open spi namespace.
{


template <class ResultT,
          class InputT>
inline
ServiceProvider<ResultT, InputT>::~ServiceProvider ()
    NOTHROWS
  { }


inline
ServiceProviderCallback::~ServiceProviderCallback ()
    NOTHROWS
  { }


}                                       // Close spi namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_SRC_SPI_SERVICE_PROVIDER_H_INCLUDED
