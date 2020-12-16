////============================================================================
////
////    FILE:           FeatureDataSource.h
////
////    DESCRIPTION:    Abstract base class for feature data sources.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 16, 2015  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_FEATURE_DATA_SOURCE_H_INCLUDED
#define ATAKMAP_FEATURE_FEATURE_DATA_SOURCE_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <string>
#include <utility>

#ifdef MSVC
#include "feature/Geometry.h"
#endif
#include "port/Platform.h"
#include "port/String.h"
#include "util/AttributeSet.h"
#include "util/Blob.h"
#include "util/NonCopyable.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


class ENGINE_API Feature;
#ifndef MSVC
class ENGINE_API Geometry;
#endif
class ENGINE_API Style;


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


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
///  class atakmap::feature::FeatureDataSource
///
///     Abstract base class for feature data sources.
///
///=============================================================================


class ENGINE_API FeatureDataSource
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    class ENGINE_API Content;
    class ENGINE_API FeatureDefinition;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~FeatureDataSource ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Returns the name of the provider.
    //
    virtual
    const char*
    getName ()
        const
        NOTHROWS
        = 0;

    //
    // Returns the (possibly NULL) FeatureDataSource having the supplied name.
    //
    // Throws std::invalid_argument if the supplied providerName is NULL.
    //
    static
    const FeatureDataSource*
    getProvider (const char* providerName);

    //
    // Parses FeatureDefinitions from the supplied filePath.  The optional
    // providerHint is the name of a FeatureDataSource to be used to parse the
    // file.  If providerHint is NULL, any compatible FeatureDataSource will be
    // used.  Returns NULL if no FeatureDefinitions could be parsed from the
    // supplied filePath.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    static
    Content*
    parse (const char* filePath,
           const char* providerHint);

    //
    // Parses FeatureDefinitions from the supplied filePath.  Returns NULL if no
    // FeatureDefinitions could be parsed from the supplied filePath.
    //
    // Throws std::invalid_argument if the supplied filePath is NULL.
    //
    virtual
    Content*
    parseFile (const char* filePath)
        const
        = 0;

    //
    // Returns the parse implementation version for this provider.  Different
    // parse versions indicate that a provider may produce different content
    // from the same file.
    //
    virtual
    unsigned int
    parseVersion ()
        const
        NOTHROWS
        = 0;

    //
    // Registers the supplied FeatureDataSource for parsing FeatureDefinitions.
    // Ignores NULL FeatureDataSource or FeatureDataSource with
    // FeatureDataSource::getName() == NULL.
    //
    static
    void
    registerProvider (const FeatureDataSource*);

    //
    // Unregisters the supplied FeatureDataSource for parsing FeatureDefinitions.
    // Ignores NULL FeatureDataSource or FeatureDataSource with
    // FeatureDataSource::getName() == NULL.
    //
    static
    void
    unregisterProvider (const FeatureDataSource*);
  };


///=========================================================================
///
///  class atakmap::feature::FeatureDataSource::Content
///
///     Abstract base class for feature database content.
///
///=========================================================================


class ENGINE_API FeatureDataSource::Content
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    virtual
    ~Content ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Returns the current FeatureDefinition (or NULL if the most recent call to
    // moveToNextFeature returned false).
    //
    virtual
    FeatureDefinition*
    get ()
        const
        = 0;

    //
    // Returns the name for the current FeatureSet.
    //
    virtual
    const char*
    getFeatureSetName ()
        const
        = 0;

    //
    // Returns the ground sample distance (in meters/pixel) of the "highest
    // resolution" at which the Features in the current FeatureSet should be
    // displayed.  A value of 0.0 indicates that there is no maximum.
    //
    // N.B.:    As "resolution" increases (in the conventional sense), the
    //          number of meters/pixel decreases; thus the value returned by
    //          getMaxResolution will be less than or equal to the value
    //          returned by getMinResolution.
    //
    virtual
    double
    getMaxResolution ()
        const
        = 0;

    //
    // Returns the ground sample distance (in meters/pixel) of the "lowest
    // resolution" at which the Features in the current FeatureSet should be
    // displayed.  A value of 0.0 indicates that there is no minimum.
    //
    // N.B.:    As "resolution" decreases (in the conventional sense), the
    //          number of meters/pixel increases; thus the value returned by
    //          getMinResolution will be greater than or equal to the value
    //          returned by getMaxResolution.
    //
    virtual
    double
    getMinResolution ()
        const
        = 0;

    //
    // Returns the name of the provider that parsed the content.
    //
    virtual
    const char*
    getProvider ()
        const
        = 0;

    //
    // Returns the type of the content.
    //
    virtual
    const char*
    getType ()
        const
        = 0;

    //
    // Moves to the next Feature in the current FeatureSet.  Returns true on
    // success, false if there are no more Features in the current FeatureSet.
    //
    virtual
    bool
    moveToNextFeature ()
        = 0;

    //
    // Moves to the next FeatureSet.  Returns true on success, false if there
    // are no more FeatureSets.  (Equivalent to calling moveToNextFeature until
    // it returns false.)
    //
    virtual
    bool
    moveToNextFeatureSet ()
        = 0;
  };


