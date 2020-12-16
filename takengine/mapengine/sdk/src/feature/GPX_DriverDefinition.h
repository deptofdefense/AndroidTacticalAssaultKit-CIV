////============================================================================
////
////    FILE:           GPX_DriverDefinition.h
////
////    DESCRIPTION:    Concrete OGR driver definition class for GPX files.
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


#ifndef ATAKMAP_FEATURE_GPX_DRIVER_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_GPX_DRIVER_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/DefaultDriverDefinition.h"

#include "ogrsf_frmts.h"
#include "string/StringHacks.hh"


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
///  class atakmap::feature::GPX_DriverDefinition
///
///     Concrete OGR driver definition for GPX files.
///
///=============================================================================


class GPX_DriverDefinition
  : public DefaultDriverDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    GPX_DriverDefinition ()
      : DefaultDriverDefinition ("gpx", "gpx", 1)
      { }

    ~GPX_DriverDefinition ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //


    //==================================
    //  OGR_DriverDefinition INTERFACE
    //==================================


    bool
    skipLayer (const OGRLayer& layer)
        const
      { return !PGSC::strcasecmp (const_cast<OGRLayer&>(layer).GetName (), "track_points"); }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  DefaultDriverDefinition IMPLEMENTATION
    //==================================


    char*
    createDefaultPointStyle ()
        const
      {
        return PGSC::dupString
                   ("SYMBOL(id:asset:/icons/reference_point.png,c:#FFFFFFFF)");
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

#endif  // #ifndef ATAKMAP_FEATURE_GPX_DRIVER_DEFINITION_H_INCLUDED
