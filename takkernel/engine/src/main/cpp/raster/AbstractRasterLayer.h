////============================================================================
////
////    FILE:           AbstractRasterLayer.h
////
////    DESCRIPTION:    Definition of abstract base class for RasterLayer
////                    implementations.
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


#ifndef ATAKMAP_RASTER_ABSTRACT_RASTER_LAYER_H_INCLUDED
#define ATAKMAP_RASTER_ABSTRACT_RASTER_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <set>
#include <vector>

#include "core/AbstractLayer.h"
#include "core/ServiceManagerImpl.h"
#include "port/String.h"
#include "raster/RasterLayer.h"

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4250)
#endif

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
///  class atakmap::raster::AbstractRasterLayer
///
///     Abstract base class for RasterLayer implementations.
///
///     Concrete derived classes must implement the following RasterLayer member
///     functions:
///
///     getGeometry
///     getSelectionOptions
///
///=============================================================================


class AbstractRasterLayer
  : public virtual RasterLayer,
    public core::AbstractLayer,
    public core::ServiceManagerImpl
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~AbstractRasterLayer ()
        NOTHROWS
      { }

    //
    // A protected constructor is declared below.  The compiler is unable to
    // generate a copy constructor or assignment operator (due to a NonCopyable
    // base class).  This is acceptable.
    //


    //==================================
    //  RasterLayer INTERFACE
    //==================================


    //
    // Registers the supplied PreferredProjectionListener for notifications when
    // the preferred projection of the layer changes.  Ignores a NULL
    // PreferredProjectionListener.
    //
    void
    addPreferredProjectionListener (PreferredProjectionListener*);

    //
    // Registers the supplied SelectionListener for notifications when the
    // layer's selection changes.  Ignores a NULL SelectionListener.
    //
    void
    addSelectionListener (SelectionListener*);

    //
    // Registers the supplied SelectionVisibilityListener for notifications when
    // the visibility of the layer's selection changes.  Ignores a NULL
    // SelectionVisibilityListener.
    //
    void
    addSelectionVisibilityListener (SelectionVisibilityListener*);

    //
    // Returns the layer's preferred projection.  This method will generally
    // return the native projection for the data that is currently being
    // rendered.  This value is expected to change if the current selection is
    // manually or automatically updated.
    //
    core::Projection*
    getPreferredProjection ()
        const;

    //
    // Returns the current selection.  If the layer is currently in auto-select
    // mode, the value returned should be the content currently displayed.  In
    // the case where multiple content is displayed, the recommendation is to
    // return the value for the top-most content.
    //
    const char*
    getSelection ()
        const;

    //
    // Returns true if the layer is in auto-select mode.
    //
    bool
    isAutoSelect ()
        const;

    using Layer::isVisible;

    //
    // Returns true if the supplied selection type is visible.  The selection
    // type should be one of the values returned by getSelectionOptions.
    //
    bool
    isVisible (const char*)
        const;

    //
    // Unregisters the supplied PreferredProjectionListener from notifications
    // of changes to the layer's preferred projection.
    //
    void
    removePreferredProjectionListener (PreferredProjectionListener*);

    //
    // Unregisters the supplied SelectionListener from notifications of layer
    // selection changes.
    //
    void
    removeSelectionListener (SelectionListener*);

    //
    // Unregisters the supplied SelectionVisibilityListener from notifications
    // of layer selection visibility changes.
    //
    void
    removeSelectionVisibilityListener (SelectionVisibilityListener*);

    //
    // Sets the preferred projection for the layer.
    //
    void
    setPreferredProjection (core::Projection*);

    //
    // Sets the type of selection to be displayed.  The selection type should be
    // one of the values returned by getSelectionOptions or NULL.  If NULL, the
    // layer will automatically select the imagery based on the current
    // resolution.
    //
    void
    setSelection (const char* type = nullptr);

    //
    // Sets the visibility of the supplied selection type to the supplied value.
    // The selection type should be one of the values returned by
    // getSelectionOptions.
    //
    void
    setSelectionVisibility (const char* type,
                            bool visible);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //
    // Constructs an AbstractRasterLayer with the supplied name.
    //
    // Throws std::invalid_argument on NULL name.
    //
    explicit
    AbstractRasterLayer (const char* name); // Must be non-NULL.

    //
    // Notifies PreferredProjectionListeners of changed projection.
    //
    void
    notifyProjectionListeners ()
        const;

    //
    // Notifies SelectionListeners of changed selection.
    //
    void
    notifySelectionListeners ()
        const;

    //
    // Notifies SelectionVisibilityListeners of changed selection visibility.
    //
    void
    notifySelectionVisibilityListeners ()
        const;

    //
    // Called by setPreferredProjection.  Default implementation sets
    // preferredProjection data member.
    //
    virtual
    void
    setPreferredProjectionInternal (core::Projection*);

    //
    // Called by setSelection.  Default implementation sets selection data
    // member.
    //
    virtual
    void
    setSelectionInternal (const char* type);


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    core::Projection* preferred_projection_;
    std::set<PreferredProjectionListener*> projection_listeners_;
    TAK::Engine::Port::String selection_;
    std::set<SelectionListener*> selection_listeners_;
    std::set<TAK::Engine::Port::String, TAK::Engine::Port::StringLess> invisible_selections_;
    std::set<SelectionVisibilityListener*> selection_visibility_listeners_;
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
#ifdef _MSC_VER
#pragma warning(pop)
#endif
#endif  // #ifndef ATAKMAP_RASTER_ABSTRACT_RASTER_LAYER_H_INCLUDED
