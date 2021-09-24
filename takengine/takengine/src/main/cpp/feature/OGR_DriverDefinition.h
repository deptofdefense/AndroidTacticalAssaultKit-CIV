////============================================================================
////
////    FILE:           OGR_DriverDefinition.h
////
////    DESCRIPTION:    Abstract base class for OGR driver definitions.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Apr 16, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_OGR_DRIVER_DEFINITION_H_INCLUDED
#define ATAKMAP_FEATURE_OGR_DRIVER_DEFINITION_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/FeatureDataSource.h"
#include "port/Platform.h"
#include "port/String.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


class OGRFeature;
class OGRGeometry;
class OGRLayer;


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
///  class atakmap::feature::OGR_DriverDefinition
///
///     Abstract base class for OGR driver definitions.
///
///=============================================================================


class ENGINE_API OGR_DriverDefinition
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    virtual
    ~OGR_DriverDefinition ()
        NOTHROWS
        = 0;

    //
    // Returns the (possibly NULL) OGR_DriverDefinition with the supplied name.
    //
    // Throws std::invalid_argument if the supplied driverName is NULL.
    //
    static
    const OGR_DriverDefinition*
    getDriver (const char* driverName);

    virtual
    const char*
    getDriverName ()
        const
        NOTHROWS
        = 0;

    virtual
    FeatureDataSource::FeatureDefinition::Encoding
    getFeatureEncoding ()
        const
        NOTHROWS
        = 0;

    //
    // Returns the OGR Feature Style Specification style that should be used for
    // the supplied Feature and Geometry.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    virtual
    TAK::Engine::Port::String
    getStyle (const char* filePath,     // File from which OGRFeature was parsed.
              const OGRFeature&,
              const OGRGeometry&)
        const
        = 0;

    virtual
    const char*
    getType ()
        const
        NOTHROWS
        = 0;

    virtual
    unsigned int
    parseVersion ()
        const
        NOTHROWS
        = 0;

    //
    // Registers the supplied OGR_DriverDefinition.
    // Ignores NULL OGR_DriverDefinition or OGR_DriverDefinition with
    // OGR_DriverDefinition::getDriverName() == NULL.
    //
    static
    void
    registerDriver (const OGR_DriverDefinition*);

    //
    // Returns true if the supplied Feature should be skipped, rather than
    // inserted.
    //
    virtual
    bool
    skipFeature (const OGRFeature&)
        const
        = 0;

    //
    // Returns true if the supplied layer should be skipped, rather than
    // inserted.
    //
    virtual
    bool
    skipLayer (const OGRLayer&)
        const
        = 0;

    //
    // Unregisters the supplied OGR_DriverDefinition.
    // Ignores NULL OGR_DriverDefinition or OGR_DriverDefinition with
    // OGR_DriverDefinition::getDriverName() == NULL.
    //
    static
    void
    unregisterDriver (const OGR_DriverDefinition*);

    virtual
    bool
    layerNameIsPath()
        const
        = 0;
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

#endif  // #ifndef ATAKMAP_FEATURE_OGR_DRIVER_DEFINITION_H_INCLUDED
