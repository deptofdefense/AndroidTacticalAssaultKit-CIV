////============================================================================
////
////    FILE:           DefaultDriverDefinition.h
////
////    DESCRIPTION:    Concrete default OGR driver definition class.
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


#ifndef ATAKMAP_FEATURE_DEFAULT_DRIVER_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_DEFAULT_DRIVER_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <string>

#include "feature/OGR_DriverDefinition.h"
#include "port/Platform.h"
#include "port/String.h"



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
///  class atakmap::feature::DefaultDriverDefinition
///
///     Concrete default OGR driver definition.
///
///=============================================================================


class ENGINE_API DefaultDriverDefinition
  : public OGR_DriverDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //
    // Throws std::invalid_argument if the supplied driverName or driverType is
    // NULL.
    //
    DefaultDriverDefinition (const char* driverName,
                             const char* driverType,
                             unsigned int version);

    ~DefaultDriverDefinition ()
        NOTHROWS
      { }


    //==================================
    //  OGR_DriverDefinition INTERFACE
    //==================================


    const char*
    getDriverName ()
        const
        NOTHROWS
      { return driverName; }

    FeatureDataSource::FeatureDefinition::Encoding
    getFeatureEncoding ()
        const
        NOTHROWS
      { return encoding; }

    //
    // Returns the OGR Feature Style Specification style that should be used for
    // the supplied Feature and Geometry.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    TAK::Engine::Port::String
    getStyle (const char* filePath,     // File from which the Feature was parsed.
              const OGRFeature&,
              const OGRGeometry&)
        const;

    const char*
    getType ()
        const
        NOTHROWS
      { return driverType; }

    bool
    layerNameIsPath()
        const
    { return false; }

    unsigned int
    parseVersion ()
        const
        NOTHROWS
      { return version; }

    //
    // Returns true if the supplied Feature should be skipped, rather than
    // inserted.
    //
    bool
    skipFeature (const OGRFeature&)
        const
      { return false; }

    //
    // Returns true if the supplied layer should be skipped, rather than
    // inserted.
    //
    bool
    skipLayer (const OGRLayer&)
        const
      { return false; }


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //
    // Throws std::invalid_argument if the supplied driverName or driverType is
    // NULL.
    //
    DefaultDriverDefinition (const char* driverName,
                             const char* driverType,
                             unsigned int version,
                             FeatureDataSource::FeatureDefinition::Encoding,
                             float strokeWidth = 2.0,
                             unsigned int strokeColor = 0xFFFFFFFF);    // ARGB

    const char*
    getDefaultLineStringStyle ()
        const;

    const char*
    getDefaultPointStyle ()
        const;

    const char*
    getDefaultPolygonStyle ()
        const;

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //
    // The default implementations of the following three member functions are:
    //
    //     return BasicStrokeStyle (strokeColor, strokeWidth).toOGR ();
    //
    virtual
    TAK::Engine::Port::String
    createDefaultLineStringStyle ()
        const;

    virtual
    TAK::Engine::Port::String
    createDefaultPointStyle ()
        const;

    virtual
    TAK::Engine::Port::String
    createDefaultPolygonStyle ()
        const;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    TAK::Engine::Port::String driverName;
    TAK::Engine::Port::String driverType;
    unsigned int version;
    FeatureDataSource::FeatureDefinition::Encoding encoding;
    float strokeWidth;
    unsigned int strokeColor;           // In 0xAARRGGBB format.
    mutable TAK::Engine::Port::String lineStringStyle;
    mutable TAK::Engine::Port::String pointStyle;
    mutable TAK::Engine::Port::String polygonStyle;
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


#endif  // #ifndef ATAKMAP_FEATURE_DEFAULT_DRIVER_DEFINITION_H_INCLUDED