///=========================================================================
///
///  class atakmap::feature::FeatureDataSource::FeatureDefinition
///
///     The definition of a feature.  Feature properties may be recorded as raw,
///     unprocessed data of several well-defined types.  Use of unprocessed data
///     may yield a significant performance advantage depending on the intended
///     storage.
///
///=========================================================================


class ENGINE_API FeatureDataSource::FeatureDefinition
  : TAK::Engine::Util::NonCopyable
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    enum Encoding                       // Representation of geometry.
      {
        WKT,                            // OGC WKT string.
        WKB,                            // OGC WKB byte buffer.
        BLOB,                           // SpatiaLite byte buffer.
        GEOMETRY                        // atakmap::feature::Geometry object.
      };

    enum Styling                        // Representation of style.
      {
        OGR,                            // OGR style string.
        STYLE                           // atakmap::feature::Style object.
      };

    typedef atakmap::util::BlobImpl
            ByteBuffer;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    FeatureDefinition (const char* name,        // Must not be NULL.
                       const util::AttributeSet&);

    ~FeatureDefinition ()
        NOTHROWS;

    //
    // The compiler is unable to generate a copy constructor or assignment
    // operator (due to a NonCopyable base class).  This is acceptable.
    //

    const util::AttributeSet&
    getAttributes ()
        const
        NOTHROWS
      { return attributes; }

    Encoding
    getEncoding ()
        const
        NOTHROWS
      { return encoding; }

    Feature*
    getFeature ();

    ByteBuffer
    getGeometryByteBuffer ()            // Only valid if ByteBuffer was used to
        const                           // set geometry.
        NOTHROWS
      {
        return std::make_pair (static_cast<const unsigned char*> (rawGeometry),
                               static_cast<const unsigned char*> (bufferTail));
      }

    const char*
    getName ()
        const
        NOTHROWS
      { return name; }

    const void*
    getRawGeometry ()
        const
        NOTHROWS
      { return rawGeometry; }

    const void*
    getRawStyle ()
        const
        NOTHROWS
      { return rawStyle; }

    Styling
    getStyling ()
        const
        NOTHROWS
      { return styling; }


     double
     getExtrude() const
     {
        return extrude;
     }

     int
     getAltitudeMode() const
     {
        return altitudeMode;
     }

    //
    // Set the attributes.
    //
    void
    setAttributes ();

    //
    // Sets the extrude value
    //
    void
    setExtrude(double extrude);

    //
    // Sets the altitudeMode value
    // 0 clampToGround
    // 1 relativeToGround
    // 2 absolute
    //
    void
    setAltitudeMode(int altitudeMode);

    //
    // Sets the geometry to a copy of the supplied OGC WKT geometry
    // representation.
    //
    // Throw std::invalid_argument if supplied styleString is NULL.
    //
    void
    setGeometry (const char* geometryString);

    //
    // Sets the geometry to the supplied Geometry object.  (Adopts the Geometry
    // object.)
    //
    // Throw std::invalid_argument if supplied Geometry is NULL.
    //
    void
    setGeometry (const Geometry*);

    //
    // Sets the geometry to a copy of the supplied byte buffer, with the
    // supplied encoding.
    //
    // Throw std::invalid_argument if supplied ByteBuffer members are NULL or if
    // the supplied encoding is not WKB or BLOB.
    //
    void
    setGeometry (const ByteBuffer&,
                 Encoding blobType);

    //
    // Set the style to a copy of the supplied OGR style specification.
    //
    void
    setStyle (const char* styleString);

    //
    // Set the style to the (possibly NULL) supplied Style object.  (Adopts the
    // Style object.)
    //
    void
    setStyle (const Style*);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    void
    copyBuffer (const ByteBuffer&);

    void
    freeGeometry ();

    void
    freeStyle ();


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    TAK::Engine::Port::String name;
    Encoding encoding;
    Styling styling;
    const void* rawGeometry;            // Feature's geometry, per encoding.
    const void* rawStyle;               // Feature's styling, per styling.
    const void* bufferTail;
    util::AttributeSet attributes;
    double extrude = 0.0;
    int altitudeMode = 0;

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

#endif  // #ifndef ATAKMAP_FEATURE_FEATURE_DATA_SOURCE_H_INCLUDED
