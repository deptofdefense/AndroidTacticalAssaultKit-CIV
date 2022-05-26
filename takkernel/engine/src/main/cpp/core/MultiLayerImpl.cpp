////============================================================================
////
////    FILE:           MultiLayerImpl.cpp
////
////    DESCRIPTION:    Implementation of MultiLayerImpl class.
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


#include "core/MultiLayerImpl.h"

#include <algorithm>
#include <functional>
#include <stdexcept>

#include "thread/Lock.h"


#define MEM_FN( fn )    "atakmap::core::MultiLayerImpl::" fn ": "


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


struct AdditionNotifier
  : std::unary_function<MultiLayer::LayerListener*, void>
  {
    AdditionNotifier (MultiLayer* parent,
                      Layer* child)
      : parent (parent),
        child (child)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (MultiLayer::LayerListener* l)
        const
      { l->layerAdded (*parent, *child); }

    MultiLayer* parent;
    Layer* child;
  };


struct IsVisible
  : std::unary_function<Layer*, bool>
  {
    bool
    operator() (Layer* l)
        const
      { return l->isVisible (); }
  };


struct RemovalNotifier
  : std::unary_function<MultiLayer::LayerListener*, void>
  {
    RemovalNotifier (MultiLayer* parent,
                     Layer* child)
      : parent (parent),
        child (child)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (MultiLayer::LayerListener* l)
        const
      { l->layerRemoved (*parent, *child); }

    MultiLayer* parent;
    Layer* child;
  };


struct ReorderNotifier
  : std::unary_function<MultiLayer::LayerListener*, void>
  {
    ReorderNotifier (MultiLayer* parent,
                     Layer* child,
                     std::size_t from,
                     std::size_t to)
      : parent (parent),
        child (child),
        from (from),
        to (to)
      { }

    //
    // The compiler-generated copy constructor, destructor, and assignment
    // operator are acceptable.
    //

    void
    operator() (MultiLayer::LayerListener* l)
        const
      { l->layerPositionChanged (*parent, *child, from, to); }

    MultiLayer* parent;
    Layer* child;
    std::size_t from;
    std::size_t to;
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


namespace                               // Open unnamed namespace.
{


template<class Notifier>
void
notify (std::set<MultiLayer::LayerListener*> listenersCopy,
        Notifier notifier)
  { std::for_each (listenersCopy.begin (), listenersCopy.end (), notifier); }


}                                       // Close unnamed namespace.


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


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


class MultiLayerImpl::MultiListener
  : public Layer::VisibilityListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    struct Disabler
      {
        Disabler (MultiListener& listener)
          : listener (listener)
          { listener.ignore (true); }

        ~Disabler ()
            NOTHROWS
          { listener.ignore (false); }

        MultiListener& listener;
      };

    MultiListener (MultiLayerImpl& parent)
      : parent (parent),
        ignoreChanges (false)
      { }

    void
    ignore (bool ignore)
      { ignoreChanges = ignore; }

    void
    visibilityChanged (Layer& layer) override
      {
        if (!ignoreChanges)
          {
            parent.updateVisibility (layer.isVisible ());
          }
      }

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    MultiLayerImpl& parent;
    bool ignoreChanges;
  };


}                                       // Close core namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


MultiLayerImpl::MultiLayerImpl (const char* name)
  : AbstractLayer (name),
    visibilityListener (new MultiListener (*this))
  { setVisible (false); }               // The empty MultiLayer is not visible.


void
MultiLayerImpl::addLayer (Layer* layer)
  {
    if (layer)
      {
        //
        // N.B.:  Lock hierarchy is parent before child.
        //
        Lock lock(getMutex());
        bool makeVisible (!this->isVisible () && layer->isVisible ());

        layers.push_back (layer);
        notify (listeners,
                AdditionNotifier (const_cast<MultiLayerImpl*> (this), layer));
        layer->addVisibilityListener (visibilityListener);
        if (makeVisible)
          {
            setVisible (true);
          }
      }
  }


void
MultiLayerImpl::addLayerListener (LayerListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        listeners.insert (listener);
      }
  }


void
MultiLayerImpl::clearLayers ()
  {
    Lock lock(getMutex());

    std::for_each (layers.begin (),
                   layers.end (),
                   std::bind2nd (std::mem_fun (&Layer::removeVisibilityListener),
                                 visibilityListener));
    layers.clear ();
    this->setVisible (false);
  }


Layer&
MultiLayerImpl::getLayer (std::size_t position)
    const
  {
    Lock lock(getMutex());

    if (position >= layers.size ())
      {
        throw std::invalid_argument (MEM_FN ("getLayer")
                                     "Position out of range");
      }

    auto iter (layers.begin ());

    std::advance (iter, position);
    return **iter;
  }


std::size_t
MultiLayerImpl::getLayerCount ()
    const
  {
    Lock lock(getMutex());

    return layers.size ();
  }


std::list<Layer*>
MultiLayerImpl::getLayers ()
    const
  {
    Lock lock(getMutex());

    return layers;
  }


void
MultiLayerImpl::insertLayer (Layer* layer,
                             std::size_t position)
  {
    if (layer)
      {
        //
        // N.B.:  Lock hierarchy is parent before child.
        //
        Lock lock(getMutex());

        if (position > layers.size ())
          {
            throw std::invalid_argument (MEM_FN ("insertLayer")
                                         "Position out of range");
          }

        bool makeVisible (!isVisible () && layer->isVisible ());
        auto iter (layers.begin ());

        std::advance (iter, position);
        layers.insert (iter, layer);
        notify (listeners,
                AdditionNotifier (const_cast<MultiLayerImpl*> (this), layer));
        layer->addVisibilityListener (visibilityListener);
        if (makeVisible)
          {
            setVisible (true);
          }
      }
  }


void
MultiLayerImpl::removeLayer (Layer* layer)
  {
    if (layer)
      {
        Lock lock(getMutex());

        if (layers.end () != std::find (layers.begin (), layers.end (), layer))
          {
            layer->removeVisibilityListener (visibilityListener);
            layers.remove (layer);
            notify (listeners, RemovalNotifier (this, layer));

            //
            // If the layer being removed is visible, the MultiLayer's
            // visibility may change.
            //

            if (layer->isVisible ())
              {
                updateVisibility (false);
              }
          }
      }
  }


void
MultiLayerImpl::removeLayerListener (LayerListener* listener)
  {
    if (listener)
      {
        Lock lock(getMutex());

        listeners.erase (listener);
      }
  }


void
MultiLayerImpl::setLayerPosition (Layer* layer,
                                  std::size_t position)
  {
    if (layer)
      {
        Lock lock(getMutex());

        if (position > layers.size ())
          {
            throw std::invalid_argument (MEM_FN ("setLayerPosition")
                                         "Position out of range");
          }

        auto oldIter (std::find (layers.begin (),
                                                        layers.end (),
                                                        layer));

        if (oldIter != layers.end ())
          {
            std::size_t oldPosition (std::distance (layers.begin (), oldIter));

            if (position != oldPosition)
              {
                auto newIter (layers.begin ());

                std::advance (newIter, position);
                layers.splice (newIter, layers, oldIter);
                notify (listeners,
                        ReorderNotifier (this, layer, oldPosition, position));
              }
          }
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


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


void
MultiLayerImpl::updateVisibility (bool childVisibility)
  {
    //
    // Visibility of the MultiLayer is turned on if any child layer is visible.
    //

    setVisible (childVisibility
                || layers.end () != std::find_if (layers.begin (),
                                                  layers.end (),
                                                  IsVisible ()));
  }


}                                       // Close core namespace.
}                                       // Close atakmap namespace.
