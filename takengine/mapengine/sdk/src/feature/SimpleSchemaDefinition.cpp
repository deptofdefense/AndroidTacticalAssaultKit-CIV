////============================================================================
////
////    FILE:           SimpleSchemaDefinition.cpp
////
////    DESCRIPTION:    Implementation of SimpleSchemaDefinition class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 24, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/SimpleSchemaDefinition.h"

#include <cstddef>
#include <stdexcept>

#include "ogr_feature.h"
#include "string/StringHacks.hh"


#define MEM_FN( fn )    "atakmap::feature::SimpleSchemaDefinition::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


bool
SimpleSchemaDefinition::matches (const char* filePath,
                                 const OGRFeatureDefn& featureDef)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("matches")
                                     "Received NULL filePath");
      }

    //
    // The Feature definition matches the schema if all of it's field names
    // match all of the schema's columnNames.
    //
    OGRFeatureDefn& fDef (const_cast<OGRFeatureDefn&> (featureDef));
    std::size_t fieldCount (fDef.GetFieldCount ());
    bool matches (fieldCount >= columnNames.size ());
    std::size_t nameCount (0);

    for (std::size_t i (0); matches && i < fieldCount; ++i)
      {
        OGRFieldDefn* fieldDef (fDef.GetFieldDefn (i));
        PGSC::String fieldName (fieldDef
                                ? PGSC::trimOrNULL (fieldDef->GetNameRef ())
                                : NULL);

        if (fieldName)
          {
            ++nameCount;
            matches = columnNames.count (fieldName);
          }
      }

    return matches && nameCount == columnNames.size ();
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////
