////============================================================================
////
////    FILE:           OGR_SchemaDefinition.h
////
////    DESCRIPTION:    Abstract base class for OGR schema definitions.
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


#ifndef ATAKMAP_FEATURE_OGR_SCHEMA_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_OGR_SCHEMA_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <vector>

#include "port/String.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


class OGRFeatureDefn;


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
///  class atakmap::feature::OGR_SchemaDefinition
///
///     Abstract base class for OGR schema definitions.
///
///=============================================================================


class OGR_SchemaDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    typedef std::vector<TAK::Engine::Port::String>   StringVector;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~OGR_SchemaDefinition ()
        NOTHROWS
        = 0;

    //
    // Returns a (possibly empty) vector of field names extracted from the
    // supplied OGR feature definition that include the "name" substring.  If
    // there is a "name" field, it will be the first element in the vector.
    // (All field names are converted to lower case.)
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    virtual
    StringVector
    getNameFields (const char* filePath,
                   const OGRFeatureDefn&)
        const
        = 0;

    //
    // Returns the (possibly NULL) OGR_SchemaDefinition for the supplied
    // filePath and OGR feature definition.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    static
    const OGR_SchemaDefinition*
    getSchema (const char* filePath,
               const OGRFeatureDefn&);

    //
    // Returns true if the SchemaDefinition is applicable to the supplied
    // filePath and OGR feature definition.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    virtual
    bool
    matches (const char* filePath,
             const OGRFeatureDefn&)
        const
        = 0;

    //
    // Registers the supplied OGR_SchemaDefinition.
    // Ignores NULL OGR_SchemaDefinition or OGR_SchemaDefinition with
    // OGR_SchemaDefinition::getSchemaName() == NULL.
    //
    static
    void
    registerSchema (const OGR_SchemaDefinition*);

    //
    // Unregisters the supplied OGR_SchemaDefinition.
    // Ignores NULL OGR_SchemaDefinition or OGR_SchemaDefinition with
    // OGR_SchemaDefinition::getSchemaName() == NULL.
    //
    static
    void
    unregisterSchema (const OGR_SchemaDefinition*);
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

#endif  // #ifndef ATAKMAP_FEATURE_OGR_SCHEMA_DEFINITION_H_INCLUDED
