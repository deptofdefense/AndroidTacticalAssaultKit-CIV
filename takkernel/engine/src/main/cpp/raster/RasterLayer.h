////============================================================================
////
////    FILE:           RasterLayer.h
////
////    DESCRIPTION:    Declaration of abstract base class for raster data
////                    layers.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 3, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_RASTER_RASTER_LAYER_H_INCLUDED
#define ATAKMAP_RASTER_RASTER_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <memory>
#include <vector>

#include "core/Layer.h"
#include "core/Service.h"
#include "port/Iterator.h"

////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace core                          // Open core namespace.
{


class Projection;


}                                       // Close core namespace.


namespace feature                       // Open feature namespace.
{


class Geometry;

typedef std::unique_ptr<Geometry, void(*)(const Geometry *)> UniqueGeometryPtr;
    
}                                       // Close feature namespace.
}                                       // Close atakmap namespace.

////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace raster                        // Open raster namespace.
{


///=============================================================================
///
///  class atakmap::raster::RasterLayer
///
///     Abstract base class for raster data layers.
///
///     The RasterLayer manages a selection that indicates what content should
///     be rendered.  For very simple RasterLayer implementations, there may
///     only be one selection value (e.g. a single image).  More complex layers
///     may contain multiple datasets and/or multiple imagery types providing
///     potentially worldwide coverage.  In this case, the selection may be used
///     to explicitly request a single dataset or type be rendered.
///
///     The RasterLayer has a preferred atakmap::core::Projection for display.
///     This value may be NULL in the event that the layer has no preference.
///     The value may change periodically, driven by the currently displayed
///     data (i.e. selection changes).
///
///     Services may provide optional functions for the layer that are not
///     expressly part of the API.  The Service::Manager::getService member
///     function provides a mechanism for other users to acquire access to that
///     functionality for a layer.
///
///     The service pattern provides layer implementors with the flexibility to
///     distribute well-defined functionality outside of the model domain.
///     Specifically, it can provide a pluggable point for functionality that
///     may be within the domain of the renderer; the application would normally
///     have no means to communicate with the renderer.  It also allows for
///     delegation of model domain functionality that can be more efficiently
///     serviced by the renderer.
///
///     Well-defined raster service interfaces that are part of the SDK may be
///     found in the atakmap::raster namespace.
///
///=============================================================================


class RasterLayer
  : public virtual core::Layer,
    public virtual core::Service::Manager
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class PreferredProjectionListener;
    class SelectionListener;
    class SelectionVisibilityListener;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    //
    // The compiler-generated constructor, copy constructor, destructor, and
    // assignment operator are acceptable.
    //

    //
    // Registers the supplied PreferredProjectionListener for notifications when
    // the preferred projection of the layer changes.
    //
    virtual
    void
    addPreferredProjectionListener (PreferredProjectionListener*)
        = 0;

    //
    // Registers the supplied SelectionListener for notifications when the
    // layer's selection changes.
    //
    virtual
    void
    addSelectionListener (SelectionListener*)
        = 0;

    //
    // Registers the supplied SelectionVisibilityListener for notifications when
    // the visibility of the layer's selection changes.
    //
    virtual
    void
    addSelectionVisibilityListener (SelectionVisibilityListener*)
        = 0;

    //
    // Returns the coverage geometry associated with the specified selection
    // (which must not be NULL).
    //
    virtual
    feature::UniqueGeometryPtr
    getGeometry (const char* selection)
        const
        = 0;

    //
    // Returns the layer's preferred projection.  This method will generally
    // return the native projection for the data that is currently being
    // rendered.  This value is expected to change if the current selection is
    // manually or automatically updated.
    //
    virtual
    core::Projection*
    getPreferredProjection ()
        const
        = 0;

    //
    // Returns the current selection.  If the layer is currently in auto-select
    // mode, the value returned should be the content currently displayed.  In
    // the case where multiple content is displayed, the recommendation is to
    // return the value for the top-most content.
    //
    virtual
    const char*
    getSelection ()
        const
        = 0;

    //
    // Returns the list of available selection options for all data contained in
    // the layer.  The available selection options are recommended to be a
    // single logical index over the data (e.g., dataset names, imagery types).
    // The NULL options is implicit and does not need to be included in the
    // results.
    //
    virtual
      atakmap::port::Iterator<const char*> *
    getSelectionOptions ()
        const
        = 0;

    //
    // Returns true if the layer is in auto-select mode.
    //
    virtual
    bool
    isAutoSelect ()
        const
        = 0;

    using Layer::isVisible;

    //
    // Returns true if the supplied selection type is visible.  The selection
    // type should be one of the values returned by getSelectionOptions.
    //
    virtual
    bool
    isVisible (const char*)
        const
        = 0;

    //
    // Unregisters the supplied PreferredProjectionListener from notifications
    // of changes to the layer's preferred projection.
    //
    virtual
    void
    removePreferredProjectionListener (PreferredProjectionListener*)
        = 0;

    //
    // Unregisters the supplied SelectionListener from notifications of layer
    // selection changes.
    //
    virtual
    void
    removeSelectionListener (SelectionListener*)
        = 0;

    //
    // Unregisters the supplied SelectionVisibilityListener from notifications
    // of layer selection visibility changes.
    //
    virtual
    void
    removeSelectionVisibilityListener (SelectionVisibilityListener*)
        = 0;

    //
    // Sets the preferred projection for the layer.
    //
    virtual
    void
    setPreferredProjection (core::Projection*)
        = 0;

    //
    // Sets the type of selection to be displayed.  The selection type should be
    // one of the values returned by getSelectionOptions or NULL.  If NULL, the
    // layer will automatically select the imagery based on the current
    // resolution.
    //
    virtual
    void
    setSelection (const char* type = nullptr)
        = 0;

    //
    // Sets the visibility of the supplied selection type to the supplied value.
    // The selection type should be one of the values returned by
    // getSelectionOptions.
    //
    virtual
    void
    setSelectionVisibility (const char* type,
                            bool visible)
        = 0;
  };


///=========================================================================
///
///  class atakmap::raster::RasterLayer::PreferredProjectionListener
///
///     Abstract base class for raster layer preferred projection change
///     callbacks.
///
///=========================================================================


class RasterLayer::PreferredProjectionListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~PreferredProjectionListener ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the preferred projection of the supplied raster layer has
    // changed.
    //
    virtual
    void
    preferredProjectionChanged (RasterLayer&)
        = 0;
  };


///=========================================================================
///
///  class atakmap::raster::RasterLayer::SelectionListener
///
///     Abstract base class for raster layer selection change callbacks.
///
///=========================================================================


class RasterLayer::SelectionListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~SelectionListener ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the selection of the supplied raster layer has changed.
    //
    virtual
    void
    selectionChanged (RasterLayer&)
        = 0;
  };


///=========================================================================
///
///  class atakmap::raster::RasterLayer::SelectionVisibilityListener
///
///     Abstract base class for raster layer selection visibility change
///     callbacks.
///
///=========================================================================


class RasterLayer::SelectionVisibilityListener
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~SelectionVisibilityListener ()
        NOTHROWS
      { }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Called when the visibility of the selection of the supplied raster layer
    // has changed.
    //
    virtual
    void
    selectionVisibilityChanged (RasterLayer&)
        = 0;
  };


}                                       // Close raster namespace.
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

#endif  // #ifndef ATAKMAP_RASTER_RASTER_LAYER_H_INCLUDED
