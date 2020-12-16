////============================================================================
////
////    FILE:           ProxyLayerImpl.cpp
////
////    DESCRIPTION:    Implementation of ProxyLayerImpl class.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/ProxyLayerImpl.h"

#include <algorithm>
#include <functional>

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


namespace                               // Open unnamed namespace.
{


using namespace atakmap::core;


struct Notifier
  : std::unary_function<ProxyLayer::SubjectListener*, void>
  {
    Notifier (ProxyLayer* layer)
      : layer (layer)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (ProxyLayer::SubjectListener* l)
        const
      { l->subjectChanged (*layer); }

    ProxyLayer* layer;
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
namespace core                          // Open core namespace.
{

ProxyLayerImpl::ProxyLayerImpl(const char* name,
    Layer* subject)
    : AbstractLayer(name),
    subject(subject)
{ }

void
ProxyLayerImpl::addSubjectListener (SubjectListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        listeners.insert (listener);
      }
  }


Layer*
ProxyLayerImpl::getSubject ()
    const
  {
    Lock lock(getMutex());

    return subject;
  }


void
ProxyLayerImpl::removeSubjectListener (SubjectListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        listeners.erase (listener);
      }
  }


void
ProxyLayerImpl::setSubject (Layer* newSubject)
  {
    Lock lock(getMutex());
    bool changed = subject != newSubject;

    subject = newSubject;
    if (changed)
      {
        notifySubjectListeners ();
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


void
ProxyLayerImpl::notifySubjectListeners ()
    const
  {
    //
    // Work with a copy of the set of listeners, in case a listener unregisters
    // itself during the callback, thereby invalidating the iterator.
    //

    Lock lock(getMutex());
    std::set<SubjectListener*> listenersCopy (listeners);

    std::for_each (listenersCopy.begin (),
                   listenersCopy.end (),
                   Notifier (const_cast<ProxyLayerImpl*> (this)));
  }


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
