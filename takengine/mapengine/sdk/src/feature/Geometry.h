////============================================================================
////
////    FILE:           Geometry.h
////
////    DESCRIPTION:    Declaration of abstract base for geometric features.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 3, 2014   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_GEOMETRY_H_INCLUDED
#define ATAKMAP_FEATURE_GEOMETRY_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <memory>
#include <ostream>
#include <stdint.h>

#include "feature/Envelope.h"
#include "port/Platform.h"


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

namespace atakmap
{
namespace feature
{

class ENGINE_API Geometry;

/** deprecated */
typedef std::unique_ptr<Geometry, void(*)(const Geometry *)> UniqueGeometryPtr;

typedef std::unique_ptr<Geometry, void(*)(const Geometry *)> GeometryPtr;
typedef std::unique_ptr<const Geometry, void(*)(const Geometry *)> GeometryPtr_Const;

}
}

namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::Geometry
///
///     Abstract base class for geometric features.
///
///=============================================================================


class ENGINE_API Geometry
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    enum BlobFormat
      {
        GEOMETRY,                       // Full GEOMETRY representation.
        ENTITY,                         // As a geometry collection entity.
        INTERNAL                        // Only the raw representation.
      };

    enum Dimension
      {
        _2D = 2,
        _3D
      };

    enum Type
      {
        POINT,
        LINESTRING,
        POLYGON,
        COLLECTION,
      };


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    virtual
    ~Geometry ()
        NOTHROWS
        = 0;

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    static
    Geometry*
    clone (const Geometry* geometry)
      { return geometry ? geometry->clone () : NULL; }


    virtual
    Geometry*
    clone ()
        const
        = 0;

    virtual
    std::size_t
    computeWKB_Size ()                  // Includes size of header.
        const
        = 0;

    Dimension
    getDimension ()
        const
        NOTHROWS
      { return dimension_; }

    //
    // Throws std::length_error if empty.
    //
    virtual
    Envelope
    getEnvelope ()
        const
        = 0;

    Type
    getType()
        const
        NOTHROWS
    { return type_; }

    void
    setDimension (Dimension dim);

    //
    // Insert the SpatiaLite blob representation of the Geometry into the
    // supplied output stream.  The supplied BlobFormat specifies whether the
    // representation is wrapped with geometry header and end marker (GEOMETRY),
    // preceded only by a geometry collection entity marker (ENTITY), or without
    // adornment (INTERNAL).  The GEOMETRY format is the default (and the only
    // value useful to clients); ENTITY applies only to Points, LineStrings, and
    // Polygons that are elements of a GeometryCollection; INTERNAL applies only
    // to LineStrings that are rings that define a Polygon.
    //
    virtual
    void
    toBlob (std::ostream&,
            BlobFormat = GEOMETRY)
        const
        = 0;

    virtual
    void
    toWKB (std::ostream&,
           bool includeHeader = true)
        const
        = 0;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//


    //==================================
    //  PROTECTED NESTED TYPES
    //==================================


    static const char BLOB_START_BYTE = 0x00;
    static const char ENTITY_START_BYTE = 0x69;
    static const char MBR_END_BYTE = 0x7C;
    static const char BLOB_END_BYTE = static_cast<char>(0xFEu);


    //==================================
    //  PROTECTED INTERFACE
    //==================================


    Geometry (Type t,
              Dimension dim)
      : type_ (t),
        dimension_ (dim)
      { }

    static
    void
    insertBlobHeader (std::ostream&,
                      const Envelope& envelope);


                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//

    virtual
    void
    changeDimension (Dimension)         // Called by setDimension before update.
        = 0;

    //
    // Private representation.
    //

    Type type_;
    Dimension dimension_;
  };

}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

namespace atakmap
{
namespace feature
{

ENGINE_API void destructGeometry(const Geometry *);

}
}

////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_GEOMETRY_H_INCLUDED
