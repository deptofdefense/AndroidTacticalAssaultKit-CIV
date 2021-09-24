////============================================================================
////
////    FILE:           KML_DriverDefinition.cpp
////
////    DESCRIPTION:    Implementation of KML_DriverDefinition class.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 17, 2015  scott           Created.
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


#include "feature/KML_DriverDefinition.h"

#include <cstring>

#include "ogr_feature.h"


#define MEM_FN( fn )    "atakmap::feature::KML_DriverDefinition::" fn ": "


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


TAK::Engine::Port::String
KML_DriverDefinition::getStyle (const char* filePath,
                                const OGRFeature& feature,
                                const OGRGeometry& geometry)
    const
  {
    TAK::Engine::Port::String result (const_cast<OGRFeature&> (feature).GetStyleString ());

    if (!TAK::Engine::Port::String_trim (result, result))      // Trim in place.
      {
        return DefaultDriverDefinition::getStyle (filePath, feature, geometry);
      }

    //
    // If the Feature is in a KMZ, check for embedded icons and expand the icon
    // URI to an absolute path if appropriate.
    //

    if (TAK::Engine::Port::String_endsWith (filePath, "kmz"))
      {
        const char* symbolStart (std::strstr (result, "SYMBOL("));

        if (symbolStart)
          {
            const char* idStart (std::strstr (symbolStart + 7, "id:"));

            if (idStart)
              {
                const char* uriStart (idStart + 3);
                const char* uriEnd (std::strchr (idStart + 3, ','));

                if (!uriEnd)
                  {
                    if (*uriStart == '"')
                      {
                        uriEnd = std::strchr (uriStart + 1, '"');
                      }
                    else if (uriEnd = std::strchr (uriStart, ')'), uriEnd != nullptr)
                      {
                        uriEnd = uriStart + std::strlen (uriStart);
                      }
                  }

                const char* colon (std::strchr (uriStart, ':'));
                if (!colon || colon > uriEnd)
                  {
                    std::ostringstream strm;

                    //
                    // Split the result at the colon just before uriStart.
                    // Insert a zip URI to the parent file.
                    //
                    result.get ()[uriStart - result - 1] = '\0';
                    strm << result << ':';
                    if (*uriStart == '"')
                      {
                        ++uriStart;
                        strm << '"';
                      }
                    strm << "zip://" << filePath << "!/" << uriStart;
                    result = strm.str ().c_str ();
                  }
              }
          }
      }

    return result;
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

