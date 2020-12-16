////============================================================================
////
////    FILE:           ShapefileDriverDefinition.h
////
////    DESCRIPTION:    Concrete OGR driver definition class for ESRI Shapefiles.
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


#ifndef ATAKMAP_FEATURE_SHAPEFILE_DRIVER_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_SHAPEFILE_DRIVER_DEFINITION_H_INCLUDED


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
///  class atakmap::feature::ShapefileDriverDefinition
///
///     Concrete ESRI Shapefile OGR driver definition.
///
///=============================================================================


class ShapefileDriverDefinition
  : public DefaultDriverDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ShapefileDriverDefinition ()
      : DefaultDriverDefinition ("esri shapefile", "shp", 1)
      { }

    ~ShapefileDriverDefinition ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //


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
        return "SYMBOL(id:asset:/icons/reference_point.png,c:#FFFFFFFF)";
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

#endif  // #ifndef ATAKMAP_FEATURE_SHAPEFILE_DRIVER_DEFINITION_H_INCLUDED
