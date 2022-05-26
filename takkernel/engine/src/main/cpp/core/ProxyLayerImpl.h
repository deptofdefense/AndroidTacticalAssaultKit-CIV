////============================================================================
////
////    FILE:           ProxyLayerImpl.h
////
////    DESCRIPTION:    Declaration of concrete class that acts as a proxy for a
////                    map layer.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Nov 24, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_CORE_PROXY_LAYER_IMPL_H_INCLUDED
#define ATAKMAP_CORE_PROXY_LAYER_IMPL_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <set>

#include "core/AbstractLayer.h"
#include "core/ProxyLayer.h"

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4250)
#endif

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


///=============================================================================
///
///  class atakmap::core::ProxyLayer
///
///     Concrete class that acts as a proxy for a map layer.  The subject of the
///     proxy may change over the lifetime of the proxy.  Subject changes can be
///     monitored by registering a ProxyLayer::SubjectListener for callback
///     notifications.
///
///=============================================================================


class ProxyLayerImpl
    : public ProxyLayer,
      public AbstractLayer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Constructs a ProxyLayerImpl with the supplied name and (possibly NULL)
    // layer.
    //
    // Throws std::invalid_argument on NULL name.
    //
    explicit
    ProxyLayerImpl (const char* name,   // Must be non-NULL.
                    Layer* subject = nullptr);

    ~ProxyLayerImpl ()
        NOTHROWS
      { }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    //
    // ProxyLayer member functions.
    //

    //
    // Registers the supplied SubjectListener for notifications when the subject
    // of the proxy layer changes.  Ignores a NULL SubjectListener.
    //
    void
    addSubjectListener (SubjectListener*);

    //
    // Returns the (possibly NULL) current subject of the proxy layer.
    //
    Layer*
    getSubject ()
        const;

    //
    // Unregisters the supplied SubjectListener from notifications of proxy
    // layer subject changes.  Ignores a NULL SubjectListener.
    //
    void
    removeSubjectListener (SubjectListener*);

    //
    // Sets a new subject for the layer.  Registered SubjectListeners are
    // notified if the subject of the proxy layer changes.
    //
    void
    setSubject (Layer*);                // May be NULL.

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

    //
    // Notifies SubjectListeners of changed subject.
    //
    void
    notifySubjectListeners ()
        const;

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    //
    // Private representation.
    //

    Layer* subject;
    std::set<SubjectListener*> listeners;
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
#ifdef _MSC_VER
#pragma warning(pop)
#endif
#endif  // #ifndef ATAKMAP_CORE_PROXY_LAYER_IMPL_H_INCLUDED
