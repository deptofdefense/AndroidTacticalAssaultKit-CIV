////============================================================================
////
////    FILE:           ProxyLayer.h
////
////    DESCRIPTION:    Declaration of abstract class that acts as a proxy for a
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


#ifndef ATAKMAP_CORE_PROXY_LAYER_H_INCLUDED
#define ATAKMAP_CORE_PROXY_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/Layer.h"


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
///     Abstract class that acts as a proxy for a map layer.  The subject of the
///     proxy may change over the lifetime of the proxy.  Subject changes can be
///     monitored by registering a ProxyLayer::SubjectListener for callback
///     notifications.
///
///=============================================================================


class ProxyLayer
    : public virtual Layer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Public nested types.
    //

    class SubjectListener;

    //
    // Public member functions.
    //

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    //
    // Registers the supplied SubjectListener for notifications when the subject
    // of the proxy layer changes.  Ignores a NULL SubjectListener.
    //
    virtual
    void
    addSubjectListener (SubjectListener*)
        = 0;

    //
    // Returns the (possibly NULL) current subject of the proxy layer.
    //
    virtual
    Layer*
    getSubject ()
        const
        = 0;

    //
    // Unregisters the supplied SubjectListener from notifications of proxy
    // layer subject changes.  Ignores a NULL SubjectListener.
    //
    virtual
    void
    removeSubjectListener (SubjectListener*)
        = 0;

    //
    // Sets a new subject for the layer.  Registered SubjectListeners are
    // notified if the subject of the proxy layer changes.
    //
    virtual
    void
    setSubject (Layer*)                 // May be NULL.
        = 0;
  };


///=========================================================================
///
///  class atakmap::core::ProxyLayer::SubjectListener
///
///     Abstract base class for proxy layer subject change callbacks.
///
///=========================================================================


class ProxyLayer::SubjectListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~SubjectListener ()
        NOTHROWS
      { };

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the subject of the supplied proxy layer has changed.
    //
    virtual
    void
    subjectChanged (ProxyLayer&)
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

#endif  // #ifndef ATAKMAP_CORE_PROXY_LAYER_H_INCLUDED
