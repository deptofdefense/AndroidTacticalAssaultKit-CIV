////============================================================================
////
////    FILE:           DefaultSchemaDefinition.h
////
////    DESCRIPTION:    Concrete singleton default OGR schema definition class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 22, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_DEFAULT_SCHEMA_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_DEFAULT_SCHEMA_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/OGR_SchemaDefinition.h"


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
///  class atakmap::feature::DefaultSchemaDefinition
///
///     Concrete singleton default OGR schema definition class.
///
///=============================================================================


class DefaultSchemaDefinition
  : public OGR_SchemaDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    ~DefaultSchemaDefinition ()
        NOTHROWS
      { }

    //
    // Singleton accessor.
    //
    static
    const DefaultSchemaDefinition*
    get ();


    //==================================
    //  OGR_SchemaDefinition INTERFACE
    //==================================


    StringVector
    getNameFields (const char* filePath,
                   const OGRFeatureDefn&)
        const override;

    //
    // Returns true.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    bool
    matches (const char* filePath,
             const OGRFeatureDefn&)
        const override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    DefaultSchemaDefinition ()
      { }
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

#endif  // #ifndef ATAKMAP_FEATURE_DEFAULT_SCHEMA_DEFINITION_H_INCLUDED
