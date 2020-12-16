////============================================================================
////
////    FILE:           DefaultSchemaDefinition.cpp
////
////    DESCRIPTION:    Implementation of DefaultSchemaDefinition class.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/DefaultSchemaDefinition.h"

#include <cstring>
#include <algorithm>
#include <memory>
#include <stdexcept>

#include "ogr_feature.h"

#include "port/String.h"


#define MEM_FN( fn )    "atakmap::feature::DefaultSchemaDefinition::" fn ": "


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


const DefaultSchemaDefinition*
DefaultSchemaDefinition::get ()
  {
    static std::unique_ptr<DefaultSchemaDefinition> instance
        (new DefaultSchemaDefinition);

    return instance.get ();
  }


OGR_SchemaDefinition::StringVector
DefaultSchemaDefinition::getNameFields (const char* filePath,
                                        const OGRFeatureDefn& featureDef)
    const
  {
    StringVector result;
    int fieldCount
        (const_cast<OGRFeatureDefn&> (featureDef).GetFieldCount ());

    for (int i (0); i < fieldCount; ++i)
      {
        OGRFieldDefn* fieldDef
            (const_cast<OGRFeatureDefn&> (featureDef).GetFieldDefn (i));

        if (fieldDef && fieldDef->GetType () == OFTString)
          {
            TAK::Engine::Port::String fieldName;
            if(TAK::Engine::Port::String_trim(fieldName, fieldDef->GetNameRef())) {
               // convert to lower
               const std::size_t len = strlen(fieldName);
               for(std::size_t j = 0u; j < len; j++)
                 fieldName[(int)j] = tolower(fieldName[(int)j]);
            }

            if (fieldName && std::strstr (fieldName, "name"))
              {
                //
                // Add the field to the end of the list unless it is exactly
                // "name".
                //

                if (std::strcmp (fieldName, "name"))
                  {
                    result.push_back (fieldName);
                  }
                else
                  {
                    result.insert (result.begin (), fieldName);
                  }
              }
          }
      }

    return result;
  }


bool
DefaultSchemaDefinition::matches (const char* filePath,
                                  const OGRFeatureDefn&)
    const
  {
    if (!filePath)
      {
        throw std::invalid_argument (MEM_FN ("matches")
                                     "Received NULL filePath");
      }

    return true;
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
