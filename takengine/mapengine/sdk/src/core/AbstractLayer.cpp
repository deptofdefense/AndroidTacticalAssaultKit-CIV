////============================================================================
////
////    FILE:           AbstractLayer.cpp
////
////    DESCRIPTION:    Implementation of AbstractLayer class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Nov 19, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/AbstractLayer.h"

#include <algorithm>
#include <functional>
#include <stdexcept>

#include "thread/Lock.h"


#define MEM_FN( fn )    "atakmap::core::AbstractLayer::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap::core;

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


namespace                               // Open unnamed namespace.
{


struct Notifier
  : std::unary_function<Layer::VisibilityListener*, void>
  {
    Notifier (Layer* layer)
      : layer (layer)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (Layer::VisibilityListener* l)
        const
      { l->visibilityChanged (*layer); }

    Layer* layer;
  };


}                                       // Close unnamed namespace.


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
namespace core                              // Open core namespace.
{


void
AbstractLayer::addVisibilityListener (VisibilityListener* listener)
  {
    if (listener)
      {
        Lock lock(mutex_);

        listeners_.insert (listener);
      }
  }


bool
AbstractLayer::isVisible ()
    const
  {
    Lock lock(mutex_);

    return visible_;
  }


void
AbstractLayer::removeVisibilityListener (VisibilityListener* listener)
  {
    if (listener)
      {
        Lock lock(mutex_);

        listeners_.erase (listener);
      }
  }


void
AbstractLayer::setVisible (bool visibility)
  {
    Lock lock(mutex_);
    bool changed = visible_ != visibility;

    visible_ = visibility;
    if (changed)
      {
        notifyVisibilityListeners ();
      }
  }


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


AbstractLayer::AbstractLayer (const char* name)
  : name_ (name),
    mutex_ (TEMT_Recursive),
    visible_ (true)
  {
    if (!name)
      {
        throw std::invalid_argument (MEM_FN ("AbstractLayer")
                                     "Received NULL name");
      }
  }


void
AbstractLayer::notifyVisibilityListeners ()
    const
  {
    //
    // Work with a copy of the set of listeners, in case a listener unregisters
    // itself during the callback, thereby invalidating the iterator.
    //

    Lock lock(mutex_);
    std::set<VisibilityListener*> listenersCopy (listeners_);

    std::for_each (listenersCopy.begin (),
                   listenersCopy.end (),
                   Notifier (const_cast<AbstractLayer*> (this)));
  }


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
