////============================================================================
////
////    FILE:           MultiLayer.h
////
////    DESCRIPTION:    Declaration of a layer composed of zero or more child
////                    layers.  Child layers may be added, removed, and
////                    reordered.
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


#ifndef ATAKMAP_CORE_MULTI_LAYER_H_INCLUDED
#define ATAKMAP_CORE_MULTI_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <list>

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
///  class atakmap::core::MultiLayer
///
///     A layer composed of an ordered list of zero or more child layers.  Child
///     layers may be added, removed, and reordered.  Child layers are stacked,
///     i.e., layers later in the list lie atop preceding layers.  Layer changes
///     can be monitored by registering a MultiLayer::LayerListener for callback
///     notifications.
///
///=============================================================================


class MultiLayer
  : public virtual Layer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Public nested types.
    //

    class LayerListener;

    //
    // Public member functions.
    //

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Adds a new layer to the end of the list of layers--equivalent to a call
    // to insertLayer (layer, getLayerCount ()).  Ignores a nullptr layer.
    // Registered LayerListeners are notified of a new layer.
    //
    virtual
    void
    addLayer (Layer*)
        = 0;

    //
    // Registers the supplied LayerListener for notifications when layers are
    // added, removed, or reordered.  Ignores a NULL LayerListener.
    //
    virtual
    void
    addLayerListener (LayerListener*)
        = 0;

    //
    // Removes all layers from the MultiLayer.  Registered LayerListeners are
    // notified of the removal of each layer.
    //
    virtual
    void
    clearLayers ()
        = 0;

    //
    // Returns the layer at the supplied position in the list of layers.
    //
    // Throws std::invalid_argument if position is >= getLayerCount().
    //
    virtual
    Layer&
    getLayer (std::size_t position)
        const
        = 0;

    //
    // Returns the number of layers.
    //
    virtual
    std::size_t
    getLayerCount ()
        const
        = 0;

    //
    // Returns a copy of the list of layers.
    //
    virtual
    std::list<Layer*>
    getLayers ()
        const
        = 0;

    //
    // Inserts the supplied layer into the list at the supplied position.
    // Ignores a nullptr layer.   Registered LayerListeners are notified of a new
    // layer.
    //
    // Throws std::invalid_argument if position is > getLayerCount().
    //
    virtual
    void
    insertLayer (Layer*,
                 std::size_t position)
        = 0;

    //
    // Removes the supplied layer from the list of layers.  Ignores a nullptr
    // layer.  Registered LayerListeners are notified if the supplied layer is
    // removed from the list.
    //
    virtual
    void
    removeLayer (Layer*)
        = 0;

    //
    // Unregisters the supplied LayerListener from notifications of layer
    // changes.  Ignores a NULL LayerListener.
    //
    virtual
    void
    removeLayerListener (LayerListener*)
        = 0;

    //
    // Moves the supplied layer to the supplied position in the list.  Ignores
    // a nullptr layer.  Registered LayerListeners are notified if the position of
    // the supplied layer is changed.
    //
    virtual
    void
    setLayerPosition (Layer*,
                      std::size_t position)
        = 0;
  };


///=========================================================================
///
///  class atakmap::core::MultiLayer::LayerListener
///
///     Abstract base class for layer change callbacks.
///
///=========================================================================


class MultiLayer::LayerListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~LayerListener ()
        NOTHROWS
      { };

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the supplied Layer has been added to the supplied MultiLayer.
    //
    virtual
    void
    layerAdded (MultiLayer& parent,
                Layer& child)
        = 0;

    //
    // Called when the position of the supplied Layer has been explicitly
    // changed.
    //
    virtual
    void
    layerPositionChanged (MultiLayer& parent,
                          Layer& child,
                          std::size_t oldPosition,
                          std::size_t newPosition)
        = 0;

    //
    // Called when the supplied Layer has been removed from the supplied
    // MultiLayer.
    //
    virtual
    void
    layerRemoved (MultiLayer& parent,
                  Layer& child)
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

#endif  // #ifndef ATAKMAP_CORE_MULTI_LAYER_H_INCLUDED
