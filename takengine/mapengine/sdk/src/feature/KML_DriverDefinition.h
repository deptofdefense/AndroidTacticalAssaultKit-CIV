////============================================================================
////
////    FILE:           KML_DriverDefinition.h
////
////    DESCRIPTION:    Concrete OGR driver definition class for KML files.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 23, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_KML_DRIVER_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_KML_DRIVER_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/DefaultDriverDefinition.h"


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
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::KML_DriverDefinition
///
///     Concrete OGR driver definition for KML files.
///
///=============================================================================


class KML_DriverDefinition
  : public DefaultDriverDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    KML_DriverDefinition ()
      : DefaultDriverDefinition ("libkml", "kml", 1)
      { }

    ~KML_DriverDefinition ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //


    //==================================
    //  OGR_DriverDefinition INTERFACE
    //==================================


    TAK::Engine::Port::String
    getStyle (const char* filePath,
              const OGRFeature&,
              const OGRGeometry&)
        const;

    bool layerNameIsPath() const { return true; }

                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  DefaultDriverDefinition IMPLEMENTATION
    //==================================


    TAK::Engine::Port::String
    createDefaultPointStyle ()
        const
      {
        return "SYMBOL(id:http://maps.google.com/mapfiles/kml/"
                                "pushpin/ylw-pushpin.png,c:#FFFFFFFF)";
      }
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

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_KML_DRIVER_DEFINITION_H_INCLUDED
