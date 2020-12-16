////============================================================================
////
////    FILE:           AbstractRasterLayer.cpp
////
////    DESCRIPTION:    Implementation of AbstractRasterLayer class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 8, 2014   scott           Created.
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


#include "raster/AbstractRasterLayer.h"

#include <algorithm>

#include "raster/AutoSelectService.h"
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


using namespace atakmap::raster;


struct ProjectionNotifier
  : std::unary_function<RasterLayer::PreferredProjectionListener*, void>
  {
    ProjectionNotifier (RasterLayer* layer)
      : layer (layer)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (RasterLayer::PreferredProjectionListener* l)
        const
      { l->preferredProjectionChanged (*layer); }

    RasterLayer* layer;
  };


struct SelectionNotifier
  : std::unary_function<RasterLayer::SelectionListener*, void>
  {
    SelectionNotifier (RasterLayer* layer)
      : layer (layer)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (RasterLayer::SelectionListener* l)
        const
      { l->selectionChanged (*layer); }

    RasterLayer* layer;
  };


struct SelectionVisibilityNotifier
  : std::unary_function<RasterLayer::SelectionVisibilityListener*, void>
  {
    SelectionVisibilityNotifier (RasterLayer* layer)
      : layer (layer)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (RasterLayer::SelectionVisibilityListener* l)
        const
      { l->selectionVisibilityChanged (*layer); }

    RasterLayer* layer;
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
namespace raster                        // Open raster namespace.
{


void
AbstractRasterLayer::addPreferredProjectionListener
    (PreferredProjectionListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        projection_listeners_.insert (listener);
      }
  }


void
AbstractRasterLayer::addSelectionListener (SelectionListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        selection_listeners_.insert (listener);
      }
  }


void
AbstractRasterLayer::addSelectionVisibilityListener
    (SelectionVisibilityListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        selection_visibility_listeners_.insert (listener);
      }
  }


core::Projection*
AbstractRasterLayer::getPreferredProjection ()
    const
  {
    Lock lock(getMutex());

    return preferred_projection_;
  }


const char*
AbstractRasterLayer::getSelection ()
    const
  {
    const char* result = nullptr;
    Lock lock(getMutex());

    if (isAutoSelect ())
      {
        auto* autoSelect
            = static_cast<AutoSelectService*>
                  (getService (AutoSelectService::SERVICE_TYPE));

        result = autoSelect ? autoSelect->getAutoSelectValue () : nullptr;
      }
    else
      {
        result = selection_;
      }

    return result;
  }


bool
AbstractRasterLayer::isAutoSelect ()
    const
  {
    Lock lock(getMutex());

    return static_cast<const char *>(selection_) == nullptr;
  }


bool
AbstractRasterLayer::isVisible (const char* selection)
    const
  {
    Lock lock(getMutex());

    return selection && !invisible_selections_.count (selection);
  }


void
AbstractRasterLayer::removePreferredProjectionListener
    (PreferredProjectionListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        projection_listeners_.erase (listener);
      }
  }


void
AbstractRasterLayer::removeSelectionListener (SelectionListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        selection_listeners_.erase (listener);
      }
  }


void
AbstractRasterLayer::removeSelectionVisibilityListener
    (SelectionVisibilityListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        selection_visibility_listeners_.erase (listener);
      }
  }


void
AbstractRasterLayer::setPreferredProjection (core::Projection* projection)
  {
    Lock lock(getMutex());
    core::Projection* savedProjection (preferred_projection_);

    setPreferredProjectionInternal (projection);
    if (savedProjection != preferred_projection_)
      {
        notifyProjectionListeners ();
      }
  }


void
AbstractRasterLayer::setSelection (const char* type) // Defaults to NULL.
  {
    Lock lock(getMutex());
    TAK::Engine::Port::String savedSelection (selection_);

    setSelectionInternal (type);
    if (TAK::Engine::Port::String_equal(selection_, savedSelection))
      {
        notifySelectionListeners ();
      }
  }


void
AbstractRasterLayer::setSelectionVisibility (const char* type,
                                             bool visible)
  {
    Lock lock(getMutex());

    if (type
        && (visible
                ? invisible_selections_.erase (type)
                : invisible_selections_.insert (type).second))
      {
        notifySelectionVisibilityListeners ();
      }
  }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


AbstractRasterLayer::AbstractRasterLayer (const char* name)
  : AbstractLayer (name),
    ServiceManagerImpl (getMutex ()),   // Use mutex from AbstractLayer.
    preferred_projection_ (nullptr)
  { }

void
AbstractRasterLayer::notifyProjectionListeners ()
    const
  {
    //
    // Work with a copy of the set of listeners, in case a listener unregisters
    // itself during the callback, thereby invalidating the iterator.
    //

    Lock lock(getMutex());
    std::set<PreferredProjectionListener*> listenersCopy (projection_listeners_);

    std::for_each (listenersCopy.begin (),
                   listenersCopy.end (),
                   ProjectionNotifier (const_cast<AbstractRasterLayer*> (this)));
  }


void
AbstractRasterLayer::notifySelectionListeners ()
    const
  {
    //
    // Work with a copy of the set of listeners, in case a listener unregisters
    // itself during the callback, thereby invalidating the iterator.
    //

    Lock lock(getMutex());
    std::set<SelectionListener*> listenersCopy (selection_listeners_);

    std::for_each (listenersCopy.begin (),
                   listenersCopy.end (),
                   SelectionNotifier (const_cast<AbstractRasterLayer*> (this)));
  }


void
AbstractRasterLayer::notifySelectionVisibilityListeners ()
    const
  {
    //
    // Work with a copy of the set of listeners, in case a listener unregisters
    // itself during the callback, thereby invalidating the iterator.
    //

    Lock lock(getMutex());
    std::set<SelectionVisibilityListener*> listenersCopy
        (selection_visibility_listeners_);

    std::for_each (listenersCopy.begin (),
                   listenersCopy.end (),
                   SelectionVisibilityNotifier
                       (const_cast<AbstractRasterLayer*> (this)));
  }


void
AbstractRasterLayer::setPreferredProjectionInternal (core::Projection* proj)
  { preferred_projection_ = proj; }


void
AbstractRasterLayer::setSelectionInternal (const char* type)
  { selection_ = type; }


}                                       // Close raster namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
