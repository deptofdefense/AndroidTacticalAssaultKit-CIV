////============================================================================
////
////    FILE:           DefaultDriverDefinition.cpp
////
////    DESCRIPTION:    Implementation of DefaultDriverDefinition class.
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


#include "feature/DefaultDriverDefinition.h"

#include "feature/Style.h"

#include "ogr_feature.h"
#include "ogr_geometry.h"
#include "ogrsf_frmts.h"


#define MEM_FN( fn )    "atakmap::feature::DefaultDriverDefinition::" fn ": "


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


DefaultDriverDefinition::DefaultDriverDefinition (const char* driverName,
                                                  const char* driverType,
                                                  unsigned int version)
  : driverName (driverName),
    driverType (driverType),
    version (version),
    encoding (FeatureDataSource::FeatureDefinition::WKB),
    strokeWidth (2.0),
    strokeColor (0xFFFFFFFF)
  {
    if (!driverName)
      {
        throw std::invalid_argument (MEM_FN ("DefaultDriverDefinition")
                                     "Received NULL driverName");
      }
    if (!driverType)
      {
        throw std::invalid_argument (MEM_FN ("DefaultDriverDefinition")
                                     "Received NULL driverType");
      }
  }


TAK::Engine::Port::String
DefaultDriverDefinition::getStyle (const char* filePath,
                                   const OGRFeature& feature,
                                   const OGRGeometry& geometry)
    const
  {
    const char* result (const_cast<OGRFeature&> (feature).GetStyleString ());

    if (!result)
      {
        switch (geometry.getGeometryType ())
          {
          case wkbPoint:
          case wkbMultiPoint:
          case wkbPoint25D:
          case wkbMultiPoint25D:

            result = getDefaultPointStyle ();
            break;

          case wkbLineString:
          case wkbMultiLineString:
          case wkbLinearRing:
          case wkbLineString25D:
          case wkbMultiLineString25D:

            result = getDefaultLineStringStyle ();
            break;

          case wkbPolygon:
          case wkbMultiPolygon:
          case wkbPolygon25D:
          case wkbMultiPolygon25D:

            result = getDefaultPolygonStyle ();
            break;

          default:

            result = getDefaultLineStringStyle ();
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


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


DefaultDriverDefinition::DefaultDriverDefinition
    (const char* driverName,
     const char* driverType,
     unsigned int version,
     FeatureDataSource::FeatureDefinition::Encoding encoding,
     float strokeWidth,                 // Defaults to 2.0.
     unsigned int strokeColor)          // Defaults to 0xFFFFFFFF (opaque white).
  : driverName (driverName),
    driverType (driverType),
    version (version),
    encoding (encoding),
    strokeWidth (strokeWidth),
    strokeColor (strokeColor)
  {
    if (!driverName)
      {
        throw std::invalid_argument (MEM_FN ("DefaultDriverDefinition")
                                     "Received NULL driverName");
      }
    if (!driverType)
      {
        throw std::invalid_argument (MEM_FN ("DefaultDriverDefinition")
                                     "Received NULL driverType");
      }
  }

inline
const char *
DefaultDriverDefinition::getDefaultLineStringStyle()
const
{
    if (!lineStringStyle)
    {
        lineStringStyle = createDefaultLineStringStyle();
    }

    return lineStringStyle;
}


const char*
DefaultDriverDefinition::getDefaultPointStyle()
    const
{
    if (!pointStyle)
    {
        pointStyle = createDefaultPointStyle();
    }

    return pointStyle;
}


const char*
DefaultDriverDefinition::getDefaultPolygonStyle()
    const
{
    if (!polygonStyle)
    {
        polygonStyle = createDefaultPolygonStyle();
    }

    return polygonStyle;
}

}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


TAK::Engine::Port::String
DefaultDriverDefinition::createDefaultLineStringStyle ()
    const
  { 
    TAK::Engine::Port::String ogr;
    BasicStrokeStyle (strokeColor, strokeWidth).toOGR (ogr);
    return ogr;
  }



TAK::Engine::Port::String
DefaultDriverDefinition::createDefaultPointStyle ()
    const
  { 
    TAK::Engine::Port::String ogr;
    BasicStrokeStyle (strokeColor, strokeWidth).toOGR (ogr);
    return ogr;
  }



TAK::Engine::Port::String
DefaultDriverDefinition::createDefaultPolygonStyle ()
    const
  { 
    TAK::Engine::Port::String ogr;
    BasicStrokeStyle (strokeColor, strokeWidth).toOGR (ogr);
    return ogr;
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.
