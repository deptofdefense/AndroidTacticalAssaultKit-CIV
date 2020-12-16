////============================================================================
////
////    FILE:           MultiLayerImpl.h
////
////    DESCRIPTION:    Declaration of a concrete implementation class for map
////                    multi-layers.
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


#ifndef ATAKMAP_CORE_MULTI_LAYER_IMPL_H_INCLUDED
#define ATAKMAP_CORE_MULTI_LAYER_IMPL_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <list>
#include <set>

#include "core/AbstractLayer.h"
#include "core/MultiLayer.h"

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
///  class atakmap::core::MultiLayerImpl
///
///     A layer composed of an ordered list of zero or more child layers.  Child
///     layers may be added, removed, and reordered.  Child layers are stacked,
///     i.e., layers later in the list lie atop preceding layers.  Layer changes
///     can be monitored by registering a MultiLayer::LayerListener for callback
///     notifications.
///
///=============================================================================


class MultiLayerImpl
  : public MultiLayer,
    public AbstractLayer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Constructs a MultiLayerImpl with the supplied name.
    //
    // Throws std::invalid_argument on NULL name.
    //
    explicit
    MultiLayerImpl (const char* name);  // Must be non-NULL.

    ~MultiLayerImpl ()
        NOTHROWS
      { delete visibilityListener; }

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    //
    // MultiLayer member functions.
    //

    //
    // Adds a new layer to the end of the list of layers--equivalent to a call
    // to insertLayer (layer, getLayerCount ()).  Ignores a nullptr layer.
    // Registered LayerListeners are notified of a new layer.
    //
    void
    addLayer (Layer*);

    //
    // Registers the supplied LayerListener for notifications when layers are
    // added, removed, or reordered.  Ignores a NULL LayerListener.
    //
    void
    addLayerListener (LayerListener*);

    //
    // Removes all layers from the MultiLayer.  Registered LayerListeners are
    // notified of the removal of each layer.
    //
    void
    clearLayers ();

    //
    // Returns the layer at the supplied position in the list of layers.
    //
    // Throws std::invalid_argument if position is >= getLayerCount().
    //
    Layer&
    getLayer (std::size_t position)
        const;

    //
    // Returns the number of layers.
    //
    std::size_t
    getLayerCount ()
        const;

    //
    // Returns a copy of the list of layers.
    //
    std::list<Layer*>
    getLayers ()
        const;

    //
    // Inserts the supplied layer into the list at the supplied position.
    // Ignores a nullptr layer.   Registered LayerListeners are notified of a new
    // layer.
    //
    // Throws std::invalid_argument if position is > getLayerCount().
    //
    void
    insertLayer (Layer*,
                 std::size_t position);

    //
    // Removes the supplied layer from the list of layers.  Ignores a nullptr
    // layer.  Registered LayerListeners are notified if the supplied layer is
    // removed from the list.
    //
    void
    removeLayer (Layer*);

    //
    // Unregisters the supplied LayerListener from notifications of layer
    // changes.  Ignores a NULL LayerListener.
    //
    void
    removeLayerListener (LayerListener*);

    //
    // Moves the supplied layer to the supplied position in the list.  Ignores
    // a nullptr layer.  Registered LayerListeners are notified if the position of
    // the supplied layer is changed.
    //
    void
    setLayerPosition (Layer*,
                      std::size_t position);

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    //
    // Private nested types.
    //

    class MultiListener;

    //
    // Private member functions.
    //

    void
    updateVisibility (bool childVisibility);

    //
    // Private representation.
    //

    std::list<Layer*> layers;
    std::set<LayerListener*> listeners;
    VisibilityListener* visibilityListener;
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
#endif  // #ifndef ATAKMAP_CORE_MULTI_LAYER_IMPL_H_INCLUDED
