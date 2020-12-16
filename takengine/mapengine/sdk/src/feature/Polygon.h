////============================================================================
////
////    FILE:           Polygon.h
////
////    DESCRIPTION:    Concrete class representing a 2D or 3D polygon.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 11, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_POLYGON_H_INCLUDED
#define ATAKMAP_FEATURE_POLYGON_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <stdexcept>
#include <utility>
#include <vector>

#include "feature/Geometry.h"
#include "feature/LineString.h"
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


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::Polygon
///
///     Concrete class representing a polygon with an outer boundary and
///     optional inner holes.
///
///=============================================================================


class ENGINE_API Polygon
  : public Geometry
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    explicit
    Polygon (Dimension dim)
      : Geometry (Geometry::POLYGON, dim)
      { }

    //
    // Throws std::invalid_argument for open LineString.
    //
    Polygon (const LineString& exteriorRing);

    //
    // Throws std::invalid_argument for open LineString or heterogeneous
    // LineString dimensions.
    //
    Polygon (const LineString& exteriorRing,
             const std::vector<LineString>& interiorRings);

    ~Polygon ()
        NOTHROWS;

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    //
    // Adds the supplied LineString as the exteriorRing if none yet exists.
    // Otherwise, adds the supplied LineString as an interior ring.
    //
    // Throws std::invalid_argument for open LineString or
    // ring.getDimension() != getDimension().
    //
    void
    addRing (const LineString& ring);

    void
    clear ()
        NOTHROWS
      { rings.clear (); }

    LineString
    getExteriorRing ()
        const
        throw (std::length_error);      // Thrown if rings.empty().

    std::pair<std::vector<LineString>::const_iterator,
              std::vector<LineString>::const_iterator>
    getInteriorRings ()
        const;


    //==================================
    //  Geometry INTERFACE
    //==================================


    Geometry*
    clone ()
        const override
      { return new Polygon (*this); }

    std::size_t
    computeWKB_Size ()
        const override;

    //
    // Throws std::length_error if rings.empty().
    //
    Envelope
    getEnvelope ()
        const override;

    void
    toBlob (std::ostream&,
            BlobFormat)                 // Defaults to GEOMETRY.
        const override;

    void
    toWKB (std::ostream&,
           bool includeHeader)          // Defaults to true.
        const override;


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  Geometry INTERFACE
    //==================================


    void
    changeDimension (Dimension) override;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    std::vector<LineString> rings;      // Exterior, then interiors.
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

#endif  // #ifndef ATAKMAP_FEATURE_POLYGON_H_INCLUDED
