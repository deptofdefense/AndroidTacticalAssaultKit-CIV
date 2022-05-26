////============================================================================
////
////    FILE:           LineString.h
////
////    DESCRIPTION:    Concrete class representing a 2D or 3D line string.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Dec 10, 2014  scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2014 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_LINE_STRING_H_INCLUDED
#define ATAKMAP_FEATURE_LINE_STRING_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <cstddef>
#include <stdexcept>
#include <vector>

#include "feature/Geometry.h"
#include "feature/Point.h"
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
///  class atakmap::feature::LineString
///
///     A 2D or 3D line string that may be open or closed.
///
///=============================================================================


class ENGINE_API LineString
  : public Geometry
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    explicit
    LineString (Dimension dim = _2D)
      : Geometry (Geometry::LINESTRING, dim),
        count (0)
      { }

    ~LineString ()
        NOTHROWS
      { }

    //
    // The compiler-generated copy constructor and assignment operator are
    // acceptable.
    //

    //
    // Appends the supplied coordinates to the LineString.
    // If getDimension() == _3D, sets z value to 0.
    //
    void
    addPoint (double x,
              double y);

    //
    // Appends the supplied coordinates to the LineString.
    // Throws std::out_of_range if getDimension() == _2D.
    //
    void
    addPoint (double x,
              double y,
              double z)
        throw (std::out_of_range);

    //
    // Appends the supplied Point to the LineString.
    //
    // If point.getDimension() == _2D and getDimension() == _3D, Z value will be
    // set to 0.
    //
    // Throws std::out_of_range if point.getDimension() == _3D
    // && getDimension() == _2D.
    //
    void
    addPoint (Point point)
        throw (std::out_of_range);

    //
    // Appends the supplied range of coordinates to the LineString.
    //
    // If dim == _2D, coordinates should be in x,y,x,y,... order.
    // If getDimension() == _3D, z values will be set to 0.
    //
    // If dim == _3D, coordinates should be in  x,y,z,x,y,z,... order.
    //
    // Throws std::out_of_range if dim == _3D && getDimension() == _2D.
    // Throws std::invalid_argument if (end - begin) % dim > 0.
    //
    void
    addPoints (const double* begin,
               const double* end,
               Dimension dim)
        throw (std::invalid_argument,
               std::out_of_range);

    //
    // Returns the Point at the supplied index.
    //
    // Throws std::out_of_range for index >= getPointCount().
    //
    Point
    getPoint (std::size_t index)
        const
        throw (std::out_of_range);

    std::size_t
    getPointCount ()
        const
        NOTHROWS
      { return count; }

    //
    // Returns the X value at the supplied index.
    //
    // Throws std::out_of_range for index >= getPointCount().
    //
    double
    getX (std::size_t index)
        const
        throw (std::out_of_range);

    //
    // Returns the Y value at the supplied index.
    //
    // Throws std::out_of_range for index >= getPointCount().
    //
    double
    getY (std::size_t index)
        const
        throw (std::out_of_range);

    //
    // Returns the Z value at the supplied index.
    //
    // Throws std::out_of_range for index >= getPointCount() or
    // getDimension() == _2D.
    //
    double
    getZ (std::size_t index)
        const
        throw (std::out_of_range);

    //
    // Returns true if getPointCount() > 0 && first point matches last point.
    //
    bool
    isClosed ()
        const
        NOTHROWS;

    //
    // Sets the point at the supplied index to the supplied Point.
    //
    // If point.getDimension() == _2D and getDimension() == _3D, Z value will be
    // set to 0.
    //
    // Throws std::out_of_range if point.getDimension() == _3D
    // && getDimension() == _2D.
    //
    void
    setPoint (std::size_t index,
              const Point&)
        throw (std::out_of_range);

    //
    // Sets the X value at the supplied index to the supplied value.
    //
    // Throws std::out_of_range for index >= getPointCount().
    //
    void
    setX (std::size_t index,
          double)
        throw (std::out_of_range);

    //
    // Sets the Y value at the supplied index to the supplied value.
    //
    // Throws std::out_of_range for index >= getPointCount().
    //
    void
    setY (std::size_t index,
          double)
        throw (std::out_of_range);

    //
    // Sets the Z value at the supplied index to the supplied value.
    //
    // Throws std::out_of_range for index >= getPointCount() or
    // getDimension() == _2D.
    //
    void
    setZ (std::size_t index,
          double)
        throw (std::out_of_range);


    //==================================
    //  Geometry INTERFACE
    //==================================


    Geometry*
    clone ()
        const override
      { return new LineString (*this); }

    std::size_t
    computeWKB_Size ()
        const override;

    //
    // Throws std::length_error if getPointCount() == 0.
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


    std::vector<double> points;
    std::size_t count;
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


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{


#define MEM_FN( fn )    "atakmap::feature::LineString::" fn ": "



#undef  MEM_FN


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_LINE_STRING_H_INCLUDED
