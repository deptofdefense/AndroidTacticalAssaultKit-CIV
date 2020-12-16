////============================================================================
////
////    FILE:           AbstractLayer.h
////
////    DESCRIPTION:    Declaration of an abstract base implementation class for
////                    map layers.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Nov 18, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_CORE_ABSTRACT_LAYER_H_INCLUDED
#define ATAKMAP_CORE_ABSTRACT_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <set>
#include <string>

#include "core/Layer.h"
#include "port/Platform.h"
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


///=============================================================================
///
///  class atakmap::core::AbstractLayer
///
///     Abstract base implementation class for map layers.
///
///     This class is synchronized for all member functions (other than getName)
///     using a recursive mutex.  The mutex is locked during VisibilityListener
///     notifications.  A VisibilityListener may make calls to the Layer on the
///     notification's thread without blocking.
///
///=============================================================================


class ENGINE_API AbstractLayer
  : public virtual Layer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    ~AbstractLayer ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // data member).  This is acceptable.
    //


    //==================================
    //  Layer INTERFACE
    //==================================


    //
    // Registers the supplied VisibilityListener for notifications when the
    // visibility of the layer changes.  Ignores a NULL VisibilityListener.
    //
    void
    addVisibilityListener (VisibilityListener*);

    //
    // Returns the name of the layer.
    //
    const char*
    getName ()
        const
        NOTHROWS
      { return name_.c_str(); }

    //
    // Returns the current visibility of the layer.
    //
    bool
    isVisible ()
        const;

    //
    // Unregisters the supplied VisibilityListener from notifications of layer
    // visibility changes.  Ignores a NULL VisibilityListener.
    //
    void
    removeVisibilityListener (VisibilityListener*);

    //
    // Sets the visibility of the layer.  Registered VisibilityListeners are
    // notified if the visibility of the layer changes.
    //
    void
    setVisible (bool visibility);

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

    //
    // Constructs a AbstractLayer with the supplied name.
    //
    // Throws std::invalid_argument on NULL name.
    //
    AbstractLayer (const char* name);   // Must be non-NULL.

    TAK::Engine::Thread::Mutex&
    getMutex ()
        const
      { return mutex_; }

    //
    // Notifies VisibilityListeners of changed visibility.
    //
    void
    notifyVisibilityListeners ()
        const;

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    //
    // Private representation.
    //

    const std::string name_;
    mutable TAK::Engine::Thread::Mutex mutex_;
    bool visible_;
    std::set<VisibilityListener*> listeners_;
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

#endif  // #ifndef ATAK_CORE_ABSTRACT_LAYER_H_INCLUDED
