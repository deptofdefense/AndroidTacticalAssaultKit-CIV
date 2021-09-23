////============================================================================
////
////    FILE:           Layer.h
////
////    DESCRIPTION:    Abstract base class for map layers.
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


#ifndef ATAKMAP_CORE_LAYER_H_INCLUDED
#define ATAKMAP_CORE_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////

#include <memory>

#include "port/Platform.h"

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
///  class atakmap::core::Layer
///
///     Abstract base class for map layers.
///
///=============================================================================


class ENGINE_API Layer
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    //
    // Public nested types.
    //

    class VisibilityListener;

    //
    // Public member functions.
    //

    virtual
    ~Layer ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Registers the supplied VisibilityListener for notifications when the
    // visibility of the layer changes.
    //
    virtual
    void
    addVisibilityListener (VisibilityListener*)
        = 0;

    //
    // Returns the name of the layer.
    //
    virtual
    const char*
    getName ()
        const
        NOTHROWS
        = 0;

    //
    // Returns the current visibility of the layer.
    //
    virtual
    bool
    isVisible ()
        const
        = 0;

    //
    // Unregisters the supplied VisibilityListener from notifications of layer
    // visibility changes.
    //
    virtual
    void
    removeVisibilityListener (VisibilityListener*)
        = 0;

    //
    // Sets the visibility of the layer.  Any registered VisibilityListeners
    // should be notified if the visibility of the layer changes.
    //
    virtual
    void
    setVisible (bool visibility)
        = 0;
  };


  typedef std::unique_ptr<Layer, void(*)(const Layer *)> LayerPtr;
///=========================================================================
///
///  class atakmap::core::Layer::VisibilityListener
///
///     Abstract base class for layer visibility change callbacks.
///
///=========================================================================


class Layer::VisibilityListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~VisibilityListener ()
        NOTHROWS
      { };

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the visibility of the supplied layer has changed.
    //
    virtual
    void
    visibilityChanged (Layer&)
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

#endif  // #ifndef ATAKMAP_CORE_LAYER_H_INCLUDED
