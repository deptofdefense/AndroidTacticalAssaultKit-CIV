////============================================================================
////
////    FILE:           FeatureLayer.h
////
////    DESCRIPTION:    Declaration of concrete class for feature data layers.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 30, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_FEATURE_LAYER_H_INCLUDED
#define ATAKMAP_FEATURE_FEATURE_LAYER_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "core/AbstractLayer.h"
#include "core/ServiceManagerImpl.h"
#include "port/Platform.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


class ENGINE_API FeatureDataStore;


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::FeatureLayer
///
///     Concrete class for feature (point and vector) data.
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
///     Well-defined feature service interfaces that are part of the SDK may be
///     found in the atakmap::feature namespace.
///
///=============================================================================


class ENGINE_API FeatureLayer
  : public core::AbstractLayer,
    public core::ServiceManagerImpl
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Constructs a FeatureLayer with the supplied layerName and
    // FeatureDataStore.
    //
    // Throws std::invalid_argument if the supplied layerName is or
    // FeatureDataStore is NULL.
    //
    FeatureLayer (const char* layerName,
                  FeatureDataStore* dataStore);

    ~FeatureLayer ()
        NOTHROWS
      { }

    FeatureDataStore&
    getDataStore ()
        const
        NOTHROWS
      { return *dataStore; }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::shared_ptr<FeatureDataStore> dataStore;
  };


}                                       // Close feature namespace.
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


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


#endif  // #ifndef ATAKMAP_FEATURE_FEATURE_LAYER_H_INCLUDED
